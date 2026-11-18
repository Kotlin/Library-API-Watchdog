pluginManagement {
    includeBuild("build-logic-settings")
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap") {
            // Space can challenge a public read on a GitHub runner. With an empty username, Gradle
            // retries the challenge as an anonymous request.
            credentials {
                username = ""
                password = ""
            }
        }
    }
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
