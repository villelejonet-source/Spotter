plugins {
    alias(libs.plugins.spotter.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

// Tests load the real exercise catalog and the shared training-math cases; native test binaries can't resolve a relative path,
// so its absolute location is generated into the test sources.
val generateCatalogPath by tasks.registering {
    val catalog = rootProject.layout.projectDirectory.file("core/data/catalog/exercises.json").asFile.absolutePath
    val trainingMath = rootProject.layout.projectDirectory.file("core/model/fixtures/training-math.json").asFile.absolutePath
    val outputDir = layout.buildDirectory.dir("generated/catalogPath")
    inputs.property("catalog", catalog)
    inputs.property("trainingMath", trainingMath)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("CatalogPath.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.viktorolsson.spotter.core.engine\n\ninternal const val CATALOG_PATH = \"$catalog\"\n" +
                "internal const val TRAINING_MATH_PATH = \"$trainingMath\"\n",
        )
    }
}
kotlin.sourceSets.commonTest {
    kotlin.srcDir(generateCatalogPath)
}
