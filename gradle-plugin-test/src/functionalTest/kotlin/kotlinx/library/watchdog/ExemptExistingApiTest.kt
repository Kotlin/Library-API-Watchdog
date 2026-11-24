@file:Suppress("RedundantVisibilityModifier", "FunctionName")

package kotlinx.library.watchdog

import com.autonomousapps.kit.gradle.Dependency.Companion.api
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import com.autonomousapps.kit.gradle.Plugin
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.library.watchdog.Diagnostic.EXEMPTION_WITHOUT_EXPLANATION
import kotlinx.library.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import kotlinx.library.watchdog.Diagnostic.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY
import kotlinx.library.watchdog.Diagnostic.TOP_LEVEL_API_WITHOUT_JVM_NAME
import kotlinx.library.watchdog.Diagnostic.UNDOCUMENTED_PUBLIC_API
import kotlinx.library.watchdog.Severity.ERROR
import kotlinx.library.watchdog.Severity.NONE
import kotlinx.library.watchdog.Severity.WARNING
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.devkit.test.KmpTarget
import org.jetbrains.kotlin.compiler.plugin.devkit.test.KotlinPlugins
import org.jetbrains.kotlin.compiler.plugin.devkit.test.TEST_COMPILER_VERSION
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion
import org.junit.Test
import org.junit.experimental.categories.Category

class ExemptExistingApiTest {
    @Test
    fun `exempt task reuses the configuration cache`() {
        val project = jvmProject {
            source("/** An existing open API. */\npublic open class ExistingApi", "existingApi")
        }
        val arguments = arrayOf(EXEMPT_TASK, "--configuration-cache", "--configuration-cache-problems=fail")

        project.build(*arguments).assertOutputHas("Configuration cache entry stored.")

        val second = project.build(*arguments)
        second.assertOutputHas("Configuration cache entry reused.")
        second.assertOutputHas("No watchdog errors to exempt.")
    }

