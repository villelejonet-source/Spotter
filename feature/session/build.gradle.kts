plugins {
    alias(libs.plugins.spotter.kmp.feature)
    alias(libs.plugins.spotter.android.screenshots)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:engine"))
            // Icons outside the core set (see KmpFeatureConventionPlugin).
            implementation(libs.compose.mp.material.icons.extended)
        }
        // The rest-timer foreground service, its notifications and the notification-permission
        // request stay Android-only (iOS equivalents come in phase 5).
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.android)
        }
    }
}
