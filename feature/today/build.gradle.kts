plugins {
    alias(libs.plugins.spotter.kmp.feature)
    alias(libs.plugins.spotter.android.screenshots)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:engine"))
        }
    }
}
