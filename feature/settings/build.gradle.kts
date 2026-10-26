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
    }
}
