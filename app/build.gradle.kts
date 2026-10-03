import java.util.Properties

plugins {
    alias(libs.plugins.spotter.android.application)
    alias(libs.plugins.spotter.android.compose)
    alias(libs.plugins.spotter.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Supabase config comes from the untracked local.properties (see README); empty values mean "sync not configured".
val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
        ?.let { load(it.reader()) }
}
fun localProperty(key: String): String = localProperties.getProperty(key, "")

android {
    namespace = "com.viktorolsson.spotter"

    defaultConfig {
        applicationId = "com.viktorolsson.spotter"
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${localProperty("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${localProperty("supabase.publishableKey")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
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
    implementation(project(":feature:library"))
    implementation(project(":feature:session"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
}
