plugins {
    alias(libs.plugins.spotter.kmp.feature)
    alias(libs.plugins.spotter.android.screenshots)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:engine"))
            implementation(libs.compose.mp.material.icons.extended)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        // View model tests run against a real in-memory database (TodayViewModelTest).
        getByName("androidHostTest").dependencies {
            implementation(libs.androidx.sqlite.framework)
        }
    }
}
