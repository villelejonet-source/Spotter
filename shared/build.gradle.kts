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
        }
    }
}
