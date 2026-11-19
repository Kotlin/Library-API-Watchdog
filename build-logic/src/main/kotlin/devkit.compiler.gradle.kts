import kotlinx.library.watchdog.conventions.catalogVersion
import kotlinx.library.watchdog.conventions.ciSingleVersion
import kotlinx.library.watchdog.conventions.SHARD_PROPERTY
import kotlinx.library.watchdog.conventions.config
import kotlinx.library.watchdog.conventions.oneVersionOfEachReleaseLine
import kotlinx.library.watchdog.conventions.reducedCompilerTests
import kotlinx.library.watchdog.conventions.shard
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitTarget
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

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

// `useLatestDev` adds the newest Kotlin dev build as a CLI target, so the highest CLI version is this dev build.
fun newestKotlinTarget(): DevKitTarget = pluginDevKit.testAgainst.filter { !it.forIde }.maxBy { it.version }

// The daily CI run uses this task.
tasks.register("latestKotlinTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs tests against the newest Kotlin version of the CLI targets"
    dependsOn(provider { newestKotlinTarget().testTask })
}

// A pull request runs the fuzz tests against one release of each release line only. They take most of the test time.
val fuzzTestVersions by lazy {
    val releases = pluginDevKit.testAgainst.filter { !it.forIde && it.version.maturity != KotlinToolingVersion.Maturity.DEV }
    oneVersionOfEachReleaseLine(releases.map { it.version }).toSet()
}

fun DevKitTarget.runsFuzzTests(): Boolean = !reducedCompilerTests || (!forIde && version in fuzzTestVersions)

pluginDevKit.testAgainst.configureEach {
    val target = this
    testTask.configure {
        if (!target.runsFuzzTests()) {
            filter.excludeTestsMatching("*FuzzTest")
        }
    }
}

// Each job of the "Compiler plugin tests" workflow runs one shard. The order gives each shard the same number of
// targets with fuzz tests, of CLI targets, and of IDE targets. Their tests take different times.
//
// A new Kotlin dev build can break the plugin, so a pull request doesn't test it. Its target stays, because the
// `.fir.txt` dump without a version belongs to the highest version of the targets.
tasks.register("shardTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the tests of one shard of the Kotlin versions, -P$SHARD_PROPERTY=<index>/<count>"
    dependsOn(
        provider {
            pluginDevKit.testAgainst
                .filter { !reducedCompilerTests || it != newestKotlinTarget() }
                .sortedWith(compareBy({ !it.runsFuzzTests() }, { it.forIde }, { it.version }))
                .shard(providers.gradleProperty(SHARD_PROPERTY).getOrElse(""))
                .map { it.testTask }
        }
    )
}
