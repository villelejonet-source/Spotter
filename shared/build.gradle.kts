// The framework the iOS app links (`Shared`): the shared app (`:shell` and everything below it)
// plus a small Swift-facing entry point. Xcode builds it with
// `:shared:embedAndSignAppleFrameworkForXcode` (see iosApp/), which also copies every module's
// Compose resources (strings) into the app bundle.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":core:data"))
            implementation(project(":shell"))
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.ui)
        }
    }
}
