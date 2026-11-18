pluginManagement {
    apply(from = "../gradle/repositories.settings.gradle.kts")
    includeBuild("../build-logic-settings")
    includeBuild("../build-logic")
}

plugins { id("devkit-included") }

rootProject.name = "library-api-watchdog-plugin-annotations"
