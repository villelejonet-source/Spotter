package com.viktorolsson.spotter.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkerParameters
import com.viktorolsson.spotter.core.data.DataGraph
import com.viktorolsson.spotter.core.data.StartupMaintenance
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.dao.ExerciseDao
import com.viktorolsson.spotter.core.data.db.dao.PlanDao
import com.viktorolsson.spotter.core.data.db.dao.RecommendationDao
import com.viktorolsson.spotter.core.data.db.dao.ScheduleDao
import com.viktorolsson.spotter.core.data.db.dao.UserProfileDao
import com.viktorolsson.spotter.core.data.db.dao.WorkoutDao
import com.viktorolsson.spotter.core.data.di.ApplicationScope
import com.viktorolsson.spotter.core.data.repository.BodyWeightRepository
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import com.viktorolsson.spotter.core.data.repository.HistoryRepository
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.RecommendationRepository
import com.viktorolsson.spotter.core.data.repository.RestTimerRepository
import com.viktorolsson.spotter.core.data.repository.ScheduleRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.data.seed.ExerciseSeeder
import com.viktorolsson.spotter.core.data.sync.AccountService
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import com.viktorolsson.spotter.core.data.sync.SyncWorker
import com.viktorolsson.spotter.feature.session.SessionViewModel
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import org.robolectric.annotation.Config
import kotlin.reflect.KClass
import kotlin.time.Clock

/**
 * The Koin graph is complete: a view model or worker asking for something nobody provides
 * fails here instead of on the phone.
 */
@RunWith(AndroidJUnit4::class)
// A plain Application, so the test doesn't start the app's own Koin or startup work.
@Config(application = Application::class)
class AppModulesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val modules = appModules(context)

    /** What the data layer (the first module, `androidDataModule`) provides. */
    private val dataTypes: List<KClass<*>> = listOf(
        DataGraph::class, Clock::class, SpotterDatabase::class,
        ExerciseDao::class, UserProfileDao::class, WorkoutDao::class, PlanDao::class, RecommendationDao::class, ScheduleDao::class,
        UserProfileRepository::class, UserPreferencesRepository::class, ExerciseRepository::class, HistoryRepository::class,
        PlanRepository::class, BodyWeightRepository::class, RestTimerRepository::class, WorkoutRepository::class,
        ScheduleRepository::class, RecommendationRepository::class, ExerciseSeeder::class, StartupMaintenance::class,
        AccountService::class, SyncRepository::class,
    )

    @Test
    fun dataLayerProvidesEveryListedType() {
        val koin = koinApplication { modules(modules.first()) }.koin
        dataTypes.forEach { type -> assertTrue("$type", type.isInstance(koin.get<Any>(type))) }
        koin.get<CoroutineScope>(ApplicationScope)
    }

    // The worker and every view model, made from the data layer above plus what Koin supplies
    // itself at runtime (Context, SavedStateHandle, WorkerParameters). SessionViewModel is built
    // by a lambda, which verify can't inspect; the next test makes one.
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun viewModelsAndWorkersResolve() {
        module {
            includes(modules.drop(1))
            workerOf(::SyncWorker)
        }
            .verify(extraTypes = dataTypes + listOf(Context::class, SavedStateHandle::class, WorkerParameters::class, CoroutineScope::class))
    }

    @Test
    fun sessionViewModelResolves() {
        val koin = koinApplication { modules(modules) }.koin
        koin.get<SessionViewModel> { parametersOf(SavedStateHandle(mapOf("sessionId" to 1L))) }
    }
}
