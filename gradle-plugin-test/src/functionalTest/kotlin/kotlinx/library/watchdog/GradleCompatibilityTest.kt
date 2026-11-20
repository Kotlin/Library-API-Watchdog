@file:Suppress("FunctionName")

package kotlinx.library.watchdog

import com.autonomousapps.kit.gradle.Dependency.Companion.api
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import com.autonomousapps.kit.gradle.Plugin
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.library.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import kotlinx.library.watchdog.Diagnostic.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.jetbrains.kotlin.compiler.plugin.devkit.test.TEST_COMPILER_VERSION
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion
import org.junit.Assume.assumeTrue
import org.junit.Test

class GradleCompatibilityTest {
    @Test
    fun `configuration cache is reused`() {
        val project = jvmProject()
        val arguments = arrayOf("help", "--configuration-cache", "--configuration-cache-problems=fail")

        project.build(*arguments).assertOutputHas("Configuration cache entry stored.")
        project.build(*arguments).assertOutputHas("Configuration cache entry reused.")
    }

    @Test
    fun `works in an isolated subproject`() {
        val project = jvmProject {
            plugins()
            subproject("lib") { source("/** A stable public type. */\npublic class StableApi", "StableApi") }
        }

        project.build(":lib:compileKotlin", *PROJECT_ISOLATION).assertOutputHas("BUILD SUCCESSFUL")
    }

    @Test
    fun `aggregates declared project reports with project isolation`() {
        val project = jvmProject {
            plugins(Plugin("org.jetbrains.kotlin.library.api-watchdog-report-aggregation", pluginUnderTestVersion))
            buildScript(
                """
                    dependencies {
                        apiWatchdogReports(project(":openApi"))
                        apiWatchdogReports(project(":dataApi"))
                    }
                    tasks.aggregateApiWatchdogReports {
                        reports { sarifFile = layout.buildDirectory.file("scan/aggregate.sarif") }
                    }
                """
            )
            subproject("openApi") { source("/** An open API. */\npublic open class OpenApi", "OpenApi") }
            subproject("dataApi") {
                source("/** A data API. */\npublic data class DataApi(public val value: Int)", "DataApi")
            }
        }
        val openApiSource = project.file("openApi/src/main/kotlin/test/OpenApi.kt")
        val dataApiSource = project.file("dataApi/src/main/kotlin/test/DataApi.kt")
        val originalOpenApi = openApiSource.readText()
        val originalDataApi = dataApiSource.readText()

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":openApi:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":dataApi:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":openApi:exemptExistingApi", null)
        result.assertTaskOutcome(":dataApi:exemptExistingApi", null)
        assertEquals(originalOpenApi, openApiSource.readText())
        assertEquals(originalDataApi, dataApiSource.readText())
        val report = project.file("build/reports/api-watchdog/aggregate-report.html")
        assertTrue(report.isFile, result.output)
        val html = report.readText()
        assertContains(html, "Aggregate report for 2 projects")
        assertContains(html, ":openApi")
        assertContains(html, ":dataApi")
        assertReportGroup(html, "IntentionallyOpen", 1, Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        assertReportGroup(html, "IntentionallyDataClass", 1, Diagnostic.DATA_CLASS_PUBLIC_API)
        assertContains(html, "class=\"fix-status not-fixed\">Wasn't auto-fixed</span>")
        assertContains(html, "<code class=\"language-kotlin\">")
        assertContains(html, "public open class OpenApi")
        assertContains(html, "public data class DataApi")
        assertContains(html, "Users can subclass <code>OpenApi</code> outside the library.")
        assertContains(html, "The <code>data class</code> <code>DataApi</code>")
        assertContains(html, "href=\"https://github.com/drewhamilton/Poko\">Poko</a>")
        assertReportedElement(html, "OpenApi", "error")
        assertReportedElement(html, "DataApi", "error")
        val sarif = project.file("build/scan/aggregate.sarif").readText()
        assertContains(sarif, "\"uri\": \"openApi/src/main/kotlin/test/OpenApi.kt\"")
        assertContains(sarif, "\"uri\": \"dataApi/src/main/kotlin/test/DataApi.kt\"")
        assertContains(sarif, "\"ruleId\": \"DATA_CLASS_PUBLIC_API\"")
    }

