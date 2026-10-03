plugins {
    alias(libs.plugins.spotter.android.application)
    alias(libs.plugins.spotter.android.compose)
    alias(libs.plugins.spotter.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.viktorolsson.spotter"

    defaultConfig {
        applicationId = "com.viktorolsson.spotter"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:today"))
    implementation(project(":feature:history"))
    implementation(project(":feature:progress"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
}
