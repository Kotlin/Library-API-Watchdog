package kotlinx.library.watchdog.conventions

import java.io.File
import java.util.Properties
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.compiler.plugin.devkit.GradlePluginConfigExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

class GradlePluginTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val generateFunctionalTestDiagnostics = registerDiagnosticsGenerator<GenerateFunctionalTestDiagnostics>(
                name = "generateFunctionalTestDiagnostics",
                description = "Generates the Diagnostic enum of the functional tests from diagnostics.json.",
                packageName = "kotlinx.library.watchdog",
                outputDirectory = "functionalTestDiagnostics/functionalTest/kotlin",
            )
            extensions.configure(KotlinProjectExtension::class.java) {
                sourceSets.named("functionalTest").configure {
                    kotlin.srcDir(generateFunctionalTestDiagnostics)
                }
            }

            // The Gradle plugin resolves the fixer from the repository of the test build. No functional test
            // calls the fixer classes, so the fixer isn't on the classpath of the tests.
            tasks.named("installForFunctionalTest").configure {
                dependsOn(":library-api-watchdog-exempts-fixer:installForFunctionalTest")
            }

            // The publication of this project goes only to the repository of the functional tests.
            excludeFromArtifactLists()

            val pluginDevKit = extensions.getByType(GradlePluginConfigExtension::class.java)
            tasks.register("printCiFunctionalTestMatrix") {
                group = "help"
                description = "Prints the Kotlin versions of the functional tests as step outputs for CI."

                val versions = pluginDevKit.testAgainst.map { it.version }
                val matrix = when (val kind = providers.gradleProperty(MATRIX_PROPERTY).getOrElse("full")) {
                    "full" -> fullFunctionalTestMatrix(versions)
                    "reduced" -> reducedFunctionalTestMatrix(versions)
                    else -> error("The Gradle property '$MATRIX_PROPERTY' is '$kind'. Set it to 'full' or 'reduced'.")
                }
                val output = matrix.toGitHubOutput()

                doLast {
                    logger.quiet(output)
                }
            }

            val agp8Version = catalogVersion("agp8")
            val agp9Version = catalogVersion("agp9")
            val bcvVersion = catalogVersion("bcv")
            val androidSdk = androidSdkDirOrNull(target)
            // A TestKit build doesn't read the Gradle properties of the user home.
            val proxyToken = mapOf(
                "WATCHDOG_PROXY_USERNAME" to providers.gradleProperty("space.username.watchdog"),
                "WATCHDOG_PROXY_PASSWORD" to providers.gradleProperty("space.password.watchdog"),
            )
            val useProxies = providers.gradleProperty("watchdog.proxies").orNull != "false"
            val oneTestTaskAtATime =
                gradle.sharedServices.registerIfAbsent("functionalTestTaskLock", FunctionalTestTaskLock::class.java) {
                    maxParallelUsages.set(1)
                }
            val smokeOnly = providers.gradleProperty(SMOKE_ONLY_PROPERTY).map(String::toBoolean).getOrElse(false)
            tasks.withType(Test::class.java).configureEach {
                systemProperty("watchdog.test.proxies", useProxies)
                proxyToken.forEach { (variable, value) ->
                    if (value.isPresent) environment(variable, value.get())
                }
                systemProperty("watchdog.test.agp8Version", agp8Version)
                systemProperty("watchdog.test.agp9Version", agp9Version)
                systemProperty("watchdog.test.bcvVersion", bcvVersion)
                androidSdk?.let { sdk ->
                    systemProperty("watchdog.test.androidHome", sdk.absolutePath)
                }
                // Each test fork runs TestKit builds with their own daemons. The 16 GB CI runners can't fit one
                // fork for each CPU, or the forks of two test tasks. See `ciSingleVersion` in devkit.kt.
                if (ciSingleVersion) {
                    maxParallelForks = 2
                    usesService(oneTestTaskAtATime)
                }
                if (smokeOnly) {
                    useJUnit { includeCategories("kotlinx.library.watchdog.SmokeTest") }
                }
            }
        }
    }

    private companion object {
        const val MATRIX_PROPERTY = "watchdog.functionalTest.matrix"
        const val SMOKE_ONLY_PROPERTY = "watchdog.functionalTest.smokeOnly"
    }

    private fun androidSdkDirOrNull(project: Project): File? {
        val localProperties = project.repositoryRoot.file("local.properties").asFile
        if (localProperties.exists()) {
            val properties = Properties()
            localProperties.inputStream().use(properties::load)
            properties.getProperty("sdk.dir")?.let(::File)?.takeIf(File::isDirectory)?.let { return it }
        }
        val home = File(System.getProperty("user.home"))
        return sequenceOf(
            System.getenv("ANDROID_HOME")?.let(::File),
            System.getenv("ANDROID_SDK_ROOT")?.let(::File),
            home.resolve("Library/Android/sdk"),
            home.resolve("Android/Sdk"),
            System.getenv("LOCALAPPDATA")?.let { File(it).resolve("Android/Sdk") },
        ).filterNotNull().firstOrNull(File::isDirectory)
    }
}

internal abstract class FunctionalTestTaskLock : BuildService<BuildServiceParameters.None>
