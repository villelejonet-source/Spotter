import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.withType

/** A feature module: Android library + Compose + Hilt + type-safe navigation, wired to the core modules. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("spotter.android.library")
        pluginManager.apply("spotter.android.compose")
        pluginManager.apply("spotter.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        // Hilt generates test sources even in modules with no tests yet; don't fail on that.
        tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:ui"))

            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-compose-material-icons-extended"))
            add("implementation", libs.lib("kotlinx-serialization-json"))
        }
    }
}
