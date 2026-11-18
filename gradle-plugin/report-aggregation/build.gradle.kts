@file:OptIn(ExperimentalAbiValidation::class)

import kotlinx.library.watchdog.conventions.ChromeForTestingArguments
import kotlinx.library.watchdog.conventions.GenerateCheckDescriptions
import kotlinx.library.watchdog.conventions.chromeForTesting
import kotlinx.library.watchdog.conventions.registerDiagnosticsGenerator
import kotlinx.library.watchdog.conventions.repositoryRoot
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("jvm")
    id("library-api-watchdog.dokka-conventions")
    id("library-api-watchdog.jvm-target-conventions")
    `java-gradle-plugin`
    `maven-publish`
    id("library-api-watchdog.space-publishing-conventions")
    id("library-api-watchdog.plugin-portal-publishing-conventions")
    id("library-api-watchdog.maven-central-publishing-conventions")
}

// The Watchdog Gradle plugin depends on this module and needs Java 17.
jvmTargetConventions {
    release.set(JvmTarget.JVM_17)
}

val generateCheckDescriptions = registerDiagnosticsGenerator<GenerateCheckDescriptions>(
    name = "generateCheckDescriptions",
    description = "Generates the report check descriptions and message templates from diagnostics.json.",
    packageName = "kotlinx.library.watchdog",
    outputDirectory = "checkDescriptions/main/kotlin",
) {
    toolVersion.set(project.version.toString())
}

kotlin {
    explicitApi()
    compilerOptions.optIn.add("kotlinx.library.watchdog.InternalWatchdogApi")
    abiValidation {
        filters {
            exclude {
                annotatedWith.add("kotlinx.library.watchdog.InternalWatchdogApi")
            }
        }
    }

    sourceSets.main {
        kotlin.srcDir(generateCheckDescriptions)
    }
}

dependencies {
    implementation(libs.commonmark)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.jsoup)
    testImplementation(libs.selenium.chrome.driver)
}

// The HTML report script tests run in this browser and driver.
val chromeForTestingDirectories = chromeForTesting(libs.versions.chromeForTesting.get())

tasks.processResources {
    // The HTML report embeds the repository logo as its favicon.
    from(repositoryRoot.file("assets/logo.svg")) {
        into("kotlinx/library/watchdog")
        rename { "favicon.svg" }
    }
}

tasks.test {
    useJUnitPlatform()
    val repositoryLogo = repositoryRoot.file("assets/logo.svg")
    systemProperty("repositoryLogo", repositoryLogo.asFile.absolutePath)
    inputs.file(repositoryLogo)
    jvmArgumentProviders.add(ChromeForTestingArguments(chromeForTestingDirectories))
}

gradlePlugin {
    plugins {
        create("LibraryApiWatchdogReportAggregation") {
            id = "org.jetbrains.kotlin.library.api-watchdog-report-aggregation"
            displayName = "Library API Watchdog report aggregation"
            description =
                "Aggregates the API Watchdog reports of library modules"
            implementationClass =
                "kotlinx.library.watchdog.WatchdogReportAggregationPlugin"
        }
    }
}
