package com.viktorolsson.spotter.core.data.di

import com.viktorolsson.spotter.core.data.DataGraph
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Scope for work that must outlive any screen, such as seeding and auto-save writes: `get(ApplicationScope)`. */
val ApplicationScope = named("ApplicationScope")

/**
 * The data layer for Koin, on both platforms: the platform builds the [DataGraph] (Android's
 * `androidDataGraph`, iOS's `iosDataGraph`) and every object here is the graph's own, made once.
 */
fun dataModule(graph: DataGraph): Module = module {
    single { graph }
    single { graph.clock }
    single { graph.errors }
    single(ApplicationScope) { graph.scope }
    single { graph.database }

    single { graph.database.exerciseDao() }
    single { graph.database.userProfileDao() }
    single { graph.database.workoutDao() }
    single { graph.database.planDao() }
    single { graph.database.recommendationDao() }
    single { graph.database.scheduleDao() }

    single { graph.userProfiles }
    single { graph.preferences }
    single { graph.exercises }
    single { graph.history }
    single { graph.plans }
    single { graph.bodyWeight }
    single { graph.restTimer }
    single { graph.workouts }
    single { graph.schedule }
    single { graph.recommendations }
    single { graph.seeder }
    single { graph.startupMaintenance }
    single { graph.accounts }
    single { graph.sync }
}
