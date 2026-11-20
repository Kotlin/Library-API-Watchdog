package kotlinx.library.watchdog.conventions

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitPropertiesDsl

// With WATCHDOG_CI_SINGLE_VERSION, the dev kit registers only the versions that the `kotlin.compiler.plugin.devkit.*`
// Gradle properties request, or the build's own Kotlin version when they request none.
val DevKitPropertiesDsl<*>.ciSingleVersion: Boolean
    get() = project.ciSingleVersion

val Project.ciSingleVersion: Boolean
    get() = providers.environmentVariable("WATCHDOG_CI_SINGLE_VERSION").getOrElse("").toBoolean()

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
