pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // libadb-android and sun-security-android, used only by Argos Setup
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.MuntashirAkon.*") } }
    }
}

rootProject.name = "argos"
include(":app")
include(":setup")
