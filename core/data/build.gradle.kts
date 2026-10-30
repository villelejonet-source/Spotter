import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Shared data layer: Room schema and DAOs, repositories, cloud sync and accounts, for the
// Android app and iOS. What differs per platform is in androidMain (the Android DataGraph,
// WorkManager background sync, the encrypted session store) and iosMain (the iOS DataGraph).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "com.viktorolsson.spotter.core.data"
        compileSdk = 37
        minSdk = 26
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        withHostTest {
            isIncludeAndroidResources = true
        }
    }
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:engine"))
            api(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            api(libs.androidx.datastore.preferences)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(project.dependencies.platform(libs.supabase.bom))
            api(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            api(libs.multiplatform.settings)
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.sqlite.framework)
            implementation(libs.ktor.client.okhttp)
            // Auth session and PKCE verifier live in EncryptedSharedPreferences (see AuthStorage).
            implementation(libs.androidx.security.crypto)
            implementation(libs.androidx.work.runtime)
            implementation(libs.koin.androidx.workmanager)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit4)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.room.testing)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// MigrationTestHelper reads the exported schemas as assets.
extensions.configure<com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension> {
    onVariants { variant ->
        (variant as? com.android.build.api.variant.HasHostTests)
            ?.hostTests?.get(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE)
            ?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
}

dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.androidx.room.compiler) }
}
