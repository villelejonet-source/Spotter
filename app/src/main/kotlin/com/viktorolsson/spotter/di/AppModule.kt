package com.viktorolsson.spotter.di

import android.content.Context
import com.viktorolsson.spotter.BuildConfig
import com.viktorolsson.spotter.core.data.di.androidDataGraph
import com.viktorolsson.spotter.core.data.di.androidDataModule
import com.viktorolsson.spotter.core.data.sync.AppBuild
import com.viktorolsson.spotter.core.data.sync.SupabaseConfig
import com.viktorolsson.spotter.shell.uiModules
import org.koin.core.module.Module

/** Everything the app injects: the data layer, then every screen's view models (shared with iOS). */
fun appModules(context: Context): List<Module> = listOf(
    androidDataModule(
        androidDataGraph(
            context,
            // From local.properties at build time; blank values turn cloud sync off.
            SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY),
            // Reported on sync, so it's known which app versions are still in use.
            AppBuild(BuildConfig.VERSION_CODE),
        ),
    ),
) + uiModules
