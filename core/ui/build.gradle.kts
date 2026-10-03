plugins {
    alias(libs.plugins.spotter.android.library)
    alias(libs.plugins.spotter.android.compose)
}

dependencies {
    api(project(":core:model"))
}
