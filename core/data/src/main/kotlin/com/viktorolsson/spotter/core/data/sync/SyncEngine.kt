package com.viktorolsson.spotter.core.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class SyncCursorStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
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
    /** This phone has its own data and so does the backup: ask which to keep. */
    data object NeedsChoice : SyncOutcome
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
) {
    suspend fun sync(userId: String, choice: FirstSyncChoice? = null): SyncOutcome {
        var cursor = cursors.get().takeIf { it.userId == userId } ?: SyncCursor(userId = userId)
        val firstSync = cursor.lastSyncedAt == 0L

        if (firstSync) {
            val backupExists = remote.hasRows()
            val phoneHasData = store.hasLocalData()
            when {
                backupExists && phoneHasData && choice == null -> return SyncOutcome.NeedsChoice
                // Restore: drop this phone's own data first (often just a fresh onboarding plan).
                backupExists && (!phoneHasData || choice == FirstSyncChoice.USE_BACKUP) -> store.wipe()
            }
            // Everything local counts as unpushed the first time.
            cursor = cursor.copy(pushedUpTo = 0, pulledUpTo = null)
        }

        // Pull: collect all pages, then apply together so parents land before children.
        val pulled = mutableListOf<RemoteRow>()
        val since = cursor.pulledUpTo?.let(::withOverlap)
        var offset = 0
        while (true) {
            val page = remote.pull(since, offset, PAGE)
            pulled += page
            if (page.size < PAGE) break
            offset += PAGE
        }
        store.apply(pulled.map { it.toSyncRow() })
        val newPulledUpTo = pulled.maxByOrNull { parse(it.serverUpdatedAt) }?.serverUpdatedAt ?: cursor.pulledUpTo

        // Push: anything changed locally since the last push (rows just pulled are skipped
        // by the server, which only keeps strictly newer versions).
        val pushStartedAt = clock.millis()
        val changes = store.changesSince(cursor.pushedUpTo)
        changes.chunked(PAGE).forEach { remote.push(it) }

        cursors.save(SyncCursor(userId, newPulledUpTo, pushStartedAt, clock.millis()))
        return SyncOutcome.Done(pulled = pulled.size, pushed = changes.size)
    }

    /** Re-reads a couple of minutes back, so rows committed slightly out of order aren't missed. */
    private fun withOverlap(serverTime: String): String = parse(serverTime).minusSeconds(120).toString()

    private fun parse(serverTime: String): Instant = OffsetDateTime.parse(serverTime).toInstant()

    private companion object {
        const val PAGE = 500
    }
}
