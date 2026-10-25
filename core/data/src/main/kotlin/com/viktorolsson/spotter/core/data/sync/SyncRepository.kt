package com.viktorolsson.spotter.core.data.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    /** The first sync needs a decision before it goes on (see [fromLink] for which one). */
    val needsChoice: Boolean = false,
    /**
     * With [needsChoice]: false means both this phone and the backup have data (use the
     * backup, or merge). True means the backup is empty and the sign-in came from an
     * emailed link, so ask "Upload this phone's data to [account]'s email?": yes is
     * [SyncRepository.syncNow] with [FirstSyncChoice.MERGE], no is [SyncRepository.signOut].
     */
    val fromLink: Boolean = false,
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
    private val clock: Clock,
    device: DeviceIdentity,
) {
    private val engine = SyncEngine(store, remote, cursors, clock, device::checkIn)
    private val mutex = Mutex()
    private val running = MutableStateFlow(false)
    private val error = MutableStateFlow<SyncError?>(null)
    private val choice = MutableStateFlow<SyncOutcome.NeedsChoice?>(null)
    private var lastOpenSyncAt = 0L

    val status: Flow<SyncStatus> = combine(accounts.account, cursors.cursor, running, error, choice) { account, cursor, running, error, choice ->
        SyncStatus(
            available = accounts.isAvailable,
            account = account,
            syncing = running,
            lastSyncedAt = cursor.lastSyncedAt.takeIf { it > 0 && cursor.userId == account?.userId }?.let(Instant::ofEpochMilli),
            error = error,
            needsChoice = choice != null,
            fromLink = choice?.fromLink == true,
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

    // Account calls throw AuthException. Any sign-in triggers the first sync by itself.

    suspend fun signUp(email: String, password: String) = accounts.signUp(email, password)

    suspend fun signIn(email: String, password: String) = accounts.signIn(email, password)

    suspend fun sendCode(email: String) = accounts.sendCode(email)

    suspend fun resendConfirmation(email: String) = accounts.resendConfirmation(email)

    suspend fun sendPasswordReset(email: String) = accounts.sendPasswordReset(email)

    suspend fun verifyCode(email: String, code: String, purpose: CodePurpose) = accounts.verifyCode(email, code, purpose)

    suspend fun updatePassword(password: String, nonce: String? = null) = accounts.updatePassword(password, nonce)

    /** True after a password-reset link opened the app: ask for a new password. */
    val recoveringPassword: StateFlow<Boolean> get() = accounts.recovering

    fun finishPasswordRecovery() = accounts.finishRecovery()

    /** Runs a sync now if signed in. Returns false when it failed (worth retrying). */
    suspend fun syncNow(choice: FirstSyncChoice? = null): Boolean {
        val account = accounts.awaitAccount() ?: return true
        return mutex.withLock {
            running.value = true
            try {
                val outcome = withContext(Dispatchers.IO) { engine.sync(account.userId, choice, fromLink = accounts.signedInFromLink) }
                when (outcome) {
                    is SyncOutcome.NeedsChoice -> this.choice.value = outcome
                    is SyncOutcome.Done -> {
                        this.choice.value = null
                        accounts.signedInFromLink = false
                        finishRestore()
                    }
                }
                error.value = null
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                error.value = SyncError.OFFLINE
                Log.w(TAG, "Sync failed: offline (${e.javaClass.simpleName})")
                false
            } catch (e: Exception) {
                val offline = e.cause is IOException
                error.value = if (offline) SyncError.OFFLINE else SyncError.FAILED
                // Class names only: no payloads, tokens or addresses in the log.
                Log.w(TAG, "Sync failed: ${e.javaClass.simpleName}" + (e.cause?.let { " caused by ${it.javaClass.simpleName}" } ?: ""))
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

    /**
     * The app came to the foreground: pick up changes made elsewhere, such as a plan
     * edited on the website. At most once a minute, so switching apps doesn't spam syncs.
     */
    fun syncOnOpen() {
        val now = clock.millis()
        if (now - lastOpenSyncAt < OPEN_SYNC_INTERVAL_MS) return
        lastOpenSyncAt = now
        requestSync()
    }

    /** Signs out on this phone. Local data stays; the backup stays in the cloud. */
    suspend fun signOut() {
        accounts.signOut()
        accounts.finishRecovery()
        cursors.clear()
        choice.value = null
        error.value = null
    }

    /** Deletes the account and its backup. Local data stays on this phone. */
    suspend fun deleteAccount() {
        accounts.deleteAccount()
        cursors.clear()
        choice.value = null
    }

    /**
     * After a successful sync: a restored profile means setup is done, and the profile's
     * units (the synced source of truth) are mirrored into the unit preference.
     */
    private suspend fun finishRestore() {
        val profile = profiles.get() ?: return
        val prefs = preferences.preferences.first()
        if (!prefs.onboardingCompleted) preferences.setOnboardingCompleted(true)
        if (prefs.weightUnit != profile.units) preferences.setWeightUnit(profile.units)
    }

    private companion object {
        const val TAG = "SpotterSync"
    }
}

private const val OPEN_SYNC_INTERVAL_MS = 60_000L

/** Background sync with WorkManager: every few hours, and soon after workouts. */
@Singleton
class SyncScheduler @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build(),
        )
    }

    /** Runs after any sync already in flight instead of cancelling it (APPEND_OR_REPLACE). */
    fun syncSoon() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "sync-soon",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build(),
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
