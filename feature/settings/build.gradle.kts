plugins {
    alias(libs.plugins.spotter.kmp.feature)
    alias(libs.plugins.spotter.android.screenshots)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Icons outside the core set (see KmpFeatureConventionPlugin).
            implementation(libs.compose.mp.material.icons.extended)
        }
        // Health Connect's permission screen is launched for a result (HealthConnect.android.kt).
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}
