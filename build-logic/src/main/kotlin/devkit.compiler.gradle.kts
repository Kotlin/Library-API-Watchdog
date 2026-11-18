import kotlinx.library.watchdog.conventions.catalogVersion
import kotlinx.library.watchdog.conventions.ciSingleVersion
import kotlinx.library.watchdog.conventions.config
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetWithTests

plugins {
    kotlin("compiler.plugin.devkit.compiler-plugin")
}

pluginDevKit {
    config()
    if (!ciSingleVersion) {
        ideaVersions(catalogVersion("kotlinIdeMin"), includeRc = true, includeEap = true)
        useLatestDev()
    }
}

// The daily CI run uses this task. `useLatestDev` adds the newest Kotlin dev build as a CLI target, so the highest CLI
// version is this dev build.
tasks.register("latestKotlinTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs tests against the newest Kotlin version of the CLI targets"
    dependsOn(
        provider {
            pluginDevKit.testAgainst
                .filter { !it.forIde }
                .maxBy { it.version }
                .testRuns.getByName(KotlinTargetWithTests.DEFAULT_TEST_RUN_NAME)
                .executionTask
        }
    )
}
