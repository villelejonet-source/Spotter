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
 *
 * In a multiplatform module (`spotter.kmp.compose`) the tests are Android host tests
 * (`src/androidHostTest`), and Roborazzi's tasks are named after that compilation. The usual
 * `*Debug` task names are registered there too, so the commands above still cover every module.
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
        val multiplatform = pluginManager.hasPlugin("com.android.kotlin.multiplatform.library")
        val test = if (multiplatform) "androidHostTestImplementation" else "testImplementation"
        dependencies {
            add(test, libs.lib("roborazzi"))
            add(test, libs.lib("roborazzi-compose"))
            add(test, libs.lib("robolectric"))
            add(test, libs.lib("junit4"))
            add(test, libs.lib("androidx-test-ext-junit"))
            add(test, libs.lib("androidx-compose-ui-test-junit4"))
            add(test, platform(libs.lib("androidx-compose-bom")))
            add(if (multiplatform) test else "debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
        }
        if (multiplatform) {
            tasks.register("testDebugUnitTest") { dependsOn("testAndroidHostTest") }
            listOf("record", "verify", "compare").forEach { mode ->
                tasks.register("${mode}RoborazziDebug") { dependsOn("${mode}RoborazziAndroidHostTest") }
            }
        }
    }
}
