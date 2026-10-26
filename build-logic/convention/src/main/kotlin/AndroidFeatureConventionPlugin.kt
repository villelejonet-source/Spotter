import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.withType

/**
 * A feature module: Android library + Compose + Koin view models + type-safe navigation, wired
 * to the core modules. Lifecycle and navigation are the multiplatform artifacts (on Android they
 * resolve to the androidx ones), ready for the move to shared code. `material-icons-extended` is not included: a module that needs an icon
 * outside the core set declares it itself, so modules on core icons stay light.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("spotter.android.library")
        pluginManager.apply("spotter.android.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        // Not every feature module has tests yet; don't fail on that.
        tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:data-android"))
            add("implementation", project(":core:ui"))

            add("implementation", libs.lib("navigation-mp-compose"))
            add("implementation", libs.lib("lifecycle-mp-runtime-compose"))
            add("implementation", libs.lib("lifecycle-mp-viewmodel-compose"))
            add("implementation", platform(libs.lib("koin-bom")))
            add("implementation", libs.lib("koin-core"))
            add("implementation", libs.lib("koin-compose-viewmodel"))
            add("implementation", libs.lib("androidx-compose-material-icons-core"))
            add("implementation", libs.lib("kotlinx-serialization-json"))
        }
    }
}
