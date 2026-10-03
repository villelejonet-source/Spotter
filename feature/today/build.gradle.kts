plugins {
    alias(libs.plugins.spotter.android.feature)
    alias(libs.plugins.spotter.android.screenshots)
}

dependencies {
    implementation(project(":core:engine"))
}
