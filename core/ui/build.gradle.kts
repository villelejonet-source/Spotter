plugins {
    alias(libs.plugins.spotter.android.library)
    alias(libs.plugins.spotter.android.compose)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.vico.compose.m3)
    implementation(libs.androidx.compose.material.icons.extended)
}
