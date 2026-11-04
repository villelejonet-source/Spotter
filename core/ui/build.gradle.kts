// Theme, shared components, labels and formatting, for the Android app and iOS.
plugins {
    alias(libs.plugins.spotter.kmp.compose)
    alias(libs.plugins.spotter.android.screenshots)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            // Stalled lifts and their options (StalledLift.kt), shared by Today, Progress and History.
            implementation(project(":core:engine"))
            implementation(libs.vico.compose.m3)
            implementation(libs.compose.mp.material.icons.extended)
        }
        // FileProvider, for share cards (Share.android.kt).
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
    }
}
