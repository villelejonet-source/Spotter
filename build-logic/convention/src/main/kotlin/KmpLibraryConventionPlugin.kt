import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Pure Kotlin module shared by the Android and iOS apps (core:model, core:engine).
 * The Android app consumes the JVM target; iOS consumes the native targets.
 * Tests live in `commonTest` (kotlin.test) and run on both: `jvmTest` and `iosSimulatorArm64Test`.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")

        extensions.configure<KotlinMultiplatformExtension> {
            jvm {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
            iosArm64()
            iosSimulatorArm64()

            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.lib("kotlin-test"))
            }
        }
    }
}
