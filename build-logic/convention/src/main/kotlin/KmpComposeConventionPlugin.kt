import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Shared UI module for the Android and iOS apps: Kotlin Multiplatform with Compose Multiplatform
 * (core:ui, and the feature modules as they move). Code lives in `commonMain`; platform pieces
 * in `androidMain` / `iosMain`.
 *
 * On Android the Compose Multiplatform artifacts resolve to the androidx ones, so the app gets
 * the same Compose it had as a plain Android library.
 *
 * Strings and plurals are Compose resources in `src/commonMain/composeResources`, read with
 * `org.jetbrains.compose.resources.stringResource`. The generated `Res` class is public and
 * sits in `<namespace>.resources`, e.g. `com.viktorolsson.spotter.core.ui.resources.Res`.
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.compose")

        val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
        (kotlin as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
            namespace = spotterNamespace
            compileSdk = SpotterSdk.COMPILE
            minSdk = SpotterSdk.MIN
            androidResources.enable = true
            withHostTest {
                isIncludeAndroidResources = true
            }
        }
        configureKotlinJvmTarget()
        kotlin.apply {
            iosArm64()
            iosSimulatorArm64()

            sourceSets.getByName("commonMain").dependencies {
                implementation(libs.lib("compose-mp-runtime"))
                implementation(libs.lib("compose-mp-foundation"))
                implementation(libs.lib("compose-mp-ui"))
                implementation(libs.lib("compose-mp-material3"))
                implementation(libs.lib("compose-mp-ui-tooling-preview"))
                api(libs.lib("compose-mp-resources"))
            }
            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.lib("kotlin-test"))
            }
        }

        (extensions.getByType<ComposeExtension>() as ExtensionAware).extensions.configure<ResourcesExtension> {
            publicResClass = true
            packageOfResClass = "$spotterNamespace.resources"
            generateResClass = ResourcesExtension.ResourceClassGeneration.Always
        }
    }
}
