// The framework the iOS app links (`Shared`): the shared modules plus a small Swift-facing
// entry point. Xcode builds it with `:shared:embedAndSignAppleFrameworkForXcode` (see iosApp/).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
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
            // The screens ported to shared code so far, linked in so the iOS build proves they
            // link; 4d puts them on screen.
            implementation(project(":feature:today"))
            implementation(project(":feature:history"))
            implementation(project(":feature:progress"))
            implementation(project(":feature:library"))
            implementation(project(":feature:plan"))
            implementation(project(":feature:onboarding"))
            implementation(project(":feature:settings"))
        }
    }
}
