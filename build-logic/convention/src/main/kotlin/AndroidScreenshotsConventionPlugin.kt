import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Roborazzi screenshot tests (Robolectric, JVM). Golden images live in
 * `src/test/screenshots`; `./gradlew recordRoborazziDebug` updates them and
 * `verifyRoborazziDebug` fails on visual changes.
 */
class AndroidScreenshotsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")
        extensions.configure<RoborazziExtension> {
            outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        }
        // Screens that show times (e.g. "Last synced") render in the JVM's zone. Pin it so
        // goldens recorded on a Mac in Sweden match CI runners, which run in UTC.
        tasks.withType<Test>().configureEach {
            systemProperty("user.timezone", "Europe/Stockholm")
        }
        dependencies {
            add("testImplementation", libs.lib("roborazzi"))
            add("testImplementation", libs.lib("roborazzi-compose"))
            add("testImplementation", libs.lib("robolectric"))
            add("testImplementation", libs.lib("junit4"))
            add("testImplementation", libs.lib("androidx-test-ext-junit"))
            add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
            add("testImplementation", platform(libs.lib("androidx-compose-bom")))
            add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
        }
    }
}
