plugins {
    alias(libs.plugins.spotter.android.feature)
    alias(libs.plugins.spotter.android.screenshots)
}

dependencies {
    implementation(project(":core:engine"))
    // Icons outside the core set (see AndroidFeatureConventionPlugin).
    implementation(libs.androidx.compose.material.icons.extended)
}
