// The app's root UI, shared by Android's MainActivity and the iOS app: navigation between every
// screen, the bottom bar, the app-wide sync dialogs, and the Koin modules of every screen.
plugins {
    alias(libs.plugins.spotter.kmp.feature)
    // Robolectric and Compose UI tests for the navigation (no screenshots yet).
    alias(libs.plugins.spotter.android.screenshots)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":feature:today"))
            implementation(project(":feature:history"))
            implementation(project(":feature:progress"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:library"))
            implementation(project(":feature:session"))
            implementation(project(":feature:onboarding"))
            implementation(project(":feature:plan"))
            // Icons outside the core set (see KmpFeatureConventionPlugin).
            implementation(libs.compose.mp.material.icons.extended)
        }
    }
}
