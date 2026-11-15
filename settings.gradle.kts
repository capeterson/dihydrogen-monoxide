pluginManagement {
    includeBuild("build-logic")
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
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "dihydrogen-monoxide"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common", ":core:model", ":core:domain", ":core:backup")
include(":core:database", ":core:datastore", ":core:data")
include(":core:designsystem", ":core:network", ":core:reminders", ":core:weather", ":core:testing")
include(":feature:onboarding", ":feature:today", ":feature:history", ":feature:insights")
include(":feature:profile", ":feature:reminders", ":feature:settings")
include(":sync:healthconnect", ":sync:drive")
include(":widget")
