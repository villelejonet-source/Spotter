package com.viktorolsson.spotter.di

import com.viktorolsson.spotter.BuildConfig
import com.viktorolsson.spotter.core.data.sync.SupabaseConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** From local.properties at build time; blank values turn cloud sync off. */
    @Provides
    fun provideSupabaseConfig() = SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
}
