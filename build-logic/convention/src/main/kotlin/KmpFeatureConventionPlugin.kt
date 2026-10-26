import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A feature module in shared code (Android + iOS): `spotter.kmp.compose` plus Koin view models,
 * type-safe navigation and lifecycle, wired to the shared core modules. The multiplatform
 * counterpart of `spotter.android.feature`; modules move over as they're ported.
 * `material-icons-extended` is not included, as there: a module that needs it declares it.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("spotter.kmp.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        // Not every feature module has tests yet; don't fail on that.
        tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }

        extensions.getByType<KotlinMultiplatformExtension>().sourceSets.getByName("commonMain").dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:data"))
            implementation(project(":core:ui"))

            implementation(libs.lib("navigation-mp-compose"))
            implementation(libs.lib("lifecycle-mp-runtime-compose"))
            implementation(libs.lib("lifecycle-mp-viewmodel-compose"))
            implementation(project.dependencies.platform(libs.lib("koin-bom")))
            implementation(libs.lib("koin-core"))
            implementation(libs.lib("koin-compose-viewmodel"))
            implementation(libs.lib("compose-mp-material-icons-core"))
            implementation(libs.lib("kotlinx-serialization-json"))
        }
    }
}
