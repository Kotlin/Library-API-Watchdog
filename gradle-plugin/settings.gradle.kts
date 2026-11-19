pluginManagement {
    apply(from = "../gradle/repositories.settings.gradle.kts")
    includeBuild("../build-logic-settings")
    includeBuild("../build-logic")
}

plugins { id("devkit-included") }

dependencyResolutionManagement {
    repositories {
        // The report aggregation tests run in Chrome for Testing. The group is `CHROME_FOR_TESTING_GROUP` of build-logic.
        exclusiveContent {
            forRepository {
                ivy("https://storage.googleapis.com/chrome-for-testing-public") {
                    name = "Chrome for Testing"
                    patternLayout { artifact("[revision]/[classifier]/[artifact]-[classifier].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("chrome-for-testing") }
        }
    }
}

rootProject.name = "library-api-watchdog-gradle-plugin"

include("library-api-watchdog-report-aggregation")
project(":library-api-watchdog-report-aggregation").projectDir = file("report-aggregation")
