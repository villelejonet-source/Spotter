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

// Play upload key, from the untracked keystore.properties (see docs/play-store/README.md).
val keystoreProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull
        ?.let { load(it.reader()) }
}
val hasUploadKey = keystoreProperties.getProperty("storeFile") != null

/**
 * The upload key password: from keystore.properties if it's there, otherwise from the
 * macOS Keychain item named by `keychainService` (default "spotter-upload-key"), so the
 * password doesn't have to live in a file.
 */
val uploadKeyPassword: String? by lazy {
    keystoreProperties.getProperty("storePassword")?.takeIf { it.isNotBlank() }
        ?: providers.exec {
            commandLine(
                "security", "find-generic-password", "-w",
                "-s", keystoreProperties.getProperty("keychainService", "spotter-upload-key"),
            )
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
}

android {
    namespace = "com.viktorolsson.spotter"

    defaultConfig {
        applicationId = "com.viktorolsson.spotter"
        versionCode = 1
        versionName = "0.9.0"

        buildConfigField("String", "SUPABASE_URL", "\"${localProperty("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${localProperty("supabase.publishableKey")}\"")
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = uploadKeyPassword
                keyAlias = keystoreProperties.getProperty("keyAlias")
                // keytool's default: the key password is the keystore password.
                keyPassword = keystoreProperties.getProperty("keyPassword")?.takeIf { it.isNotBlank() } ?: uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Without the upload key, release builds are debug-signed so R8 output can be
            // tested locally; such builds can't be uploaded to Play (bundleRelease checks).
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
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
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:plan"))

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

// Refuse to produce a Play bundle signed with the debug key.
tasks.matching { it.name == "bundleRelease" }.configureEach {
    doFirst {
        check(hasUploadKey) { "bundleRelease needs keystore.properties with the upload key (see docs/play-store/README.md)." }
        checkNotNull(uploadKeyPassword) {
            "No upload key password: add it to keystore.properties or the Keychain item \"spotter-upload-key\"."
        }
    }
}
