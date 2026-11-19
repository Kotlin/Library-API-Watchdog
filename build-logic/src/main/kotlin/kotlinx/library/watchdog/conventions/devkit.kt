package kotlinx.library.watchdog.conventions

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitPropertiesDsl

// The "Gradle tests" workflow sets WATCHDOG_CI_SINGLE_VERSION in the jobs of the Gradle plugin tests. The dev kit
// then registers only the versions that the `kotlin.compiler.plugin.devkit.*` Gradle properties request, or the
// build's own Kotlin version when they request none. The Gradle plugin tests then run one test task at a time with
// fewer forked JVMs, and their TestKit daemons get a heap limit.
val DevKitPropertiesDsl<*>.ciSingleVersion: Boolean
    get() = project.ciSingleVersion

val Project.ciSingleVersion: Boolean
    get() = providers.environmentVariable("WATCHDOG_CI_SINGLE_VERSION").getOrElse("").toBoolean()

// A pull request run of the "Compiler plugin tests" workflow sets this Gradle property.
internal val Project.reducedCompilerTests: Boolean
    get() = providers.gradleProperty("watchdog.compilerTest.reduced").map(String::toBoolean).getOrElse(false)

/** The Kotlin versions that every dev kit project builds or tests against. */
fun DevKitPropertiesDsl<*>.config() {
    if (!ciSingleVersion) {
        cliVersions(project.catalogVersion("kotlinCliMin"))
    }
}

fun Project.catalogVersion(name: String): String =
    extensions.getByType(VersionCatalogsExtension::class.java).named("libs").findVersion(name).get().requiredVersion
