pluginManagement {
    apply(from = "gradle/repositories.settings.gradle.kts")
    includeBuild("build-logic-settings")
    includeBuild("build-logic")
}

plugins {
    id("devkit")
    id("library-api-watchdog.develocity-conventions")
}

pluginDevKit {
    includeBuildWithChecks("gradle-plugin")
    includeBuildWithChecks("plugin-annotations")
}

rootProject.name = "library-api-watchdog"

include("library-api-watchdog-compiler-plugin")
project(":library-api-watchdog-compiler-plugin").projectDir = file("compiler-plugin")

include("library-api-watchdog-exempts-fixer")
project(":library-api-watchdog-exempts-fixer").projectDir = file("exempts-fixer")

include("gradle-plugin-test")

// The included builds resolve the compiler plugin and the fixer by their coordinates.
includeBuild(".")
