package com.viktorolsson.spotter.core.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.viktorolsson.spotter.core.data.logWarn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Where sync left off, per signed-in user. */
data class SyncCursor(
    val userId: String? = null,
    /** Server timestamp of the last row pulled; null before the first pull. */
    val pulledUpTo: String? = null,
    /** Local changes after this time (ms) haven't been pushed yet. */
    val pushedUpTo: Long = 0,
    /** Last successful sync (ms), 0 if never. */
    val lastSyncedAt: Long = 0,
)

class SyncCursorStore(private val dataStore: DataStore<Preferences>) {
    val cursor: Flow<SyncCursor> = dataStore.data.map { p ->
        SyncCursor(p[USER], p[PULLED], p[PUSHED] ?: 0, p[LAST] ?: 0)
    }

    suspend fun get(): SyncCursor = cursor.first()

    suspend fun save(cursor: SyncCursor) {
        dataStore.edit { p ->
            cursor.userId?.let { p[USER] = it } ?: p.remove(USER)
            cursor.pulledUpTo?.let { p[PULLED] = it } ?: p.remove(PULLED)
            p[PUSHED] = cursor.pushedUpTo
            p[LAST] = cursor.lastSyncedAt
        }
    }

    suspend fun clear() = save(SyncCursor())

    private companion object {
        val USER = stringPreferencesKey("sync_user_id")
        val PULLED = stringPreferencesKey("sync_pulled_up_to")
        val PUSHED = longPreferencesKey("sync_pushed_up_to")
        val LAST = longPreferencesKey("sync_last_synced_at")
    }
}

sealed interface SyncOutcome {
    data class Done(val pulled: Int, val pushed: Int) : SyncOutcome
    /**
     * Ask before going on: this phone has its own data and so does the backup, or the
     * backup is empty and the sign-in came from an emailed link ([fromLink]), so the data
     * must not be uploaded to an account the user hasn't confirmed.
     */
    data class NeedsChoice(val fromLink: Boolean = false) : SyncOutcome
}

/**
 * One sync run: pull everything newer than the cursor and apply it (last-write-wins),
 * then push local changes. Pulls first, so a phone that's been offline doesn't
 * overwrite newer edits from another phone.
 */
class SyncEngine(
    private val store: LocalSyncStore,
    private val remote: SyncRemote,
    private val cursors: SyncCursorStore,
    private val clock: Clock,
    /** What this install reports after each sync; null reports nothing. */
    private val device: suspend () -> DeviceCheckIn? = { null },
) {
    /**
     * [fromLink]: the session came from a tapped email link rather than typed
     * credentials. The first sync then never uploads this phone's data to an empty
     * account without an explicit [choice] (any choice means "go ahead").
     */
    suspend fun sync(userId: String, choice: FirstSyncChoice? = null, fromLink: Boolean = false): SyncOutcome {
        var cursor = cursors.get().takeIf { it.userId == userId } ?: SyncCursor(userId = userId)
        val firstSync = cursor.lastSyncedAt == 0L

        if (firstSync) {
            val backupExists = remote.hasRows(userId)
            val phoneHasData = store.hasLocalData()
            when {
                backupExists && phoneHasData && choice == null -> return SyncOutcome.NeedsChoice(fromLink = false)
                !backupExists && phoneHasData && fromLink && choice == null -> return SyncOutcome.NeedsChoice(fromLink = true)
                // Restore: drop this phone's own data first (often just a fresh onboarding plan).
                backupExists && (!phoneHasData || choice == FirstSyncChoice.USE_BACKUP) -> store.wipe()
            }
            // Everything local counts as unpushed the first time.
            cursor = cursor.copy(pushedUpTo = 0, pulledUpTo = null)
        }

        // Pull page by page, applying each as it arrives so a large backup never sits in
        // memory. Rows whose parent is in a later page are retried once the pull is done.
        val since = cursor.pulledUpTo?.let(::withOverlap)
        var offset = 0
        var pulled = 0
        var newestServerTime: Instant? = null
        var newPulledUpTo = cursor.pulledUpTo
        val deferred = mutableListOf<SyncRow>()
        while (true) {
            val page = remote.pull(userId, since, offset, PAGE)
            pulled += page.size
            page.forEach { row ->
                val at = parse(row.serverUpdatedAt)
                if (newestServerTime == null || at > newestServerTime) {
                    newestServerTime = at
                    newPulledUpTo = row.serverUpdatedAt
                }
            }
            deferred += store.applyAll(page.map { it.toSyncRow() }).deferred
            if (page.size < PAGE) break
            offset += PAGE
        }
        if (deferred.isNotEmpty()) {
            // A row whose parent still isn't here after every page (deleted on another phone,
            // or pushed without it) can't be stored. Rare, but it's data that won't show here.
            val dropped = store.applyAll(deferred).deferred
            if (dropped.isNotEmpty()) {
                logWarn(TAG, "Dropped ${dropped.size} pulled rows with no parent: ${dropped.groupingBy { it.tableName }.eachCount()}")
            }
        }

        // Push: anything changed locally since the last push (rows just pulled are skipped
        // by the server, which only keeps strictly newer versions).
        val pushStartedAt = clock.now().toEpochMilliseconds()
        val changes = store.changesSince(cursor.pushedUpTo)
        changes.chunked(PAGE).forEach { remote.push(it) }

        cursors.save(SyncCursor(userId, newPulledUpTo, pushStartedAt, clock.now().toEpochMilliseconds()))
        checkIn()
        return SyncOutcome.Done(pulled = pulled, pushed = changes.size)
    }

    /**
     * Tells the server which versions this install runs. Best effort: the sync already
     * succeeded, so a failure here (offline, a server without `check_in_device`) is dropped
     * and the next sync tries again.
     */
    private suspend fun checkIn() {
        try {
            device()?.let { remote.checkIn(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Dropped on purpose, see above.
        }
    }

    /** Re-reads a couple of minutes back, so rows committed slightly out of order aren't missed. */
    private fun withOverlap(serverTime: String): String = (parse(serverTime) - 120.seconds).toString()

    private fun parse(serverTime: String): Instant = Instant.parse(serverTime)

    private companion object {
        const val PAGE = 500
        const val TAG = "SpotterSync"
    }
}