    @Test
    fun `aggregates every subproject and skips projects without the watchdog`() {
        val project = jvmProject {
            plugins(Plugin("org.jetbrains.kotlin.library.api-watchdog-report-aggregation", pluginUnderTestVersion))
            buildScript(
                """
                    dependencies {
                        subprojects.forEach { apiWatchdogReports(project(it.path)) }
                    }
                """
            )
            subproject("openApi") { source("/** An open API. */\npublic open class OpenApi", "OpenApi") }
            subproject("dataApi") {
                source("/** A data API. */\npublic data class DataApi(public val value: Int)", "DataApi")
            }
            // A Kotlin module without the Watchdog doesn't have a report variant.
            subproject("tools") {
                plugins(Plugin("org.jetbrains.kotlin.jvm"))
                source("/** A tool. */\npublic open class Tool", "Tool")
            }
            subproject("internalApi") {
                apiWatchdog { enabled = false }
                source("/** An internal API. */\npublic open class InternalApi", "InternalApi")
            }
        }

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":openApi:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":dataApi:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":tools:generateApiWatchdogReport", null)
        result.assertTaskOutcome(":internalApi:generateApiWatchdogReport", null)
        result.assertOutputLacks("doesn't match a project of an included build")
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Aggregate report for 2 projects")
        assertContains(html, ":openApi")
        assertContains(html, ":dataApi")
        assertFalse(html.contains(":tools"), html)
        assertFalse(html.contains(":internalApi"), html)
    }

    @Test
    fun `help doesn't realize plugin or compilation tasks`() {
        val compileMarker = "REALIZED_WATCHDOG_COMPILE_TASK"
        val updateMarker = "REALIZED_WATCHDOG_UPDATE_TASK"
        val project = jvmProject {
            explicitApi = false
            buildScript(
                """
                    kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=strict") } }
                    tasks.named("compileKotlin").configure { println("$compileMarker") }
                    tasks.named("exemptExistingApi").configure {
                        println("$updateMarker")
                    }
                """
            )
        }

        val result = project.build("help")

        result.assertOutputLacks(compileMarker)
        result.assertOutputLacks(updateMarker)
    }

    @Test
    fun `realized tasks keep configured severities`() {
        // `tasks.all { }` realizes the exemption tasks without a schedule.
        val project = jvmProject {
            buildScript("tasks.all { }")
            source("/** An open API. */\npublic open class OpenApi", "OpenApi")
        }

        project.buildAndFail("compileKotlin").assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
    }

    @Test
    fun `compile task is cached for a checkout in another directory`() {
        val project = jvmProject {
            consumerSource()
            dependencies(api(":model"))
            modelSubproject()
        }
        project.useLocalBuildCache()
        val relocated = project.copyTo("${project.rootDir.name}-relocated")

        project.build("compileKotlin", "--build-cache")

        relocated.build("compileKotlin", "--build-cache").assertTaskOutcome(":compileKotlin", FROM_CACHE)
    }

    @Test
    fun `multiplatform compile tasks are cached for a checkout in another directory`() {
        val project = kmpProject("jvm()\njs { nodejs() }") {
            consumerSource()
            dependencies(api(":model"))
            modelSubproject()
        }
        project.useLocalBuildCache()
        val relocated = project.copyTo("${project.rootDir.name}-relocated")
        val tasks = arrayOf("compileKotlinJvm", "compileKotlinJs", "compileCommonMainKotlinMetadata")

        project.build(*tasks, "--build-cache")

        val result = relocated.build(*tasks, "--build-cache")
        tasks.forEach { result.assertTaskOutcome(":$it", FROM_CACHE) }
    }

    @Test
    fun `cached compile task isn't reused after a dependency exemption is removed`() {
        val exemption = "dependencies { apiWatchdogExemptNonTransitive(project(\":model\")) }"
        val project = jvmProject {
            consumerSource()
            dependencies(implementation(":model"))
            buildScript(exemption)
            modelSubproject()
        }
        project.useLocalBuildCache()
        project.build("compileKotlin", "--build-cache")
        val script = project.file("build.gradle.kts")
        script.writeText(script.readText().replace(exemption, ""))

        val result = project.buildAndFail("clean", "compileKotlin", "--build-cache")

        result.assertTaskOutcome(":compileKotlin", FAILED)
        result.assertErrors(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)
    }

    @Test
    fun `compile task stays up to date after a function body changes in a dependency`() {
        val project = jvmProject {
            consumerSource()
            dependencies(api(":model"))
            modelSubproject()
        }
        val model = project.file("model/src/main/kotlin/test/model/ExternalModel.kt")
        val withFunction = "public class ExternalModel {\n    public fun size(): Int = 1\n}"
        model.writeText(model.readText().replace("public class ExternalModel", withFunction))
        project.build("compileKotlin")
        model.writeText(model.readText().replace("= 1", "= 2"))

        val result = project.build("compileKotlin")

        result.assertTaskOutcome(":model:compileKotlin", SUCCESS)
        result.assertTaskOutcome(":compileKotlin", UP_TO_DATE)
    }

    /** The cache is outside the build, so a copy of the build in another directory has the same cache. */
    private fun WatchdogBuild.useLocalBuildCache() {
        val cache = rootDir.resolveSibling("${rootDir.name}-build-cache").invariantSeparatorsPath
        file("settings.gradle.kts").appendText("\nbuildCache { local { directory = file(\"$cache\") } }\n")
    }

    @Test
    fun `a convention plugin in Java configures the reports`() {
        val project = jvmProject {
            plugins(Plugin(KOTLIN_JVM_PLUGIN_ID), Plugin("test.conventions"))
            buildSrc {
                plugins(Plugin.javaGradle)
                buildScript(
                    """
                        gradlePlugin {
                            plugins {
                                create("conventions") {
                                    id = "test.conventions"
                                    implementationClass = "test.ConventionPlugin"
                                }
                            }
                        }
                        dependencies {
                            implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$TEST_COMPILER_VERSION")
                            implementation("org.jetbrains.kotlinx:library-api-watchdog-gradle-plugin:$pluginUnderTestVersion")
                        }
                    """
                )
                javaFile(
                    "ConventionPlugin",
                    """
                        package test;

                        import kotlinx.library.watchdog.WatchdogGradleExtension;
                        import org.gradle.api.Plugin;
                        import org.gradle.api.Project;

                        public class ConventionPlugin implements Plugin<Project> {
                            @Override
                            public void apply(Project project) {
                                project.getPluginManager().apply("org.jetbrains.kotlin.library.api-watchdog");
                                project.getExtensions().configure(
                                    WatchdogGradleExtension.class,
                                    watchdog -> watchdog.reports(reports -> reports.getSarif().set(false))
                                );
                            }
                        }
                    """,
                )
            }
            buildScript("kotlin { explicitApi() }")
            source("/** An open API. */\npublic open class OpenApi", "OpenApi")
        }
        // buildSrc resolves the plugin from the repositories of the root build.
        project.file("settings.gradle.kts").copyTo(project.file("buildSrc/settings.gradle.kts"))

        project.build("generateApiWatchdogReport")

        assertTrue(project.file("build/reports/api-watchdog/report.html").isFile)
        assertFalse(project.file("build/reports/api-watchdog/report.sarif").exists())
    }

    @Test
    fun `works on the minimum Gradle version`() {
        assumeKotlinSupportsOlderGradle()
        val project = jvmProject {
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject()
        }.withGradle(MINIMUM_GRADLE_VERSION)

        project.buildAndFail("compileKotlin").assertErrors(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)
        project.build("generateApiWatchdogReport", "--configuration-cache")
        assertTrue(project.file("build/reports/api-watchdog/report.html").isFile)
    }

    @Test
    fun `fails on a Gradle version before the minimum`() {
        assumeKotlinSupportsOlderGradle()

        val result = jvmProject().withGradle("8.10.2").buildAndFail("help")

        result.assertOutputHas(
            "library-api-watchdog needs Gradle $MINIMUM_GRADLE_VERSION or later, and this build runs on Gradle 8.10.2."
        )
    }

    /** Kotlin 2.5.0 needs Gradle 8.14.4. */
    private fun assumeKotlinSupportsOlderGradle() {
        assumeTrue(TEST_COMPILER_VERSION.toKotlinVersion() < KotlinVersion(2, 5))
    }

    private companion object {
        const val MINIMUM_GRADLE_VERSION = "8.11"
        val PROJECT_ISOLATION = arrayOf("-Dorg.gradle.unsafe.isolated-projects=true", "--configuration-cache-problems=fail")
    }
}
