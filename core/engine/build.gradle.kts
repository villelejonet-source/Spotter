plugins {
    alias(libs.plugins.spotter.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.serialization.json)
}
