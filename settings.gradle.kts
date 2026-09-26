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
    }
}

rootProject.name = "FlowState"
include(":app")
include(":core:data")
include(":core:designsystem")
include(":feature:flow")
include(":core:domain")
include(":feature:calendar")
include(":core:testing")
include(":feature:habits")
include(":core:notifications")
include(":core:widgets")
include(":feature:settings")
include(":benchmark")
