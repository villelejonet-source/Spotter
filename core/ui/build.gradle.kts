// Theme, shared components, labels and formatting, for the Android app and iOS.
plugins {
    alias(libs.plugins.spotter.kmp.compose)
    alias(libs.plugins.spotter.android.screenshots)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(libs.vico.compose.m3)
            implementation(libs.compose.mp.material.icons.extended)
        }
    }
}