    @Test
    @Category(SmokeTest::class)
    fun `fixes every fixable diagnostic and the build passes`() {
        val project = jvmProject {
            buildScript(CONTEXT_PARAMETERS_BUILD_SCRIPT)
            apiWatchdog { severity(ERROR, UNDOCUMENTED_PUBLIC_API) }
            source(fixableFile, "legacy", imports = listOf("test.model.ExternalModel"))
            dependencies(implementation(":model"))
            modelSubproject()
        }

        val updateResult = project.build(EXEMPT_TASK)
        updateResult.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        updateResult.assertOutputHas("backwards-compatibility exemption(s) and 2 placeholder KDoc(s)")

        val fixedText = project.mainSource("legacy").readText()
        val reason = "reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY"
        assertContains(fixedText, "@file:IntentionallyDefaultFacadeName($reason)")
        assertEquals(0, fixedText.indexOf("@file:IntentionallyDefaultFacadeName"))
        assertContains(fixedText, "$PLACEHOLDER_KDOC\n@IntentionallyOpen($reason)\npublic open class UndocumentedOpenClass {")
        assertContains(fixedText, "    $PLACEHOLDER_KDOC\n    public fun undocumentedMember()")
        assertFalse(fixedText.contains("@IntentionallyUndocumented"))
        assertContains(fixedText, "@IntentionallyOpen($reason)\npublic open class UnprotectedOpenClass")
        assertContains(fixedText, "@IntentionallyOpen($reason)\npublic open class UnmarkedOptIn")
        assertFalse(fixedText.contains("@SubclassOptInRequired"))
        assertContains(fixedText, "@IntentionallyExhaustive($reason)\npublic enum class UnmarkedEnum")
        assertContains(fixedText, "@IntentionallyFunctionTypeAlias($reason)\npublic typealias UnacknowledgedCallback")
        assertContains(fixedText, "@IntentionallyDataClass($reason)\npublic data class UnmarkedData")
        assertContains(fixedText, "@IntentionallyWithoutEqualsHashCodeOrToString($reason)\npublic class UnrenderedSession")
        assertFalse(fixedText.contains("@IntentionallyWithoutEquals("))
        assertFalse(fixedText.contains("@IntentionallyWithoutHashCode("))
        assertFalse(fixedText.contains("@IntentionallyWithoutToString("))
        assertContains(fixedText, "@IntentionallyMutableCollection($reason)\npublic fun leakState()")
        assertContains(fixedText, "@IntentionallyPairOrTriple($reason)\npublic fun locateOrigin()")
        assertContains(fixedText, "@IntentionallyBooleanParameter($reason)\npublic fun toggleWork(enabled: Boolean)")
        assertContains(fixedText, "@IntentionallyBooleanParameter($reason)\ncontext(enabled: Boolean)\npublic fun toggleContextWork()")
        assertContains(fixedText, "@IntentionallyNullableBoolean($reason)\npublic fun lastKnownState()")
        assertContains(fixedText, "@IntentionallyRequiredParameterAfterOptional($reason)\n@IntentionallyWithoutJvmOverloads($reason)\npublic fun retryWork")
        assertContains(fixedText, "@IntentionallyInconsistentParameterOrder($reason)\npublic fun drawShape(y: Int, x: Int, scale: Double)")
        assertContains(fixedText, "@IntentionallyInlinedLogic($reason)\n@Suppress(\"NOTHING_TO_INLINE\")\npublic inline fun squared")
        assertContains(fixedText, "@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility\n@DslMarker\n@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)\npublic annotation class NoopTargetDsl")
        assertContains(fixedText, "@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility\n@DslMarker\npublic annotation class TargetlessDsl")
        assertContains(fixedText, "@IntentionallyMangledJvmName($reason)\npublic fun findUser")
        assertContains(fixedText, "@IntentionallyNonStaticCompanionApi($reason)\n        public fun instance()")
        assertContains(fixedText, "@IntentionallyNonStaticCompanionApi($reason)\n        public val DEFAULT_LABEL")
        assertContains(fixedText, "@IntentionallyKotlinOnlyApi($reason)\npublic suspend fun refreshState()")
        assertContains(fixedText, "@IntentionallyWithoutJvmOverloads($reason)\npublic fun openPort")
        assertContains(fixedText, "@IntentionallyNonTransitiveDependency($reason)\npublic fun loadExternalModel()")

        // The report lists the exemptions the task has just applied.
        val report = project.report().readText()
        assertContains(report, "Applied annotations")
        assertReportGroup(report, "IntentionallyDataClass", 1)
        assertReportGroup(report, "IntentionallyOpen", 3)
        assertContains(report, "legacy.kt:")
        assertContains(report, "No diagnostics without an exemption.")
        assertContains(report, "class=\"language-kotlin\"")
        assertContains(report, "public open class")

        project.build("build").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `second run finds nothing left to exempt`() {
        val project = jvmProject {
            buildScript(CONTEXT_PARAMETERS_BUILD_SCRIPT)
            apiWatchdog { severity(ERROR, UNDOCUMENTED_PUBLIC_API) }
            source(fixableFile, "legacy", imports = listOf("test.model.ExternalModel"))
            dependencies(implementation(":model"))
            modelSubproject()
        }

        project.build(EXEMPT_TASK)
        val fixedOnce = project.mainSource("legacy").readText()

        project.build(EXEMPT_TASK).assertOutputHas("No watchdog errors to exempt.")
        assertEquals(fixedOnce, project.mainSource("legacy").readText())
    }

    @Test
    fun `report includes exemptions that already existed`() {
        val project = jvmProject {
            source(
                """
                    /** An existing deliberately open API. */
                    @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
                    public open class ExistingApi

                    @IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)
                    public fun undocumentedApi(): Unit = Unit

                    /** An intentionally opaque identity object. */
                    @kotlinx.library.watchdog.IntentionallyWithoutEqualsHashCodeOrToString(
                        reason = ExemptionReason.API_DESIGN,
                    )
                    public class OpaqueApi(public val value: Int)
                """,
                "existingApi",
            )
        }
        val sourceFile = project.mainSource("existingApi")
        val original = sourceFile.readText()

        project.build(REPORT_TASK).assertTaskOutcome(":$REPORT_TASK", SUCCESS)

        assertEquals(original, sourceFile.readText())
        val report = project.report().readText()
        assertContains(report, "Applied annotations")
        assertReportGroup(report, "IntentionallyOpen", 1)
        assertReportGroup(report, "IntentionallyUndocumented", 1)
        assertReportGroup(report, "IntentionallyWithoutEqualsHashCodeOrToString", 1)
        assertContains(report, "existingApi.kt:")
    }

    @Test
    fun `report shows applicable annotations and leaves sources unchanged`() {
        val project = jvmProject {
            source(
                """
                    /** A legacy open API. */
                    @Suppress(
                        "unused",
                        "DEPRECATION",
                        "UNCHECKED_CAST",
                        "NOTHING_TO_INLINE",
                    )
                    public open class LegacyApi
                """.trimIndent(),
                "legacyApi",
            )
        }
        val sourceFile = project.mainSource("legacyApi")
        val original = sourceFile.readText()

        project.build(REPORT_TASK).assertTaskOutcome(":$REPORT_TASK", SUCCESS)

        assertEquals(original, sourceFile.readText())
        val report = project.report().readText()
        assertContains(report, "Not applied")
        assertReportGroup(report, "IntentionallyOpen", 1, Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        assertContains(report, "class=\"fix-status not-fixed\">Wasn't auto-fixed</span>")
        assertFalse(report.contains("<p class=\"reason\">"))
        assertContains(report, "<code class=\"language-kotlin\">")
        assertContains(report, "public open class LegacyApi")
        assertContains(report, "data-line=\"")
        assertContains(report, "Users can subclass <code>LegacyApi</code> outside the library.")
        assertContains(report, "Apply <code>@SubclassOptInRequired</code>")
        assertContains(report, "<details class=\"diagnostic-message\"><summary>Diagnostic message</summary>")
        assertReportedElement(report, "LegacyApi", "error")
    }

    @Test
    fun `report shows the placeholder KDoc and leaves sources unchanged`() {
        val project = jvmProject {
            apiWatchdog { severity(ERROR, UNDOCUMENTED_PUBLIC_API) }
            source("public class Undocumented", "undocumented")
        }
        val sourceFile = project.mainSource("undocumented")
        val original = sourceFile.readText()

        project.build(REPORT_TASK).assertTaskOutcome(":$REPORT_TASK", SUCCESS)

        assertEquals(original, sourceFile.readText())
        val report = project.report().readText()
        assertReportGroup(report, "IntentionallyUndocumented", 1, Diagnostic.UNDOCUMENTED_PUBLIC_API)
        assertContains(report, "class=\"fix-status not-fixed\">Wasn't auto-fixed</span>")
        assertFalse(report.contains("No automatic annotation"))
        assertContains(report, "UNDOCUMENTED_PUBLIC_API")
        assertContains(report, "exemptExistingApi can add a placeholder KDoc.")
        assertContains(report, "The public class <code>Undocumented</code> doesn't have <code>KDoc</code>.")
    }

    @Test
    fun `SARIF report lists the diagnostics with root-relative locations`() {
        val project = jvmProject {
            subproject("lib") {
                apiWatchdog { severity(WARNING, UNDOCUMENTED_PUBLIC_API) }
                source("public open class LegacyApi", "legacyApi")
            }
        }

        project.build(":lib:$REPORT_TASK").assertTaskOutcome(":lib:$REPORT_TASK", SUCCESS)

        val sarif = project.file("lib/build/reports/api-watchdog/report.sarif").readText()
        assertContains(sarif, "\"version\": \"2.1.0\"")
        assertContains(sarif, "\"id\": \"OPEN_API_WITHOUT_SUBCLASS_OPT_IN\"")
        assertContains(sarif, "\"helpUri\": \"${OPEN_API_WITHOUT_SUBCLASS_OPT_IN.docsUrl}\"")
        assertContains(sarif, "\"ruleId\": \"UNDOCUMENTED_PUBLIC_API\"")
        assertContains(sarif, "\"level\": \"error\"")
        assertContains(sarif, "\"level\": \"warning\"")
        assertContains(sarif, "\"uri\": \"lib/src/main/kotlin/test/legacyApi.kt\"")
        assertContains(sarif, "\"startLine\": ")
        assertContains(sarif, "Open API without subclass opt-in. Exemption: @IntentionallyOpen.\"")
    }

    @Test
    fun `reports block selects the formats and the files`() {
        val project = jvmProject {
            apiWatchdog { reports { html = false; sarifFile = "scan/watchdog.sarif" } }
            source("/** A legacy open API. */\npublic open class LegacyApi", "legacyApi")
        }

        val result = project.build(REPORT_TASK)

        result.assertTaskOutcome(":$REPORT_TASK", SUCCESS)
        assertFalse(project.report().exists(), "HTML report is off")
        assertFalse(project.file("build/reports/api-watchdog/report.sarif").exists(), "SARIF report moved")
        assertContains(project.file("build/scan/watchdog.sarif").readText(), "OPEN_API_WITHOUT_SUBCLASS_OPT_IN")
        result.assertOutputLacks("API Watchdog report: ")
        result.assertOutputHas("API Watchdog SARIF report: ")

        val sarifOff = jvmProject {
            apiWatchdog { reports { sarif = false } }
            source("/** A legacy open API. */\npublic open class LegacyApi", "legacyApi")
        }

        sarifOff.build(EXEMPT_TASK).assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)

        assertFalse(sarifOff.file("build/reports/api-watchdog/report.sarif").exists(), "SARIF report is off")
        assertReportGroup(sarifOff.report().readText(), "IntentionallyOpen", 1)
    }

    @Test
    fun `disabled diagnostics aren't exempted`() {
        val project = jvmProject {
            apiWatchdog { severity(NONE, UNDOCUMENTED_PUBLIC_API) }
            source("public open class UndocumentedOpen", "legacy")
        }

        project.build(EXEMPT_TASK)

        val fixedText = project.mainSource("legacy").readText()
        assertFalse(fixedText.contains("@IntentionallyUndocumented"))
        assertFalse(fixedText.contains(PLACEHOLDER_KDOC))
        assertContains(fixedText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
    }

    @Test
    fun `warnings stay and the report lists them`() {
        val project = jvmProject {
            apiWatchdog { severity(WARNING, OPEN_API_WITHOUT_SUBCLASS_OPT_IN) }
            source("/** A warning-only class. */\npublic open class WarningOnly", "warningOnly")
        }
        val sourceFile = project.mainSource("warningOnly")
        val original = sourceFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertOutputHas("No watchdog errors to exempt.")
        result.assertOutputHas("1 warning(s) stay, because the task exempts errors only.")
        result.assertOutputLacks("needs manual attention")
        assertEquals(original, sourceFile.readText())
        val report = project.report().readText()
        assertContains(report, "Not applied")
        assertReportGroup(report, "IntentionallyOpen", 1, Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        assertContains(report, "class=\"fix-status not-fixed\">Wasn't auto-fixed</span>")
        assertContains(report, "exemptExistingApi exempts errors only.")
        assertReportedElement(report, "WarningOnly", "warning")
    }

    @Test
    fun `the default undocumented warning gets no placeholder KDoc`() {
        val project = jvmProject { source("public open class UndocumentedOpen", "legacy") }

        project.build(EXEMPT_TASK)

        val fixedText = project.mainSource("legacy").readText()
        assertFalse(fixedText.contains(PLACEHOLDER_KDOC))
        assertContains(fixedText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
        assertContains(project.report().readText(), "exemptExistingApi exempts errors only.")
        assertContains(project.report().readText(), "class=\"fix-status not-fixed\">Wasn't auto-fixed</span>")
    }

    @Test
    fun `works without explicit API mode`() {
        // The task adds `-Xexplicit-api=warning` itself.
        val project = jvmProject {
            explicitApi = false
            source("/** An open class. */\npublic open class Open", "open")
        }

        project.build(EXEMPT_TASK)

        assertContains(
            project.mainSource("open").readText(),
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class Open",
        )
    }

    @Test
    fun `allWarningsAsErrors doesn't fail the exempt task`() {
        val project = jvmProject {
            buildScript(ALL_WARNINGS_AS_ERRORS_BUILD_SCRIPT)
            source(openClass("ExistingApi") + " {\n    public fun undocumented(): Int = 0\n}", "existingApi")
        }

        project.build(EXEMPT_TASK)

        project.assertExempted("src/main/kotlin/test/existingApi.kt", "ExistingApi")
        // The undocumented function is still a warning, and the flag is on outside the task.
        project.buildAndFail("compileKotlin").assertOutputHas("-Werror")
    }

    @Test
    fun `allWarningsAsErrors doesn't fail the report task in a multiplatform project`() {
        val project = kmpProject("jvm()\njs { nodejs() }") {
            buildScript(ALL_WARNINGS_AS_ERRORS_BUILD_SCRIPT)
            source(openClass("SharedApi"), "sharedApi")
        }

        project.build(REPORT_TASK)

        assertContains(project.report().readText(), "sharedApi.kt:")
    }

    @Test
    fun `keeps the configured explicit API mode`() {
        val project = kmpProject { source(openClass("SharedApi"), "sharedApi") }

        val result = project.build(EXEMPT_TASK)

        result.assertOutputLacks("is passed multiple times")
        project.assertExempted("src/commonMain/kotlin/test/sharedApi.kt", "SharedApi")
    }

    @Test
    fun `missing imports are added`() {
        val project = jvmProject {
            source("/** A bare open class. */\npublic open class Bare", "bare", defaultImports = false)
        }

        project.build(EXEMPT_TASK)

        val fixedText = project.mainSource("bare").readText()
        assertContains(fixedText, "import kotlinx.library.watchdog.ExemptionReason")
        assertContains(fixedText, "import kotlinx.library.watchdog.IntentionallyOpen")
        assertContains(fixedText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")

        project.build("build").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `CRLF line endings and a byte order mark stay and don't move the exemptions`() = crlfAndByteOrderMarkCase()

    @Test
    fun `CRLF line endings and a byte order mark stay and don't move the exemptions with the PSI parser`() =
        crlfAndByteOrderMarkCase("""kotlin { compilerOptions.freeCompilerArgs.add("-Xuse-fir-lt=false") }""")

    private fun crlfAndByteOrderMarkCase(script: String? = null) {
        val a = "/** A class. */\npublic class A {\n    /** A function. */\n    public fun c() {}\n}\n\n"
        val b = "public data class B(val x: Int)\n\n"
        val topLevel = "/** A top-level function. */\npublic fun topLevel(): Int = 0\n"
        val text = "package test\n\n$a/** A data class. */\n$b$topLevel"
        val project = jvmProject {
            script?.let(::buildScript)
            kotlinFile("crlf", text = text)
        }
        val file = project.mainSource("crlf")
        val byteOrderMark = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        file.writeBytes(byteOrderMark + text.replace("\n", "\r\n").toByteArray())

        project.build(EXEMPT_TASK).assertOutputHas("Added 2 backwards-compatibility exemption(s) in 1 file(s).")

        val reason = "reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY"
        assertEquals(
            "\uFEFF@file:IntentionallyDefaultFacadeName($reason)\n\n" +
                    "package test\n\n" +
                    "import kotlinx.library.watchdog.ExemptionReason\n" +
                    "import kotlinx.library.watchdog.IntentionallyDataClass\n" +
                    "import kotlinx.library.watchdog.IntentionallyDefaultFacadeName\n\n" +
                    "$a/** A data class. */\n@IntentionallyDataClass($reason)\n$b$topLevel",
            file.readText().replace("\r\n", "\n"),
        )
        assertFalse(Regex("(?<!\r)\n").containsMatchIn(file.readText()))

        project.build("build").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `generated sources in the build directory aren't edited`() {
        val generated = "package test\n\n/** A generated open API. */\npublic open class GeneratedApi\n"
        val project = jvmProject {
            buildScript(
                """
                    val generateApi = tasks.register("generateApi") {
                        val output = layout.buildDirectory.dir("generated/api")
                        outputs.dir(output)
                        doLast { output.get().file("generated.kt").asFile.writeText("${generated.replace("\n", "\\n")}") }
                    }
                    kotlin.sourceSets.getByName("main").kotlin.srcDir(generateApi)
                """
            )
            source(openClass("MainApi"), "mainApi")
        }

        val result = project.build(EXEMPT_TASK)

        project.assertExempted("src/main/kotlin/test/mainApi.kt", "MainApi")
        assertEquals(generated, project.file("build/generated/api/generated.kt").readText())
        result.assertOutputHas("1 backwards-compatibility exemption(s) in 1 file(s). 1 diagnostic(s) need manual attention.")
        result.assertOutputHas("needs manual attention: `exemptExistingApi` doesn't edit a file in the build directory.")
    }

    @Test
    fun `unfixable diagnostics are reported for manual attention`() {
        val project = jvmProject { source(unfixableFile, "manual") }

        project.build(REPORT_TASK).assertTaskOutcome(":$REPORT_TASK", SUCCESS)
        val report = project.report().readText()
        assertContains(report, "Not applied")
        assertReportGroup(report, null, 1, Diagnostic.DSL_MARKER_NOOP_TYPE_POSITION)
        assertContains(report, "class=\"fix-status unfixable\">Can't be auto-fixed</span>")
        assertFalse(report.contains("No automatic annotation"))

        val result = project.build(EXEMPT_TASK)

        result.assertOutputHas("needs manual attention")
        result.assertOutputHas("DSL_MARKER_NOOP_TYPE_POSITION")
    }

    @Test
    fun `broken compilation fails before the fixer and doesn't touch sources`() {
        val project = jvmProject {
            source("internal fun broken() { thisCallDoesNotResolve() }", "broken")
        }
        val original = project.mainSource("broken").readText()

        val result = project.buildAndFail(EXEMPT_TASK)

        result.assertOutputHas("thisCallDoesNotResolve")
        result.assertTaskOutcome(":$EXEMPT_TASK", null)
        assertEquals(original, project.mainSource("broken").readText())
    }

    @Test
    fun `unfixable always-error diagnostic is disabled only during the update`() {
        val project = jvmProject {
            source(alwaysErrorDiagnosticsFile, "alwaysErrors", "test.consumer")
        }
        val sourceFile = project.file("src/main/kotlin/test/consumer/alwaysErrors.kt")
        val original = sourceFile.readText()

        val updateResult = project.build(EXEMPT_TASK)

        updateResult.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        updateResult.assertOutputHas("No watchdog errors to exempt.")
        assertEquals(original, sourceFile.readText())

        project.buildAndFail("compileKotlin").assertErrors(EXEMPTION_WITHOUT_EXPLANATION)
    }

    @Test
    fun `internal API exposure is exempted for backwards compatibility`() {
        val project = jvmProject {
            apiWatchdog { severity(NONE, UNDOCUMENTED_PUBLIC_API) }
            source(internalApiExposureFile, "exposure", "test.consumer")
        }

        val updateResult = project.build(EXEMPT_TASK)

        updateResult.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        updateResult.assertOutputHas("backwards-compatibility exemption(s)")
        val fixedText = project.file("src/main/kotlin/test/consumer/exposure.kt").readText()
        val annotation = "@IntentionallyExposedInternalApiForBackwardsCompatibility"
        assertContains(fixedText, "$annotation\n    public fun loadModel(): InternalModel")
        assertContains(fixedText, "$annotation\n    public fun useModels(first: InternalModel, second: InternalModel)")
        assertContains(fixedText, "public class Holder($annotation public val model: InternalModel)")
        assertContains(fixedText, "$annotation\npublic class Special : InternalModel()")
        assertFalse(fixedText.contains("$annotation("))
        assertFalse(fixedText.contains("@IntentionallyExposedInternalApiForBackwardsCompatibility\n    public fun supported()"))

        val report = project.report().readText()
        assertReportGroup(report, "IntentionallyExposedInternalApiForBackwardsCompatibility", 4)

        project.build("build").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `redundant internal API exposure exemption is removed`() {
        val project = jvmProject {
            apiWatchdog { severity(NONE, UNDOCUMENTED_PUBLIC_API) }
            source(redundantInternalApiExemptionFile, "redundant", "test.consumer")
        }

        project.build(EXEMPT_TASK).assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)

        val fixedText = project.file("src/main/kotlin/test/consumer/redundant.kt").readText()
        assertContains(fixedText, "@IntentionallyExposedInternalApiForBackwardsCompatibility\npublic fun needed(): InternalModel")
        assertContains(fixedText, "/** Returns a supported type. */\npublic fun supported(): String")
        project.build("build").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `non-transitive dependency exposure is exempted for backwards compatibility`() {
        val project = jvmProject {
            consumerSource()
            source(redundantNonTransitiveExemptionFile, "count", "test.consumer")
            dependencies(implementation(":model"))
            modelSubproject()
        }
        val sourceFile = project.file("src/main/kotlin/test/consumer/Consumer.kt")
        val original = sourceFile.readText()

        project.build(REPORT_TASK).assertTaskOutcome(":$REPORT_TASK", SUCCESS)
        assertEquals(original, sourceFile.readText())
        // The report lists the diagnostics of the type alias and of the class, and the class exemption covers the members.
        assertReportGroup(project.report().readText(), "IntentionallyNonTransitiveDependency", 2, PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)

        project.build(EXEMPT_TASK).assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        val fixedText = sourceFile.readText()
        val annotation = "@IntentionallyNonTransitiveDependency(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)"
        assertContains(fixedText, "$annotation\npublic typealias ExternalModels")
        // The class exemption covers the members.
        assertContains(fixedText, "$annotation\n@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)\npublic open class Consumer")
        assertEquals(2, fixedText.split(annotation).size - 1, fixedText)
        assertFalse("@IntentionallyNonTransitiveDependency" in project.file("src/main/kotlin/test/consumer/count.kt").readText())

        project.build("build").assertNoWatchdogDiagnostics()
        project.build(EXEMPT_TASK).assertOutputHas("No watchdog errors to exempt.")
        assertEquals(fixedText, sourceFile.readText())
    }

    @Test
    fun `non-transitive dependency exposure is exempted in a multiplatform project`() {
        val project = kmpProject {
            source(multiplatformDependencyExposureFile, "loaders", "test.consumer")
            source(
                "public actual fun loadPlatformModel(): ExternalModel = ExternalModel()",
                "platformLoaders",
                "test.consumer",
                sourceSet = "jvmMain",
                imports = listOf("test.model.ExternalModel"),
            )
            dependencies(implementation(":model"))
            modelSubproject()
        }
        val common = project.file("src/commonMain/kotlin/test/consumer/loaders.kt")
        val actual = project.file("src/jvmMain/kotlin/test/consumer/platformLoaders.kt")

        project.build(REPORT_TASK).assertTaskOutcome(":$REPORT_TASK", SUCCESS)
        assertReportGroup(project.report().readText(), "IntentionallyNonTransitiveDependency", 3, PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)

        project.build(EXEMPT_TASK).assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        val annotation = "@IntentionallyNonTransitiveDependency(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)"
        val fixedCommon = common.readText()
        assertContains(fixedCommon, "$annotation\npublic fun loadModel()")
        assertContains(fixedCommon, "$annotation\npublic expect fun loadPlatformModel()")
        val fixedActual = actual.readText()
        assertContains(fixedActual, "$annotation\npublic actual fun loadPlatformModel()")

        val compilation = project.build("compileKotlinJvm")
        compilation.assertNoWatchdogDiagnostics()
        assertFalse("is missing on actual declaration" in compilation.output, compilation.output)

        project.build(EXEMPT_TASK).assertOutputHas("No watchdog errors to exempt.")
        assertEquals(fixedCommon, common.readText())
        assertEquals(fixedActual, actual.readText())
    }

    @Test
    fun `non-transitive dependency exemptions of a source set that several targets share stay as they are`() {
        val project = kmpProject("jvm()\njs { nodejs() }") {
            apiWatchdog { severity(NONE, TOP_LEVEL_API_WITHOUT_JVM_NAME) }
            sharedDependencyExposureSources("jsMain")
            dependencies(implementation(":model"))
            modelSubproject()
        }

        project.assertSharedDependencyExemptionsStay("jsMain", jvmExempted = true)
            .assertTaskOutcome(":compileCommonMainKotlinMetadata", SUCCESS)

        // A regular build still reports the exposure in `commonMain` without an exemption.
        val regular = project.buildAndFail("compileKotlinJvm", "compileKotlinJs", "--continue")
        regular.assertReportedIn(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, "shared.kt", "shared.kt")
        regular.assertOutputLacks("is missing on actual declaration")
    }

    @Test
    fun `non-transitive dependency exemptions of a source set that JVM and Android targets share stay as they are`() {
        val target = KmpAndroidTarget.LIBRARY_AGP9
        val project = kmpAndroidProject(target) {
            apiWatchdog { severity(NONE, TOP_LEVEL_API_WITHOUT_JVM_NAME) }
            sharedDependencyExposureSources("androidMain")
            dependencies(implementation(":model"))
            dependencies("androidMain", api(":model"))
            modelSubproject()
        }

        project.assertSharedDependencyExemptionsStay("androidMain", jvmExempted = true)

        val regular = project.buildAndFail("compileKotlinJvm", target.mainCompileTask, "--continue")
        regular.assertReportedIn(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, "shared.kt")
        regular.assertOutputLacks("is missing on actual declaration")
    }

    @Test
    fun `non-transitive dependency exemptions of a shared source set stay as they are without the metadata compilation`() {
        val project = kmpProject("jvm()\njs { nodejs() }") {
            apiWatchdog { severity(NONE, TOP_LEVEL_API_WITHOUT_JVM_NAME) }
            sharedDependencyExposureSources("jsMain")
            dependencies(implementation(":model"))
            dependencies("jsMain", api(":model"))
            modelSubproject()
        }

        project.assertSharedDependencyExemptionsStay("jsMain", jvmExempted = true, "-x", "compileCommonMainKotlinMetadata")

        val regular = project.buildAndFail("compileKotlinJvm", "compileKotlinJs", "--continue")
        regular.assertReportedIn(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, "shared.kt")
        regular.assertOutputLacks("is missing on actual declaration")
    }

    @Test
    fun `non-transitive dependency exemptions of a shared source set stay as they are when every target declares the dependency with api`() {
        val project = kmpProject("jvm()\njs { nodejs() }") {
            apiWatchdog { severity(NONE, TOP_LEVEL_API_WITHOUT_JVM_NAME) }
            sharedDependencyExposureSources("jsMain")
            dependencies(implementation(":model"))
            dependencies("jvmMain", api(":model"))
            dependencies("jsMain", api(":model"))
            modelSubproject()
        }

        project.assertSharedDependencyExemptionsStay("jsMain", jvmExempted = false, "-x", "compileCommonMainKotlinMetadata")

        project.build("compileKotlinJvm", "compileKotlinJs").assertOutputLacks("is missing on actual declaration")
    }

    @Test
    fun `the copies of a shared non-transitive dependency exemption stay when the targets declare the dependency differently`() {
        val project = kmpProject("jvm()\njs { nodejs() }") {
            apiWatchdog { severity(NONE, TOP_LEVEL_API_WITHOUT_JVM_NAME) }
            source(
                "import test.model.ExternalModel\n\n/** Loads a model. */\n$HOST_MODEL_EXEMPTION\npublic expect fun loadModel(): ExternalModel",
                "loaders",
                "test.consumer",
            )
            for (sourceSet in listOf("jvmMain", "jsMain")) {
                source(
                    "$HOST_MODEL_EXEMPTION\npublic actual fun loadModel(): ExternalModel = ExternalModel()",
                    "platformLoaders",
                    "test.consumer",
                    sourceSet = sourceSet,
                    imports = listOf("test.model.ExternalModel"),
                )
            }
            dependencies(implementation(":model"))
            dependencies("jvmMain", api(":model"))
            modelSubproject()
        }
        val files = listOf("commonMain/kotlin/test/consumer/loaders.kt", "jvmMain/kotlin/test/consumer/platformLoaders.kt", "jsMain/kotlin/test/consumer/platformLoaders.kt")
            .map { project.file("src/$it") }
        val originals = files.map { it.readText() }

        project.build(EXEMPT_TASK).assertOutputLacks("Removed the redundant")

        assertEquals(originals, files.map { it.readText() })
        project.build("compileKotlinJvm", "compileKotlinJs").assertOutputLacks("is missing on actual declaration")
    }

    @Test
    fun `an expect class keeps the non-transitive dependency exemption that a member of its actual class needs`() {
        val project = kmpProject {
            apiWatchdog { severity(NONE, TOP_LEVEL_API_WITHOUT_JVM_NAME) }
            source(
                """
                    /** Holds models. */
                    $HOST_MODEL_EXEMPTION
                    public expect class Holder() {
                        /** Counts the models. */
                        public fun count(): Int
                    }
                """.trimIndent(),
                "holder",
                "test.consumer",
            )
            source(
                """
                    $HOST_MODEL_EXEMPTION
                    public actual class Holder actual constructor() {
                        public actual fun count(): Int = 1

                        /** Loads a model. */
                        public fun load(): ExternalModel = ExternalModel()
                    }
                """.trimIndent(),
                "holderJvm",
                "test.consumer",
                sourceSet = "jvmMain",
                imports = listOf("test.model.ExternalModel"),
            )
            dependencies(implementation(":model"))
            modelSubproject()
        }
        val files = listOf("commonMain/kotlin/test/consumer/holder.kt", "jvmMain/kotlin/test/consumer/holderJvm.kt")
            .map { project.file("src/$it") }
        val originals = files.map { it.readText() }

        project.build(EXEMPT_TASK).assertOutputLacks("Removed the redundant")

        assertEquals(originals, files.map { it.readText() })
        val compilation = project.build("compileKotlinJvm")
        compilation.assertNoWatchdogDiagnostics()
        compilation.assertOutputLacks("is missing on actual declaration")
    }

    @Test
    fun `a class exemption replaces the non-transitive dependency exemptions of its members`() {
        val project = jvmProject {
            source(holderWithMemberExemptionsFile, "Holder", "test.consumer", imports = listOf("test.model.ExternalContract", "test.model.ExternalModel"))
            dependencies(implementation(":model"))
            modelSubproject()
        }
        val sourceFile = project.file("src/main/kotlin/test/consumer/Holder.kt")

        // The member exemptions are redundant only after the class exemption is in the source.
        project.build(REPORT_TASK)
        assertFalse("fix-status redundant" in project.report().readText())

        val result = project.build(EXEMPT_TASK)

        result.assertOutputHas("Added 1 backwards-compatibility exemption(s), removed 3 redundant exemption(s), in 1 file(s).")
        assertEquals(1, result.output.lines().count { "@IntentionallyNonTransitiveDependency exempts" in it }, result.output)
        val fixedText = sourceFile.readText()
        assertContains(fixedText, "$BACKWARDS_COMPATIBLE_DEPENDENCY_EXEMPTION\npublic class Holder : ExternalContract {")
        assertFalse("ExemptionReason.INTEROP" in fixedText, fixedText)
        project.build("build").assertNoWatchdogDiagnostics()

        project.build(EXEMPT_TASK).assertOutputHas("No watchdog errors to exempt.")
        assertEquals(fixedText, sourceFile.readText())
    }

    @Test
    fun `an actual class exemption keeps the copies of the new non-transitive dependency exemptions of its expect class`() =
        expectAndActualClassExemptionsCase(expectAnnotated = false, actualAnnotated = true)

    @Test
    fun `an expect class exemption replaces the non-transitive dependency exemptions inside both classes`() =
        expectAndActualClassExemptionsCase(expectAnnotated = true, actualAnnotated = false)

    @Test
    fun `the exemptions of an expect class and its actual class replace the non-transitive dependency exemptions inside them`() =
        expectAndActualClassExemptionsCase(expectAnnotated = true, actualAnnotated = true)

    /**
     * An `expect` class and its `actual` class with each kind of member exemption. The run annotates the `expect` class
     * for its type parameter bound when [expectAnnotated], and the `actual` class for its own supertype when
     * [actualAnnotated].
     */
    private fun expectAndActualClassExemptionsCase(expectAnnotated: Boolean, actualAnnotated: Boolean) {
        val typeParameter = if (expectAnnotated) "<T : ExternalModel>" else ""
        val actualSupertype = if (actualAnnotated) " : ExternalContract" else ""
        val project = kmpProject {
            source(
                """
                    import test.model.ExternalContract
                    import test.model.ExternalModel

                    /** Holds models. */
                    public expect class Holder$typeParameter(model: ExternalModel) {
                        /** Loads a model. */
                        $HOST_MODEL_EXEMPTION
                        public fun load(): ExternalModel

                        /** Reads a model. */
                        public fun read(): ExternalModel

                        /** Writes a model. */
                        public fun write(): ExternalModel

                        /** Reads models in a nested class. */
                        public class Inner : ExternalContract
                    }
                """.trimIndent(),
                "holder",
                "test.consumer",
            )
            source(
                """
                    public actual class Holder$typeParameter actual constructor(model: ExternalModel)$actualSupertype {
                        $HOST_MODEL_EXEMPTION
                        public actual fun load(): ExternalModel = ExternalModel()

                        public actual fun read(): ExternalModel = ExternalModel()

                        $HOST_MODEL_EXEMPTION
                        public actual fun write(): ExternalModel = ExternalModel()

                        /** Loads an extra model. */
                        $HOST_MODEL_EXEMPTION
                        public fun extra(): ExternalModel = ExternalModel()

                        /** Loads another model. */
                        public fun other(): ExternalModel = ExternalModel()

                        public actual class Inner : ExternalContract
                    }
                """.trimIndent(),
                "holderJvm",
                "test.consumer",
                sourceSet = "jvmMain",
                imports = listOf("test.model.ExternalContract", "test.model.ExternalModel"),
            )
            dependencies(implementation(":model"))
            modelSubproject()
        }
        val files = listOf("commonMain/kotlin/test/consumer/holder.kt", "jvmMain/kotlin/test/consumer/holderJvm.kt")
            .map { project.file("src/$it") }

        project.build(EXEMPT_TASK)

        val (common, actual) = files.map { it.readText() }
        val annotation = BACKWARDS_COMPATIBLE_DEPENDENCY_EXEMPTION
        fun String.count(text: String) = split(text).size - 1
        if (expectAnnotated) {
            // The class exemptions cover every declaration inside the classes.
            assertContains(common, "$annotation\npublic expect class Holder<T : ExternalModel>(model: ExternalModel) {")
            assertContains(actual, "$annotation\npublic actual class Holder<T : ExternalModel> actual constructor(model: ExternalModel)$actualSupertype {")
            assertEquals(listOf(1, 1), listOf(common.count(annotation), actual.count(annotation)), common + actual)
            assertEquals(listOf(0, 0), listOf(common.count(HOST_MODEL_EXEMPTION), actual.count(HOST_MODEL_EXEMPTION)), common + actual)
        } else {
            // The `actual` declarations get the copies of the new `expect` exemptions, and only the existing copy stays.
            assertContains(common, "public expect class Holder $annotation constructor(model: ExternalModel) {")
            assertContains(actual, "$annotation\npublic actual class Holder $annotation actual constructor(model: ExternalModel) : ExternalContract {")
            for (declaration in listOf("fun read()", "fun write()", "class Inner")) {
                assertContains(common, "    $annotation\n    public $declaration")
                assertContains(actual, "    $annotation\n    public actual $declaration")
            }
            assertContains(common, "    $HOST_MODEL_EXEMPTION\n    public fun load()")
            assertContains(actual, "    $HOST_MODEL_EXEMPTION\n    public actual fun load()")
            assertEquals(1, actual.count(HOST_MODEL_EXEMPTION), actual)
            assertContains(actual, "/** Loads an extra model. */\n    public fun extra()")
            assertContains(actual, "/** Loads another model. */\n    public fun other()")
        }
        val compilation = project.build("compileKotlinJvm")
        compilation.assertNoWatchdogDiagnostics()
        compilation.assertOutputLacks("is missing on actual declaration")

        project.build(EXEMPT_TASK).assertOutputHas("No watchdog errors to exempt.")
        assertEquals(listOf(common, actual), files.map { it.readText() })
    }

    @Test
    fun `a class exemption replaces the non-transitive dependency exemptions in an actual class that aren't copies`() {
        val project = kmpProject {
            source(
                """
                    import test.model.ExternalModel

                    /** Holds models. */
                    public expect class Outer() {
                        /** Reads models. */
                        public class Inner() {
                            /** Reads a model. */
                            $HOST_MODEL_EXEMPTION
                            public fun read(): ExternalModel
                        }
                    }
                """.trimIndent(),
                "outer",
                "test.consumer",
            )
            source(
                """
                    public actual class Outer actual constructor() : ExternalContract {
                        public actual class Inner actual constructor() : $HOST_MODEL_EXEMPTION ExternalContract {
                            $HOST_MODEL_EXEMPTION
                            public actual fun read(): ExternalModel = ExternalModel()

                            /** Loads a model. */
                            $HOST_MODEL_EXEMPTION
                            public fun load(): ExternalModel = ExternalModel()
                        }
                    }
                """.trimIndent(),
                "outerJvm",
                "test.consumer",
                sourceSet = "jvmMain",
                imports = listOf("test.model.ExternalContract", "test.model.ExternalModel"),
            )
            dependencies(implementation(":model"))
            modelSubproject()
        }
        val files = listOf("commonMain/kotlin/test/consumer/outer.kt", "jvmMain/kotlin/test/consumer/outerJvm.kt")
            .map { project.file("src/$it") }
        val (common, actual) = files
        val originalCommon = common.readText()

        project.build(EXEMPT_TASK)
            .assertOutputHas("Added 1 backwards-compatibility exemption(s), removed 2 redundant exemption(s), in 1 file(s).")

        assertEquals(originalCommon, common.readText())
        val fixedActual = actual.readText()
        assertContains(fixedActual, "$BACKWARDS_COMPATIBLE_DEPENDENCY_EXEMPTION\npublic actual class Outer")
        assertContains(fixedActual, "public actual class Inner actual constructor() : ExternalContract {")
        // Only the copy of the exemption of the `expect` function stays.
        assertContains(fixedActual, "    $HOST_MODEL_EXEMPTION\n        public actual fun read()")
        assertEquals(1, fixedActual.split(HOST_MODEL_EXEMPTION).size - 1, fixedActual)
        val compilation = project.build("compileKotlinJvm")
        compilation.assertNoWatchdogDiagnostics()
        compilation.assertOutputLacks("is missing on actual declaration")

        project.build(EXEMPT_TASK).assertOutputHas("No watchdog errors to exempt.")
        assertEquals(listOf(originalCommon, fixedActual), files.map { it.readText() })
    }

    @Test
    fun `non-transitive dependency exemptions of a shared native source set stay as they are`() {
        val project = kmpProject("jvm()\nlinuxX64()\nmacosArm64()") {
            apiWatchdog { severity(NONE, TOP_LEVEL_API_WITHOUT_JVM_NAME) }
            source(
                "/** Loads a model that the host ships. */\n$HOST_MODEL_EXEMPTION\npublic fun loadHostModel(): ExternalModel = ExternalModel()",
                "native",
                "test.consumer",
                sourceSet = "nativeMain",
                imports = listOf("test.model.ExternalModel"),
            )
            dependencies(implementation(":model"))
            modelSubproject()
        }
        val native = project.file("src/nativeMain/kotlin/test/consumer/native.kt")
        val original = native.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":compileNativeMainKotlinMetadata", SUCCESS)
        result.assertOutputLacks("Removed the redundant")
        assertEquals(original, native.readText())
    }

    @Test
    fun `JS-only project is fixed through its regular compilation`() {
        val project = kmpProject("js { nodejs() }") {
            source("/** A JS class. */\npublic open class JsOnly", "jsOnly")
        }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlinJs", SUCCESS)
        assertContains(
            project.file("src/commonMain/kotlin/test/jsOnly.kt").readText(),
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class JsOnly",
        )
    }

    @Test
    fun `native-only project is fixed through its regular compilation`() {
        val native = KmpTarget.NATIVE_HOST
        val project = kmpProject("${native.gradleTargetName}()") {
            source("/** A native class. */\npublic open class NativeOnly", "nativeOnly")
        }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":${native.compileTaskName}", SUCCESS)
        assertContains(
            project.file("src/commonMain/kotlin/test/nativeOnly.kt").readText(),
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class NativeOnly",
        )
    }

    @Test
    fun `multiplatform JVM compilation covers common sources`() {
        val project = kmpProject { source(multiplatformFile, "shared") }

        project.build(EXEMPT_TASK).assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)

        assertContains(
            project.file("src/commonMain/kotlin/test/shared.kt").readText(),
            "@IntentionallyDataClass(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic data class SharedData",
        )

        project.build("compileKotlinJvm").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `an exemption of an expect declaration is copied to its actual declaration`() {
        val project = kmpProject {
            source(
                """
                    /** Server credentials. */
                    public expect open class Credentials()

                    /** Configures the server. */
                    public expect fun configure(enabled: Boolean)
                """.trimIndent(),
                "credentials",
            )
            source(
                """
                    public actual open class Credentials actual constructor()

                    public actual fun configure(enabled: Boolean) {}
                """.trimIndent(),
                "credentialsJvm",
                sourceSet = "jvmMain",
            )
        }

        project.build(EXEMPT_TASK).assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)

        val common = project.file("src/commonMain/kotlin/test/credentials.kt").readText()
        val jvm = project.file("src/jvmMain/kotlin/test/credentialsJvm.kt").readText()
        assertContains(common, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic expect open class Credentials")
        assertContains(jvm, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic actual open class Credentials")
        assertContains(common, "@IntentionallyBooleanParameter(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic expect fun configure")
        assertContains(jvm, "@IntentionallyBooleanParameter(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic actual fun configure")

        val compilation = project.build("compileKotlinJvm")
        compilation.assertNoWatchdogDiagnostics()
        assertFalse("is missing on actual declaration" in compilation.output, compilation.output)
    }

    @Test
    fun `a redundant exemption of an expect declaration is removed with its copy on the actual declaration`() {
        val project = kmpProject {
            source(
                """
                    /** Configures the server. */
                    @IntentionallyBooleanParameter(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
                    public expect fun configure(name: String)

                    /** Reads the state. */
                    @IntentionallyKotlinOnlyApi(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
                    public expect fun state(): Int
                """.trimIndent(),
                "server",
            )
            source(
                """
                    @IntentionallyBooleanParameter(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
                    public actual fun configure(name: String) {}

                    @IntentionallyKotlinOnlyApi(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
                    public actual fun state(): Int = 1
                """.trimIndent(),
                "serverJvm",
                sourceSet = "jvmMain",
            )
        }

        val result = project.build(EXEMPT_TASK)
        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        assertEquals(4, result.output.lines().count { "Removed the redundant @Intentionally" in it }, result.output)

        val common = project.file("src/commonMain/kotlin/test/server.kt").readText()
        val jvm = project.file("src/jvmMain/kotlin/test/serverJvm.kt").readText()
        for (text in listOf(common, jvm)) {
            assertFalse("@IntentionallyBooleanParameter" in text, text)
            assertFalse("@IntentionallyKotlinOnlyApi" in text, text)
        }
        project.build("compileKotlinJvm").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `a redundant type argument exemption of an expect supertype is removed with its own copy only`() {
        val annotation = "@IntentionallyExposedInternalApiForBackwardsCompatibility"
        val project = kmpProject {
            apiWatchdog { severity(NONE, UNDOCUMENTED_PUBLIC_API) }
            source(
                """
                    @InternalAnnotationMarker
                    public annotation class LibInternal

                    @LibInternal
                    public interface HiddenContract

                    public interface Contract

                    public interface Box<A, B>

                    public expect class Args : Box<$annotation HiddenContract, $annotation Contract>
                """.trimIndent(),
                "args",
            )
            source(
                "public actual class Args : Box<$annotation HiddenContract, $annotation Contract>",
                "argsJvm",
                sourceSet = "jvmMain",
            )
        }

        project.build(EXEMPT_TASK).assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)

        val common = project.file("src/commonMain/kotlin/test/args.kt").readText()
        val jvm = project.file("src/jvmMain/kotlin/test/argsJvm.kt").readText()
        assertContains(common, "Box<$annotation HiddenContract, Contract>")
        assertContains(jvm, "Box<$annotation HiddenContract, Contract>")
        val compilation = project.build("compileKotlinJvm")
        compilation.assertNoWatchdogDiagnostics()
        assertFalse("is missing on actual declaration" in compilation.output, compilation.output)
    }

    @Test
    fun `Android library is fixed through both variants`() = androidLibraryIsFixedThroughBothVariantsCase(AgpVersion.AGP8)

    @Test
    fun `Android library is fixed through both variants on AGP 9`() = androidLibraryIsFixedThroughBothVariantsCase(AgpVersion.AGP9)

    private fun androidLibraryIsFixedThroughBothVariantsCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            source(openClass("AndroidApi"), "androidApi")
            source(openClass("DebugOnlyApi"), "debugOnlyApi", sourceSet = "debug")
        }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileDebugKotlin", SUCCESS)
        result.assertTaskOutcome(":compileReleaseKotlin", SUCCESS)
        result.assertOutputHas("2 backwards-compatibility exemption(s)")
        project.assertExempted("src/main/kotlin/test/androidApi.kt", "AndroidApi")
        project.assertExempted("src/debug/kotlin/test/debugOnlyApi.kt", "DebugOnlyApi")
        assertReportGroup(project.report().readText(), "IntentionallyOpen", 2)

        project.build("compileDebugKotlin", "compileReleaseKotlin").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `multiplatform Android target is fixed`() = multiplatformAndroidTargetIsFixedCase(KmpAndroidTarget.LIBRARY_AGP8)

    @Test
    fun `multiplatform Android target is fixed on AGP 9`() = multiplatformAndroidTargetIsFixedCase(KmpAndroidTarget.LIBRARY_AGP9)

    @Test
    fun `legacy multiplatform Android target is fixed`() = multiplatformAndroidTargetIsFixedCase(KmpAndroidTarget.LEGACY)

    private fun multiplatformAndroidTargetIsFixedCase(target: KmpAndroidTarget) {
        val project = kmpAndroidProject(target) {
            source(openClass("SharedApi"), "sharedApi")
            source(openClass("AndroidOnlyApi"), "androidOnlyApi", sourceSet = "androidMain")
        }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlinJvm", SUCCESS)
        result.assertTaskOutcome(":${target.mainCompileTask}", SUCCESS)
        if (target == KmpAndroidTarget.LEGACY) {
            result.assertTaskOutcome(":compileReleaseKotlinAndroid", SUCCESS)
        }
        // The JVM and Android compilations both report the common declaration. It gets one exemption.
        result.assertOutputHas("2 backwards-compatibility exemption(s)")
        project.assertExempted("src/commonMain/kotlin/test/sharedApi.kt", "SharedApi")
        project.assertExempted("src/androidMain/kotlin/test/androidOnlyApi.kt", "AndroidOnlyApi")

        project.build(target.mainCompileTask).assertNoWatchdogDiagnostics()
    }

    @Test
    fun `custom JVM compilations have no explicit API mode and aren't fixed`() {
        val project = jvmProject {
            buildScript(
                """
                    sourceSets.create("extra")
                    kotlin.target.compilations.create("benchmark")
                    kotlin.target.compilations.create("integrationTest") {
                        associateWith(kotlin.target.compilations.getByName("main"))
                    }
                """
            )
            source(openClass("MainApi"), "mainApi")
            source(openClass("ExtraApi"), "extraApi", sourceSet = "extra")
            source(openClass("BenchmarkApi"), "benchmarkApi", sourceSet = "benchmark")
            source(openClass("IntegrationHelper"), "integrationHelper", sourceSet = "integrationTest")
        }
        val customFiles = listOf(
            "src/extra/kotlin/test/extraApi.kt",
            "src/benchmark/kotlin/test/benchmarkApi.kt",
            "src/integrationTest/kotlin/test/integrationHelper.kt",
        ).map(project::file)
        val customTexts = customFiles.map { it.readText() }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlin", SUCCESS)
        result.assertTaskOutcome(":compileExtraKotlin", null)
        result.assertTaskOutcome(":compileBenchmarkKotlin", null)
        result.assertTaskOutcome(":compileIntegrationTestKotlin", null)
        result.assertOutputHas("1 backwards-compatibility exemption(s) in 1 file(s)")
        project.assertExempted("src/main/kotlin/test/mainApi.kt", "MainApi")
        assertEquals(customTexts, customFiles.map { it.readText() })

        project.build("compileExtraKotlin", "compileBenchmarkKotlin").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `raw explicit API flag makes custom JVM compilations fixed`() {
        val project = jvmProject {
            explicitApi = false
            buildScript(
                """
                    kotlin { compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict") }
                    sourceSets.create("extra")
                    kotlin.target.compilations.create("integrationTest") {
                        associateWith(kotlin.target.compilations.getByName("main"))
                    }
                """
            )
            source(openClass("ExtraApi"), "extraApi", sourceSet = "extra")
            source(openClass("IntegrationHelper"), "integrationHelper", sourceSet = "integrationTest")
        }
        val integrationFile = project.file("src/integrationTest/kotlin/test/integrationHelper.kt")
        val integrationText = integrationFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileExtraKotlin", SUCCESS)
        result.assertTaskOutcome(":compileIntegrationTestKotlin", null)
        project.assertExempted("src/extra/kotlin/test/extraApi.kt", "ExtraApi")
        assertEquals(integrationText, integrationFile.readText())

        project.build("compileExtraKotlin").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `custom multiplatform compilation has no explicit API mode and isn't fixed`() {
        val project = kmpProject("jvm { compilations.create(\"extra\") }") {
            source(openClass("SharedApi"), "sharedApi")
            source(openClass("ExtraApi"), "extraApi", sourceSet = "jvmExtra")
        }
        val extraFile = project.file("src/jvmExtra/kotlin/test/extraApi.kt")
        val extraText = extraFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlinJvm", SUCCESS)
        result.assertTaskOutcome(":compileExtraKotlinJvm", null)
        project.assertExempted("src/commonMain/kotlin/test/sharedApi.kt", "SharedApi")
        assertEquals(extraText, extraFile.readText())

        project.build("compileExtraKotlinJvm").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `report task covers the same compilations as the exempt task`() {
        val project = jvmProject {
            buildScript("""sourceSets.create("extra")""")
            source(openClass("MainApi"), "mainApi")
            source(openClass("ExtraApi"), "extraApi", sourceSet = "extra")
        }

        val result = project.build(REPORT_TASK)

        result.assertTaskOutcome(":$REPORT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlin", SUCCESS)
        result.assertTaskOutcome(":compileExtraKotlin", null)
        val report = project.report().readText()
        assertContains(report, "Not applied")
        assertContains(report, "mainApi.kt:")
        assertFalse(report.contains("extraApi.kt:"))
    }

    @Test
    fun `Android flavors and custom build types are fixed`() = androidFlavorsAndCustomBuildTypesCase(AgpVersion.AGP8)

    @Test
    fun `Android flavors and custom build types are fixed on AGP 9`() = androidFlavorsAndCustomBuildTypesCase(AgpVersion.AGP9)

    private fun androidFlavorsAndCustomBuildTypesCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            android(
                """
                    flavorDimensions += "tier"
                    productFlavors {
                        create("free") { dimension = "tier" }
                        create("paid") { dimension = "tier" }
                    }
                    buildTypes { create("staging") }
                """
            )
            source(openClass("MainApi"), "mainApi")
            source(openClass("FreeApi"), "freeApi", sourceSet = "free")
            source(openClass("StagingApi"), "stagingApi", sourceSet = "staging")
            source(openClass("PaidReleaseApi"), "paidReleaseApi", sourceSet = "paidRelease")
            source(openClass("UnitTestHelper"), "unitTestHelper", sourceSet = "test")
            source(openClass("DeviceTestHelper"), "deviceTestHelper", sourceSet = "androidTest")
        }
        val testFiles = listOf("src/test/kotlin/test/unitTestHelper.kt", "src/androidTest/kotlin/test/deviceTestHelper.kt")
            .map(project::file)
        val testTexts = testFiles.map { it.readText() }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        listOf("FreeDebug", "FreeRelease", "FreeStaging", "PaidDebug", "PaidRelease", "PaidStaging").forEach {
            result.assertTaskOutcome(":compile${it}Kotlin", SUCCESS)
        }
        result.assertTaskOutcome(":compileFreeDebugUnitTestKotlin", null)
        result.assertTaskOutcome(":compileFreeDebugAndroidTestKotlin", null)
        result.assertOutputHas("4 backwards-compatibility exemption(s) in 4 file(s)")
        project.assertExempted("src/main/kotlin/test/mainApi.kt", "MainApi")
        project.assertExempted("src/free/kotlin/test/freeApi.kt", "FreeApi")
        project.assertExempted("src/staging/kotlin/test/stagingApi.kt", "StagingApi")
        project.assertExempted("src/paidRelease/kotlin/test/paidReleaseApi.kt", "PaidReleaseApi")
        assertEquals(testTexts, testFiles.map { it.readText() })
        assertReportGroup(project.report().readText(), "IntentionallyOpen", 4)

        project.build("compileFreeStagingKotlin", "compilePaidReleaseKotlin").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `disabled Android variant is skipped`() = disabledAndroidVariantIsSkippedCase(AgpVersion.AGP8)

    @Test
    fun `disabled Android variant is skipped on AGP 9`() = disabledAndroidVariantIsSkippedCase(AgpVersion.AGP9)

    private fun disabledAndroidVariantIsSkippedCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            buildScript(
                """
                    androidComponents {
                        beforeVariants(selector().withBuildType("release")) { it.enable = false }
                    }
                """
            )
            source(openClass("AndroidApi"), "androidApi")
            source(openClass("ReleaseOnlyApi"), "releaseOnlyApi", sourceSet = "release")
        }
        val releaseFile = project.file("src/release/kotlin/test/releaseOnlyApi.kt")
        val releaseText = releaseFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileDebugKotlin", SUCCESS)
        result.assertTaskOutcome(":compileReleaseKotlin", null)
        project.assertExempted("src/main/kotlin/test/androidApi.kt", "AndroidApi")
        assertEquals(releaseText, releaseFile.readText())
    }

    @Test
    fun `legacy multiplatform Android target flavors are fixed`() {
        val project = kmpAndroidProject(KmpAndroidTarget.LEGACY) {
            buildScript(
                """
                    android {
                        flavorDimensions += "tier"
                        productFlavors {
                            create("free") { dimension = "tier" }
                            create("paid") { dimension = "tier" }
                        }
                    }
                """
            )
            source(openClass("SharedApi"), "sharedApi")
            source(openClass("AndroidApi"), "androidApi", sourceSet = "androidMain")
            source(openClass("FreeApi"), "freeApi", sourceSet = "androidFree")
            source(openClass("PaidDebugApi"), "paidDebugApi", sourceSet = "androidPaidDebug")
            source(openClass("UnitTestHelper"), "unitTestHelper", sourceSet = "androidUnitTest")
        }
        val testFile = project.file("src/androidUnitTest/kotlin/test/unitTestHelper.kt")
        val testText = testFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        listOf("FreeDebug", "FreeRelease", "PaidDebug", "PaidRelease").forEach {
            result.assertTaskOutcome(":compile${it}KotlinAndroid", SUCCESS)
        }
        result.assertTaskOutcome(":compileFreeDebugUnitTestKotlinAndroid", null)
        result.assertOutputHas("4 backwards-compatibility exemption(s) in 4 file(s)")
        project.assertExempted("src/commonMain/kotlin/test/sharedApi.kt", "SharedApi")
        project.assertExempted("src/androidMain/kotlin/test/androidApi.kt", "AndroidApi")
        project.assertExempted("src/androidFree/kotlin/test/freeApi.kt", "FreeApi")
        project.assertExempted("src/androidPaidDebug/kotlin/test/paidDebugApi.kt", "PaidDebugApi")
        assertEquals(testText, testFile.readText())

        project.build("compilePaidDebugKotlinAndroid").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `custom intermediate source set is fixed once`() {
        val project = kmpProject(
            """
                jvm()
                js { nodejs() }
                sourceSets {
                    val jvmAndJs by creating { dependsOn(commonMain.get()) }
                    jvmMain.get().dependsOn(jvmAndJs)
                    jsMain.get().dependsOn(jvmAndJs)
                }
            """
        ) {
            source(openClass("SharedApi"), "sharedApi")
            source(openClass("JvmAndJsApi"), "jvmAndJsApi", sourceSet = "jvmAndJs")
            source(openClass("JvmOnlyApi"), "jvmOnlyApi", sourceSet = "jvmMain")
        }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlinJvm", SUCCESS)
        result.assertTaskOutcome(":compileKotlinJs", SUCCESS)
        result.assertOutputHas("3 backwards-compatibility exemption(s) in 3 file(s)")
        project.assertExempted("src/commonMain/kotlin/test/sharedApi.kt", "SharedApi")
        project.assertExempted("src/jvmAndJs/kotlin/test/jvmAndJsApi.kt", "JvmAndJsApi")
        project.assertExempted("src/jvmMain/kotlin/test/jvmOnlyApi.kt", "JvmOnlyApi")

        project.build("compileKotlinJvm", "compileKotlinJs").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `custom target name is fixed`() {
        val project = kmpProject("jvm(\"desktop\")") {
            source(openClass("DesktopApi"), "desktopApi", sourceSet = "desktopMain")
        }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlinDesktop", SUCCESS)
        project.assertExempted("src/desktopMain/kotlin/test/desktopApi.kt", "DesktopApi")
    }

    @Test
    fun `test-named multiplatform compilation isn't fixed`() {
        val project = kmpProject(
            """
                jvm {
                    val main = compilations.getByName("main")
                    compilations.create("integrationTest") { associateWith(main) }
                }
            """
        ) {
            source(openClass("SharedApi"), "sharedApi")
            source(openClass("IntegrationHelper"), "integrationHelper", sourceSet = "jvmIntegrationTest")
        }
        val testFile = project.file("src/jvmIntegrationTest/kotlin/test/integrationHelper.kt")
        val testText = testFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileIntegrationTestKotlinJvm", null)
        project.assertExempted("src/commonMain/kotlin/test/sharedApi.kt", "SharedApi")
        assertEquals(testText, testFile.readText())
    }

    @Test
    fun `test fixtures have no explicit API mode and aren't fixed`() {
        val project = jvmProject {
            plugins(
                KotlinPlugins.jvm("$TEST_COMPILER_VERSION"),
                Plugin("java-test-fixtures"),
                Plugin(WATCHDOG_PLUGIN_ID, pluginUnderTestVersion),
            )
            buildScript("kotlin { explicitApi() }")
            source(openClass("MainApi"), "mainApi")
            source(openClass("Fixture"), "fixture", sourceSet = "testFixtures")
        }
        val fixtureFile = project.file("src/testFixtures/kotlin/test/fixture.kt")
        val fixtureText = fixtureFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileTestFixturesKotlin", null)
        project.assertExempted("src/main/kotlin/test/mainApi.kt", "MainApi")
        assertEquals(fixtureText, fixtureFile.readText())

        project.build("compileTestFixturesKotlin").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `test fixtures are fixed when the requirement is WARN`() = testFixturesAreFixedCase(ExplicitApiRequirement.WARN)

    @Test
    fun `test fixtures are fixed when the requirement is SKIP`() = testFixturesAreFixedCase(ExplicitApiRequirement.SKIP)

    private fun testFixturesAreFixedCase(requirement: ExplicitApiRequirement) {
        val project = jvmProject {
            plugins(
                KotlinPlugins.jvm("$TEST_COMPILER_VERSION"),
                Plugin("java-test-fixtures"),
                Plugin(WATCHDOG_PLUGIN_ID, pluginUnderTestVersion),
            )
            buildScript("kotlin { explicitApi() }")
            apiWatchdog { explicitApiRequirement = requirement }
            source(cleanMainFile)
            source(openClass("Fixture"), "fixture", sourceSet = "testFixtures")
        }

        project.buildAndFail("compileTestFixturesKotlin").assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":compileTestFixturesKotlin", SUCCESS)
        project.assertExempted("src/testFixtures/kotlin/test/fixture.kt", "Fixture")
        project.build("compileTestFixturesKotlin").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `explicit API flag of a target makes its custom compilation fixed`() {
        val project = kmpProject(
            """
                jvm {
                    compilations.create("extra")
                    compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
                }
            """
        ) {
            source(openClass("ExtraApi"), "extraApi", sourceSet = "jvmExtra")
        }

        project.buildAndFail("compileExtraKotlinJvm").assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":compileExtraKotlinJvm", SUCCESS)
        project.assertExempted("src/jvmExtra/kotlin/test/extraApi.kt", "ExtraApi")
        project.build("compileExtraKotlinJvm").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `explicit API flag of a compile task makes its custom compilation fixed`() {
        val project = jvmProject {
            plugins(
                KotlinPlugins.jvm("$TEST_COMPILER_VERSION"),
                Plugin("java-test-fixtures"),
                Plugin(WATCHDOG_PLUGIN_ID, pluginUnderTestVersion),
            )
            buildScript(
                """
                    kotlin { explicitApi() }
                    tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestFixturesKotlin") {
                        compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
                    }
                """
            )
            source(cleanMainFile)
            source(openClass("Fixture"), "fixture", sourceSet = "testFixtures")
        }

        project.buildAndFail("compileTestFixturesKotlin").assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":compileTestFixturesKotlin", SUCCESS)
        project.assertExempted("src/testFixtures/kotlin/test/fixture.kt", "Fixture")
        project.build("compileTestFixturesKotlin").assertNoWatchdogDiagnostics()
    }

    private fun openClass(name: String): String = "/** An existing open API. */\npublic open class $name"

    private fun WatchdogBuild.assertExempted(path: String, className: String) {
        assertContains(
            file(path).readText(),
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class $className",
        )
    }

    private fun WatchdogBuild.mainSource(fileNameWithoutExtension: String): File =
        file("src/main/kotlin/test/$fileNameWithoutExtension.kt")

    private fun WatchdogBuild.report(): File =
        file("build/reports/api-watchdog/report.html")

    /**
     * Adds [sharedDependencyExposureFile] to `commonMain`, [jvmDependencyExposureFile] to `jvmMain`, and
     * [platformDependencyExposureFile] to [otherSourceSet].
     */
    private fun ProjectSpec.sharedDependencyExposureSources(otherSourceSet: String) {
        val imports = listOf("test.model.ExternalModel")
        source(sharedDependencyExposureFile, "shared", "test.consumer")
        source(jvmDependencyExposureFile, "jvmLoaders", "test.consumer", sourceSet = "jvmMain", imports = imports)
        source(platformDependencyExposureFile, "platformLoaders", "test.consumer", sourceSet = otherSourceSet, imports = imports)
    }

    /**
     * Runs [EXEMPT_TASK] with [arguments] twice on the sources of [sharedDependencyExposureSources]. Only `loadJvmModel`
     * gets an exemption, and only when [jvmExempted]. Returns the result of the first run.
     */
    private fun WatchdogBuild.assertSharedDependencyExemptionsStay(
        otherSourceSet: String,
        jvmExempted: Boolean,
        vararg arguments: String,
    ): BuildResult {
        val files = listOf("commonMain/kotlin/test/consumer/shared.kt", "jvmMain/kotlin/test/consumer/jvmLoaders.kt", "$otherSourceSet/kotlin/test/consumer/platformLoaders.kt")
            .map { file("src/$it") }
        val (common, jvm, other) = files.map { it.readText() }
        val jvmDoc = "/** Loads a model on the JVM. */\n"
        val expected = listOf(common, if (jvmExempted) jvm.replace(jvmDoc, "$jvmDoc$BACKWARDS_COMPATIBLE_DEPENDENCY_EXEMPTION\n") else jvm, other)

        val result = build(EXEMPT_TASK, *arguments)

        result.assertOutputLacks("Removed the redundant")
        assertFalse("fix-status redundant" in report().readText())
        assertEquals(expected, files.map { it.readText() })
        build(EXEMPT_TASK, *arguments).assertOutputHas("No watchdog errors to exempt.")
        assertEquals(expected, files.map { it.readText() })
        return result
    }

    private companion object {
        const val EXEMPT_TASK = "exemptExistingApi"
        const val REPORT_TASK = "generateApiWatchdogReport"
        const val PLACEHOLDER_KDOC = "/** TODO: Document this declaration */"
        const val ALL_WARNINGS_AS_ERRORS_BUILD_SCRIPT = "kotlin { compilerOptions { allWarningsAsErrors = true } }"
        const val CONTEXT_PARAMETERS_BUILD_SCRIPT = """kotlin { compilerOptions.freeCompilerArgs.add("-Xcontext-parameters") }"""
    }
}

/** One specimen of every diagnostic that the fixer resolves. Two declarations are undocumented. */
@Suppress("RedundantSuspendModifier", "MayBeConstant")
@Language("kotlin")
private val fixableFile = """
    /** A deliberately unrestricted base class. */
    public open class UnprotectedOpenClass

    public open class UndocumentedOpenClass {
        public fun undocumentedMember() {}
    }

    /** A base class whose subclass opt-in doesn't list any markers and so doesn't restrict anything. */
    @SubclassOptInRequired
    public open class UnmarkedOptIn

    /** A deliberately matchable enum. */
    public enum class UnmarkedEnum {
        /** The first entry. */
        A,

        /** The second entry. */
        B,
    }

    /** An unacknowledged function type alias. */
    public typealias UnacknowledgedCallback = (Int) -> Unit

    /** An unacknowledged data class. */
    public data class UnmarkedData(
        /** The only coordinate. */
        val x: Int,
    )

    /** A stateful session relying on the opaque default toString. */
    public class UnrenderedSession(
        /** The session identifier. */
        public val id: Int,
    )

    /** A function handing out the library's mutable state. */
    public fun leakState(): MutableList<String> = mutableListOf()

    /** A function pairing coordinates without naming them. */
    public fun locateOrigin(): Pair<Int, Int> = 0 to 0

    /** A function switched by an opaque positional flag. */
    public fun toggleWork(enabled: Boolean) {}

    /** A function switched by a flag the call site puts into scope. */
    context(enabled: Boolean)
    public fun toggleContextWork() {}

    /** A query returning a silent three-state flag. */
    public fun lastKnownState(): Boolean? = null

    /** A function declaring a required parameter after an optional one. */
    public fun retryWork(retries: Int = 3, host: String) {}

    /** An overload setting the parameter order convention. */
    public fun drawShape(x: Int, y: Int) {}

    /** An overload breaking the parameter order convention. */
    public fun drawShape(y: Int, x: Int, scale: Double) {}

    /** An inline function computing inline instead of delegating. */
    @Suppress("NOTHING_TO_INLINE")
    public inline fun squared(value: Int): Int = value * value

    /** A DSL marker with a target on which it doesn't have any effect. */
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    public annotation class NoopTargetDsl

    /** A DSL marker left with the default target set. */
    @DslMarker
    public annotation class TargetlessDsl

    /** A user handle compiled to its underlying type. */
    @JvmInline
    public value class UserHandle(
        /** The raw handle value. */
        public val raw: String,
    )

    /** A lookup whose JVM name is mangled by the value class parameter. */
    public fun findUser(handle: UserHandle) {}

    /** A coordinator whose companion members hide behind the Companion instance for Java. */
    public class Coordinator {
        /** The companion holding the factory and the default label. */
        public companion object {
            /** A factory Java callers reach only through the Companion instance. */
            public fun instance(): Coordinator = Coordinator()

            /** A constant Java callers read only through the Companion instance getter. */
            public val DEFAULT_LABEL: String = "coordinator"
        }
    }

    /** A suspend function left visible to Java sources. */
    public suspend fun refreshState(): Int = 0

    /** A function whose default parameter values don't exist for Java callers. */
    public fun openPort(host: String, port: Int = 8080) {}

    /** A loader that returns a type of an `implementation` dependency. */
    public fun loadExternalModel(): ExternalModel = ExternalModel()
""".trimIndent()

/** The diagnostics without an exemption annotation. */
@Language("kotlin")
private val unfixableFile = """
    /** A DSL marker with tidy targets, misapplied to an inert type position below. */
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
    public annotation class ScopedDsl

    /** A parameter type carrying a DSL marker that doesn't restrict anything. */
    public fun processTag(tag: @ScopedDsl String) { }
""".trimIndent()

/** The always-error diagnostic without an automatic fix, which the task skips. */
@Language("kotlin")
private val alwaysErrorDiagnosticsFile = """
    /** Existing open API with an invalid, unfixable exemption. */
    @IntentionallyOpen
    public open class ExistingOpenApi

    /** Public entry points. */
    public object Api {
        /** Carries an invalid type-use exemption handled by the dedicated type checker. */
        public fun loadMutable(): @IntentionallyMutableCollection MutableList<String> = mutableListOf()
    }
""".trimIndent()

/** Internal API types in public signatures, which the task exempts for backwards compatibility. */
@Language("kotlin")
private val internalApiExposureFile = """
    /** Marks declarations that are public only for technical reasons. */
    @InternalAnnotationMarker
    @Target(AnnotationTarget.CLASS)
    public annotation class InternalLibApi

    @InternalLibApi
    public open class InternalModel

    /** Public entry points that expose an unsupported type. */
    public object Api {
        /** Leaks an explicitly unsupported type. */
        public fun loadModel(): InternalModel = InternalModel()

        /** Leaks it twice in one signature. */
        public fun useModels(first: InternalModel, second: InternalModel) {}

        /** Exposes nothing unsupported. */
        public fun supported(): String = ""
    }

    /** Holds an unsupported type in a constructor property. */
    public class Holder(public val model: InternalModel)

    /** Extends an unsupported type. */
    public class Special : InternalModel()
""".trimIndent()

/** A needed and a redundant exemption of the internal API exposure check. */
@Language("kotlin")
private val redundantInternalApiExemptionFile = """
    /** Marks declarations that are public only for technical reasons. */
    @InternalAnnotationMarker
    @Target(AnnotationTarget.CLASS)
    public annotation class InternalLibApi

    @InternalLibApi
    public class InternalModel

    /** Returns an unsupported type. */
    @IntentionallyExposedInternalApiForBackwardsCompatibility
    public fun needed(): InternalModel = InternalModel()

    /** Returns a supported type. */
    @IntentionallyExposedInternalApiForBackwardsCompatibility
    public fun supported(): String = ""
""".trimIndent()

/** An exemption of the dependency exposure check on a declaration that doesn't expose a dependency type. */
@Language("kotlin")
private val redundantNonTransitiveExemptionFile = """
    /** Returns a count. */
    @IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
    public fun count(): Int = 1
""".trimIndent()

/** Common declarations that expose a type of an `implementation` dependency. */
@Language("kotlin")
private val multiplatformDependencyExposureFile = """
    import test.model.ExternalModel

    /** Loads a model in every target. */
    public fun loadModel(): ExternalModel = ExternalModel()

    /** Loads a model in a platform-specific way. */
    public expect fun loadPlatformModel(): ExternalModel
""".trimIndent()

private const val HOST_MODEL_EXEMPTION =
    "@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = \"The host ships the model.\")"

private const val BACKWARDS_COMPATIBLE_DEPENDENCY_EXEMPTION =
    "@IntentionallyNonTransitiveDependency(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)"

/** `commonMain` declarations that expose a type of an `implementation` dependency, two of them with an exemption. */
@Language("kotlin")
private val sharedDependencyExposureFile = """
    import test.model.ExternalModel

    /** Loads a model that the host ships. */
    $HOST_MODEL_EXEMPTION
    public fun loadHostModel(): ExternalModel = ExternalModel()

    /** Loads a model in a platform-specific way. */
    $HOST_MODEL_EXEMPTION
    public expect fun loadPlatformModel(): ExternalModel

    /** Loads a model without an exemption. */
    public fun loadSharedModel(): ExternalModel = ExternalModel()

    /** Counts the models. */
    $HOST_MODEL_EXEMPTION
    public expect fun count(): Int

    /** Holds the models. */
    $HOST_MODEL_EXEMPTION
    public expect class Holder()
""".trimIndent()

/** The `jvmMain` part of [sharedDependencyExposureFile]. */
@Language("kotlin")
private val jvmDependencyExposureFile = """
    /** Loads a model on the JVM. */
    public fun loadJvmModel(): ExternalModel = ExternalModel()

    $HOST_MODEL_EXEMPTION
    public actual fun loadPlatformModel(): ExternalModel = ExternalModel()

    $HOST_MODEL_EXEMPTION
    public actual fun count(): Int = 1

    $HOST_MODEL_EXEMPTION
    public actual class Holder actual constructor()
""".trimIndent()

/** The part of [sharedDependencyExposureFile] for the target other than JVM. */
@Language("kotlin")
private val platformDependencyExposureFile = """
    $HOST_MODEL_EXEMPTION
    public actual fun loadPlatformModel(): ExternalModel = ExternalModel()

    $HOST_MODEL_EXEMPTION
    public actual fun count(): Int = 1

    $HOST_MODEL_EXEMPTION
    public actual class Holder actual constructor()
""".trimIndent()

/** A class with a supertype of an `implementation` dependency and members with their own exemptions. */
@Language("kotlin")
private val holderWithMemberExemptionsFile = """
    /** Holds models. */
    public class Holder : ExternalContract {
        /** Loads a model. */
        $HOST_MODEL_EXEMPTION
        public fun load(): ExternalModel = ExternalModel()

        /** Reads a model. */
        public fun read(): $HOST_MODEL_EXEMPTION ExternalModel = ExternalModel()

        /** Reads models in a nested class. */
        public class Reader {
            /** Reads a model. */
            $HOST_MODEL_EXEMPTION
            public fun read(): ExternalModel = ExternalModel()
        }
    }
""".trimIndent()

@Language("kotlin")
private val multiplatformFile = """
    /** A shared data holder. */
    public data class SharedData(
        /** The only coordinate. */
        val x: Int,
    )
""".trimIndent()
