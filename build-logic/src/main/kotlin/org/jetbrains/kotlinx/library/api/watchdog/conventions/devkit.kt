package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitPropertiesDsl

// The "Gradle tests" workflow sets WATCHDOG_CI_SINGLE_VERSION in lanes that test one Kotlin version. The dev kit
// then registers only the versions that the `kotlin.compiler.plugin.devkit.*` Gradle properties request, or the
// build's own Kotlin version when they request none. The Gradle plugin tests then run fewer forked JVMs per test
// task.
val DevKitPropertiesDsl<*>.ciSingleVersion: Boolean
    get() = project.ciSingleVersion

val Project.ciSingleVersion: Boolean
    get() = providers.environmentVariable("WATCHDOG_CI_SINGLE_VERSION").getOrElse("").toBoolean()

/** The Kotlin versions that every dev kit project builds or tests against. */
fun DevKitPropertiesDsl<*>.config() {
    if (!ciSingleVersion) {
        cliVersions(project.catalogVersion("kotlinCliMin"))
    }
}

fun Project.catalogVersion(name: String): String =
    extensions.getByType(VersionCatalogsExtension::class.java).named("libs").findVersion(name).get().requiredVersion
