pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

includeBuild("build-logic")

rootProject.name = "lingxi"
include(":app")
include(":common")
include(":ai")
include(":material3")
include(":search")
include(":highlight")
include(":speech")
include(":workspace")
include(":document")
include(":videogen")
