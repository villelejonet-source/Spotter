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
        register("androidFeature") {
            id = "spotter.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("hilt") {
            id = "spotter.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("androidScreenshots") {
            id = "spotter.android.screenshots"
            implementationClass = "AndroidScreenshotsConventionPlugin"
        }
        register("jvmLibrary") {
            id = "spotter.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
