package com.viktorolsson.spotter.core.data.sync

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.viktorolsson.spotter.core.data.di.ApplicationScope
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SyncStatus(
    /** False when this build has no Supabase project configured. */
    val available: Boolean = false,
    val account: Account? = null,
    val syncing: Boolean = false,
    val lastSyncedAt: Instant? = null,
    val error: SyncError? = null,
    /** The first sync found data both here and in the backup: ask what to keep. */
    val needsChoice: Boolean = false,
)

enum class SyncError { OFFLINE, FAILED }

/** Cloud backup and sync: sign-in, running a sync, and its status for the UI. */
@Singleton
class SyncRepository @Inject constructor(
    private val accounts: AccountService,
    remote: SupabaseSyncRemote,
    store: LocalSyncStore,
    private val cursors: SyncCursorStore,
    private val preferences: UserPreferencesRepository,
    private val profiles: UserProfileRepository,
    private val scheduler: SyncScheduler,
    @param:ApplicationScope private val scope: CoroutineScope,
    clock: Clock,
) {
    private val engine = SyncEngine(store, remote, cursors, clock)
    private val mutex = Mutex()
    private val running = MutableStateFlow(false)
    private val error = MutableStateFlow<SyncError?>(null)
    private val needsChoice = MutableStateFlow(false)

    val status: Flow<SyncStatus> = combine(accounts.account, cursors.cursor, running, error, needsChoice) { account, cursor, running, error, choice ->
        SyncStatus(
            available = accounts.isAvailable,
            account = account,
            syncing = running,
            lastSyncedAt = cursor.lastSyncedAt.takeIf { it > 0 && cursor.userId == account?.userId }?.let(Instant::ofEpochMilli),
            error = error,
            needsChoice = choice,
        )
    }

    init {
        // Any sign-in (email link, code, or a session restored at start-up) kicks off a
        // sync, which restores the backup on a fresh phone, and keeps background sync on.
        scope.launch {
            accounts.account.distinctUntilChangedBy { it?.userId }.collect { account ->
                if (account != null) {
                    scheduler.schedulePeriodic()
                    syncNow()
                }
            }
        }
    }

    fun isSignedIn(): Boolean = accounts.currentAccount() != null

    fun handleDeeplink(intent: Intent) = accounts.handleDeeplink(intent)

    suspend fun sendCode(email: String) = accounts.sendCode(email)

    /** Signs in with a typed code; the sign-in itself triggers the first sync. */
    suspend fun verifyCode(email: String, code: String) = accounts.verifyCode(email, code)

    /** Runs a sync now if signed in. Returns false when it failed (worth retrying). */
    suspend fun syncNow(choice: FirstSyncChoice? = null): Boolean {
        val account = accounts.currentAccount() ?: return true
        return mutex.withLock {
            running.value = true
            try {
                when (withContext(Dispatchers.IO) { engine.sync(account.userId, choice) }) {
                    SyncOutcome.NeedsChoice -> needsChoice.value = true
                    is SyncOutcome.Done -> {
                        needsChoice.value = false
                        finishRestore()
                    }
                }
                error.value = null
                true
            } catch (e: IOException) {
                error.value = SyncError.OFFLINE
                false
            } catch (e: Exception) {
                error.value = if (e.cause is IOException) SyncError.OFFLINE else SyncError.FAILED
                false
            } finally {
                running.value = false
            }
        }
    }

    /** Queue a sync for when there's a connection (e.g. right after a workout). */
    fun requestSync() {
        if (accounts.currentAccount() != null) scheduler.syncSoon()
    }

    /** Signs out on this phone. Local data stays; the backup stays in the cloud. */
    suspend fun signOut() {
        accounts.signOut()
        cursors.clear()
        needsChoice.value = false
        error.value = null
    }

    /** Deletes the account and its backup. Local data stays on this phone. */
    suspend fun deleteAccount() {
        accounts.deleteAccount()
        cursors.clear()
        needsChoice.value = false
    }

    /** A restored profile means setup is done; take the units from it too. */
    private suspend fun finishRestore() {
        val profile = profiles.get() ?: return
        if (!preferences.preferences.first().onboardingCompleted) {
            preferences.setWeightUnit(profile.units)
            preferences.setOnboardingCompleted(true)
        }
    }
}

/** Background sync with WorkManager: every few hours, and soon after workouts. */
@Singleton
class SyncScheduler @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(online).build(),
        )
    }

    fun syncSoon() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "sync-soon",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build(),
        )
    }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: SyncRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (sync.syncNow()) Result.success() else Result.retry()
}
