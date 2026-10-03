pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Spotter"

include(":app")
include(":core:model")
include(":core:data")
include(":core:ui")
include(":feature:today")
include(":feature:history")
include(":feature:progress")
include(":feature:settings")
