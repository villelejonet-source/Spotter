plugins {
    `kotlin-dsl`
}

group = "com.viktorolsson.spotter.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.roborazzi.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "spotter.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "spotter.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "spotter.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidScreenshots") {
            id = "spotter.android.screenshots"
            implementationClass = "AndroidScreenshotsConventionPlugin"
        }
        register("kmpCompose") {
            id = "spotter.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("kmpFeature") {
            id = "spotter.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
        register("kmpLibrary") {
            id = "spotter.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
    }
}
