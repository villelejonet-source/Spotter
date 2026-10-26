// Android wiring for the shared :core:data: the Hilt graph, WorkManager background sync, the
// encrypted session store and the bundled exercise catalog as an asset.
plugins {
    alias(libs.plugins.spotter.android.library)
    alias(libs.plugins.spotter.hilt)
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("main").assets.directories.add("../data/catalog")
}

dependencies {
    api(project(":core:data"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.framework)
    // Auth session and PKCE verifier live in EncryptedSharedPreferences (see AuthStorage).
    implementation(libs.androidx.security.crypto)
    implementation(libs.multiplatform.settings)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
