@file:Suppress("RedundantVisibilityModifier", "FunctionName")

package kotlinx.library.watchdog

import com.autonomousapps.kit.gradle.Dependency.Companion.api
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import com.autonomousapps.kit.gradle.Plugin
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.library.watchdog.AgpVersion.AGP8
import kotlinx.library.watchdog.AgpVersion.AGP9
import kotlinx.library.watchdog.Diagnostic.BOOLEAN_PARAMETER_PUBLIC_API
import kotlinx.library.watchdog.Diagnostic.COMPANION_API_WITHOUT_JVM_STATIC
import kotlinx.library.watchdog.Diagnostic.DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS
import kotlinx.library.watchdog.Diagnostic.EXEMPTION_WITHOUT_EXPLANATION
import kotlinx.library.watchdog.Diagnostic.EXHAUSTIVE_PUBLIC_API
import kotlinx.library.watchdog.Diagnostic.KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC
import kotlinx.library.watchdog.Diagnostic.MUTABLE_COLLECTION_PUBLIC_API
import kotlinx.library.watchdog.Diagnostic.NULLABLE_BOOLEAN_PUBLIC_API
import kotlinx.library.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import kotlinx.library.watchdog.Diagnostic.PAIR_OR_TRIPLE_PUBLIC_API
import kotlinx.library.watchdog.Diagnostic.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY
import kotlinx.library.watchdog.Diagnostic.PUBLIC_TYPE_WITH_INTERNAL_API
import kotlinx.library.watchdog.Diagnostic.REDUNDANT_EXEMPTION
import kotlinx.library.watchdog.Diagnostic.STATEFUL_CLASS_WITHOUT_EQUALS
import kotlinx.library.watchdog.Diagnostic.STATEFUL_CLASS_WITHOUT_HASH_CODE
import kotlinx.library.watchdog.Diagnostic.STATEFUL_CLASS_WITHOUT_TO_STRING
import kotlinx.library.watchdog.Diagnostic.TOP_LEVEL_API_WITHOUT_JVM_NAME
import kotlinx.library.watchdog.Diagnostic.UNDOCUMENTED_PUBLIC_API
import kotlinx.library.watchdog.KmpAndroidTarget.LEGACY
import kotlinx.library.watchdog.KmpAndroidTarget.LIBRARY_AGP8
import kotlinx.library.watchdog.KmpAndroidTarget.LIBRARY_AGP9
import kotlinx.library.watchdog.Severity.ERROR
import kotlinx.library.watchdog.Severity.NONE
import kotlinx.library.watchdog.Severity.WARNING
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome.SKIPPED
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.devkit.test.KotlinPlugins
import org.jetbrains.kotlin.compiler.plugin.devkit.test.TEST_COMPILER_VERSION
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.experimental.categories.Category

class WatchdogProjectTest {
    @Test
    @Category(SmokeTest::class)
    fun `fails with errors on unacknowledged API by default`() {
        val project = jvmProject {
            buildScript(REPORT_ALL_WARNINGS_FLAG)
            source(unacknowledgedFile)
            source(internalApiExposureFile, "internalApiExposure")
        }

        val result = project.buildAndFail("build")
        result.assertErrors(*(singleModuleDiagnostics - UNDOCUMENTED_PUBLIC_API).toTypedArray())
        result.assertWarnings(UNDOCUMENTED_PUBLIC_API)
        result.assertError("Poko (https://github.com/drewhamilton/Poko)")
        result.assertError(
            "Poko (https://kotlin.github.io/Library-API-Watchdog/checks/" +
                    "stateful-class-without-equals-hashcode-to-string#poko)",
        )
        result.assertError("press `${ideaGenerateShortcut()}`")
        result.assertError("mangles the JVM method name")
        result.assertError("compiles to an instance method of the nested `Companion` class")
        result.assertError("has its getter on the nested companion class")
        result.assertError("Java-visible top-level functions")
        result.assertError("declares the `FUNCTION` annotation target")
        result.assertError("doesn't have any effect on this parameter type")
        result.assertError(
            "parameter `first` publicly exposes `test.InternalModel`, but that type is marked " +
                    "as internal API with `@InternalLibApi`",
        )
        result.assertError(
            "parameter `second` publicly exposes `test.OtherInternalModel`, but that type is " +
                    "marked as internal API with `@OtherInternalApi`",
        )
    }

    @Test
    fun `demoted diagnostics are reported as warnings`() {
        val project = jvmProject {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            source(unacknowledgedFile)
        }

        val result = project.build("build")
        result.assertWarnings(*configurableDiagnostics.toTypedArray())
    }

    @Test
    fun `severity is configured per diagnostic`() {
        val project = jvmProject {
            buildScript(REPORT_ALL_WARNINGS_FLAG)
            apiWatchdog {
                severity(WARNING, OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
                severity(ERROR, UNDOCUMENTED_PUBLIC_API)
            }
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("build")
        result.assertErrors(UNDOCUMENTED_PUBLIC_API, EXHAUSTIVE_PUBLIC_API)
        result.assertWarnings(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
    }

    @Test
    fun `disabled Java interop group silences all its diagnostics`() {
        val project = jvmProject {
            apiWatchdog {
                javaInterop {
                    enabled = false
                    severity(ERROR, COMPANION_API_WITHOUT_JVM_STATIC)
                }
            }
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("build")
        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        result.assertNotReported(*Diagnostic.entries.filter { it.javaInterop }.toTypedArray())
    }

    @Test
    fun `disabled diagnostics aren't reported`() {
        val project = jvmProject {
            apiWatchdog {
                severity(
                    NONE,
                    UNDOCUMENTED_PUBLIC_API,
                    STATEFUL_CLASS_WITHOUT_EQUALS,
                    STATEFUL_CLASS_WITHOUT_HASH_CODE,
                    STATEFUL_CLASS_WITHOUT_TO_STRING,
                )
            }
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("build")
        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, EXHAUSTIVE_PUBLIC_API)
        result.assertNotReported(
            UNDOCUMENTED_PUBLIC_API,
            STATEFUL_CLASS_WITHOUT_EQUALS,
            STATEFUL_CLASS_WITHOUT_HASH_CODE,
            STATEFUL_CLASS_WITHOUT_TO_STRING,
        )
    }

    @Test
    fun `Poko-generated members satisfy the stateful class checks`() {
        val pokoVersion = pokoVersionFor(TEST_COMPILER_VERSION.toKotlinVersion())
        assumeTrue("No Poko release supports Kotlin $TEST_COMPILER_VERSION", pokoVersion != null)
        val project = jvmProject {
            plugins(
                KotlinPlugins.jvm("$TEST_COMPILER_VERSION"),
                Plugin(WATCHDOG_PLUGIN_ID, pluginUnderTestVersion),
                Plugin("dev.drewhamilton.poko", pokoVersion),
            )
            buildScript("kotlin { explicitApi() }")
            source(
                """
                    /** A connection. */
                    @Poko
                    public class Connection(
                        /** The host. */
                        public val host: String,
                    )

                    /** A connection that prints its state. */
                    @Poko.ToString
                    public class PrintedConnection(
                        /** The host. */
                        public val host: String,
                    )

                    /** A connection that compares its state. */
                    @Poko.EqualsAndHashCode
                    public class ComparedConnection(
                        /** The host. */
                        public val host: String,
                    )
                """,
                "Connections",
                imports = listOf("dev.drewhamilton.poko.Poko"),
                fileAnnotation = "@file:OptIn(dev.drewhamilton.poko.IndependentFunctionsSupport::class)",
            )
        }

        val result = project.buildAndFail("build")
        result.assertErrors(
            STATEFUL_CLASS_WITHOUT_EQUALS,
            STATEFUL_CLASS_WITHOUT_HASH_CODE,
            STATEFUL_CLASS_WITHOUT_TO_STRING,
        )
        assertFalse(result.hasStatefulDiagnostic("Connection", "an `equals`"), result.output)
        assertFalse(result.hasStatefulDiagnostic("Connection", "a `hashCode`"), result.output)
        assertFalse(result.hasStatefulDiagnostic("Connection", "a `toString`"), result.output)
        assertTrue(result.hasStatefulDiagnostic("PrintedConnection", "an `equals`"), result.output)
        assertTrue(result.hasStatefulDiagnostic("PrintedConnection", "a `hashCode`"), result.output)
        assertFalse(result.hasStatefulDiagnostic("PrintedConnection", "a `toString`"), result.output)
        assertFalse(result.hasStatefulDiagnostic("ComparedConnection", "an `equals`"), result.output)
        assertFalse(result.hasStatefulDiagnostic("ComparedConnection", "a `hashCode`"), result.output)
        assertTrue(result.hasStatefulDiagnostic("ComparedConnection", "a `toString`"), result.output)
    }

    @Test
    fun `configured annotations can ignore one check`() {
        val project = jvmProject {
            apiWatchdog {
                ignore(
                    STATEFUL_CLASS_WITHOUT_EQUALS,
                    "test.GeneratedValueMembers",
                    "test.AlternateGeneratedValueMembers",
                )
            }
            source(annotationIgnoreFile)
        }

        val result = project.buildAndFail("build")
        assertFalse(result.hasStatefulDiagnostic("GeneratedState", "an `equals`"))
        assertFalse(result.hasStatefulDiagnostic("AlternateGeneratedState", "an `equals`"))
        assertTrue(result.hasStatefulDiagnostic("GeneratedState", "a `hashCode`"))
        assertTrue(result.hasStatefulDiagnostic("GeneratedState", "a `toString`"))
        assertTrue(result.hasStatefulDiagnostic("NestedState", "an `equals`"))
        assertTrue(result.hasStatefulDiagnostic("RegularState", "an `equals`"))
    }

    @Test
    fun `recursive ignore rule covers nested declarations`() {
        val project = jvmProject {
            apiWatchdog {
                ignore(STATEFUL_CLASS_WITHOUT_EQUALS, "test.GeneratedValueMembers", recursive = true)
                ignore(STATEFUL_CLASS_WITHOUT_HASH_CODE, "test.GeneratedValueMembers")
            }
            source(annotationIgnoreFile)
        }

        val result = project.buildAndFail("build")
        assertFalse(result.hasStatefulDiagnostic("GeneratedState", "an `equals`"))
        assertFalse(result.hasStatefulDiagnostic("NestedState", "an `equals`"))
        assertTrue(result.hasStatefulDiagnostic("NestedState", "a `hashCode`"))
        assertTrue(result.hasStatefulDiagnostic("RegularState", "an `equals`"))
    }

    @Test
    fun `ignore accepts a list of checks and a single annotation`() {
        val project = jvmProject {
            apiWatchdog {
                ignoreOne(
                    listOf(STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE),
                    "test.GeneratedValueMembers",
                )
                ignore(listOf(STATEFUL_CLASS_WITHOUT_TO_STRING), "test.AlternateGeneratedValueMembers")
                ignoreOne(STATEFUL_CLASS_WITHOUT_EQUALS, "test.AlternateGeneratedValueMembers")
            }
            source(annotationIgnoreFile)
        }

        val result = project.buildAndFail("build")
        assertFalse(result.hasStatefulDiagnostic("GeneratedState", "an `equals`"))
        assertFalse(result.hasStatefulDiagnostic("GeneratedState", "a `hashCode`"))
        assertTrue(result.hasStatefulDiagnostic("GeneratedState", "a `toString`"))
        assertFalse(result.hasStatefulDiagnostic("AlternateGeneratedState", "an `equals`"))
        assertTrue(result.hasStatefulDiagnostic("AlternateGeneratedState", "a `hashCode`"))
        assertFalse(result.hasStatefulDiagnostic("AlternateGeneratedState", "a `toString`"))
        assertTrue(result.hasStatefulDiagnostic("RegularState", "an `equals`"))
    }

    @Test
    fun `ignore rejects an always-error check`() {
        val project = jvmProject {
            apiWatchdog { ignore(EXEMPTION_WITHOUT_EXPLANATION, "test.GeneratedValueMembers") }
            source(annotationIgnoreFile)
        }

        project.buildAndFail("build").assertOutputHas(
            "API Watchdog check 'EXEMPTION_WITHOUT_EXPLANATION' is always an error and doesn't accept an ignore rule"
        )
    }

    @Test
    fun `unexplained exemption is always an error`() {
        // The checker that reads the exemption is off, so the error can't come from it.
        val project = jvmProject {
            apiWatchdog { severity(NONE, MUTABLE_COLLECTION_PUBLIC_API) }
            source(unexplainedExemptionFile)
        }

        project.buildAndFail("build").assertErrors(EXEMPTION_WITHOUT_EXPLANATION)
    }

    @Test
    fun `internal API exposure is always an error`() {
        val project = jvmProject { source(internalApiExposureFile, "internalApiExposure") }

        project.buildAndFail("build").assertError("marked as internal API with `@InternalLibApi`")
    }

    @Test
    fun `internal API exposure check can only be disabled as a whole`() {
        val project = jvmProject {
            apiWatchdog { publicTypeWithInternalApi = false }
            source(internalApiExposureFile, "internalApiExposure")
        }

        project.build("build").assertNotReported(PUBLIC_TYPE_WITH_INTERNAL_API)
    }

    @Test
    fun `internal API exposure is accepted when exempted for backwards compatibility`() {
        val project = jvmProject { source(exemptedInternalApiExposureFile, "exemptedInternalApiExposure") }

        project.build("build").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `internal API exposure exemption on a class doesn't cover its members`() {
        val project = jvmProject { source(partiallyExemptedInternalApiExposureFile, "partiallyExempted") }

        val result = project.buildAndFail("build")

        result.assertErrors(PUBLIC_TYPE_WITH_INTERNAL_API)
        result.assertOutputHas("The function `member` publicly exposes `test.InternalModel`")
        result.assertOutputLacks("`Exempted` publicly exposes")
        result.assertNotReported(REDUNDANT_EXEMPTION)
    }

    @Test
    fun `unneeded internal API exposure exemption is redundant`() {
        val project = jvmProject { source(redundantInternalApiExposureExemptionFile, "redundantExemption") }

        val result = project.build("build")

        result.assertWarnings(REDUNDANT_EXEMPTION)
        result.assertOutputHas("The `@IntentionallyExposedInternalApiForBackwardsCompatibility` exemption is redundant.")
    }

    @Test
    fun `internal API exposure exemption is redundant when the check is off`() {
        val project = jvmProject {
            apiWatchdog { publicTypeWithInternalApi = false }
            source(exemptedInternalApiExposureFile, "exemptedInternalApiExposure")
        }

        val result = project.build("build")

        result.assertNotReported(PUBLIC_TYPE_WITH_INTERNAL_API)
        result.assertWarnings(REDUNDANT_EXEMPTION)
    }

    @Test
    fun `undocumented class properties are skipped by default`() {
        val result = holderProject(reportUndocumentedClassProperties = null).build("build")

        result.assertWarnings(UNDOCUMENTED_PUBLIC_API)
        result.assertOutputHas("The public function `undocumentedFunction` doesn't have `KDoc`")
        result.assertOutputHas("The public property `undocumentedTopLevelProperty` doesn't have `KDoc`")
        result.assertOutputLacks("`value` doesn't have `KDoc`")
    }

    @Test
    fun `undocumented class properties can be reported`() {
        val result = holderProject(reportUndocumentedClassProperties = true).build("build")

        result.assertOutputHas("The public property `value` doesn't have `KDoc`")
    }

    @Test
    fun `deprecated API is checked by default`() {
        val result = deprecatedProject(ignoreDeprecatedApi = null).buildAndFail("build")

        result.assertErrors(BOOLEAN_PARAMETER_PUBLIC_API)
    }

    @Test
    fun `deprecated API can be ignored`() {
        val result = deprecatedProject(ignoreDeprecatedApi = true).build("build")

        result.assertNoWatchdogDiagnostics()
    }

    private fun deprecatedProject(ignoreDeprecatedApi: Boolean?) = jvmProject {
        apiWatchdog {
            this.ignoreDeprecatedApi = ignoreDeprecatedApi
            severity(NONE, TOP_LEVEL_API_WITHOUT_JVM_NAME)
        }
        source(
            """
            /** Deprecated. */
            @Deprecated("Use enable.")
            public fun toggle(on: Boolean) {}
            """,
            "deprecated",
        )
    }

    private fun holderProject(reportUndocumentedClassProperties: Boolean?) = jvmProject {
        apiWatchdog {
            this.reportUndocumentedClassProperties = reportUndocumentedClassProperties
            severity(NONE, OPEN_API_WITHOUT_SUBCLASS_OPT_IN, TOP_LEVEL_API_WITHOUT_JVM_NAME)
        }
        source(
            """
            /**
             * Documented.
             *
             * @property value Documented through the class KDoc.
             */
            public interface Holder {
                public val value: Int

                public fun undocumentedFunction(): Int
            }

            public val undocumentedTopLevelProperty: Int = 0
            """,
            "holder",
        )
    }

    @Test
    fun `acknowledged API compiles without diagnostics`() {
        val project = jvmProject {
            source(
                acknowledgedFile,
                "acknowledged",
                fileAnnotation = "@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)",
            )
        }

        project.build("build").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `a redundant exemption is a warning that the report tags and exemptExistingApi removes`() {
        val project = jvmProject {
            apiWatchdog { suggestAbiValidation = false }
            source(redundantExemptionFile, "redundant")
        }

        val result = project.build("build", "generateApiWatchdogReport")
        result.assertWarnings(REDUNDANT_EXEMPTION)
        result.assertReportedIn(REDUNDANT_EXEMPTION, "redundant.kt")
        result.assertWarning("The `@IntentionallyDataClass` exemption is redundant.")
        result.assertNotReported(*configurableDiagnostics.toTypedArray())

        val report = project.file("build/reports/api-watchdog/report.html").readText()
        assertContains(report, "1 redundant annotation")
        assertContains(report, "<span class=\"fix-status redundant\">Redundant</span>")
        assertContains(report, "<span class=\"fix-status applied\">Applied</span>")
        assertReportGroup(report, "IntentionallyDataClass", 1, REDUNDANT_EXEMPTION)
        assertContains(report, "exemptExistingApi can remove it.")

        val fixed = project.build("exemptExistingApi")
        fixed.assertOutputHas("Removed the redundant @IntentionallyDataClass at src/main/kotlin/test/redundant.kt:")
        fixed.assertOutputHas("removed 1 redundant exemption(s)")
        val source = project.file("src/main/kotlin/test/redundant.kt").readText()
        assertEquals(1, Regex("@IntentionallyDataClass").findAll(source).count(), source)
        assertContains(source, "/** A regular class with a data class exemption. */\npublic class Settled")
        project.build("build").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `fails without explicit API mode`() {
        val project = jvmProject {
            explicitApi = false
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("build")
        result.assertOutputHas("doesn't enable explicit API mode")
        result.assertOutputHas("explicitApi()")
        result.assertOutputHas("ExplicitApiRequirement.WARN")
        result.assertNoWatchdogDiagnostics()
        assertFalse(result.output.contains("Task :compileKotlin"), result.output)
    }

    @Test
    fun `fails without explicit API mode in a multiplatform project`() {
        val project = kmpProject {
            explicitApi = false
            source(unacknowledgedFile)
        }

        project.buildAndFail("compileKotlinJvm").assertOutputHas("doesn't enable explicit API mode")
    }

    @Test
    fun `explicit API requirement lets help and the exemption tasks run`() {
        val project = jvmProject {
            explicitApi = false
            source(unacknowledgedFile)
        }

        project.build("help").assertOutputLacks("doesn't enable explicit API mode")
        project.build("generateApiWatchdogReport").assertOutputLacks("doesn't enable explicit API mode")
    }

    @Test
    fun `checks run without explicit API mode when the requirement is SKIP`() {
        val project = jvmProject {
            explicitApi = false
            apiWatchdog { explicitApiRequirement = ExplicitApiRequirement.SKIP }
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("build")
        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, EXHAUSTIVE_PUBLIC_API, MUTABLE_COLLECTION_PUBLIC_API)
        result.assertOutputLacks("doesn't enable explicit API mode")
    }

    @Test
    fun `disabled watchdog adds no compiler plugin and skips its tasks`() {
        val project = jvmProject {
            explicitApi = false
            apiWatchdog { enabled = false }
            source(unacknowledgedFile)
            source(
                acknowledgedFile,
                "acknowledged",
                fileAnnotation = "@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)",
            )
            buildScript(
                """
                    tasks.compileKotlin {
                        doLast { println("PLUGIN_CLASSPATH=" + pluginClasspath.files.joinToString { it.name }) }
                    }
                """
            )
        }

        val result = project.build("build", "exemptExistingApi", "generateApiWatchdogReport")
        result.assertNoWatchdogDiagnostics()
        result.assertOutputHas("PLUGIN_CLASSPATH=")
        result.assertOutputLacks("watchdog-compiler-plugin")
        result.assertOutputLacks("doesn't enable explicit API mode")
        result.assertOutputLacks("but doesn't enable binary compatibility validation")
        result.assertTaskOutcome(":exemptExistingApi", SKIPPED)
        result.assertTaskOutcome(":generateApiWatchdogReport", SKIPPED)
    }

    @Test
    fun `disabled watchdog keeps exemption annotations in a multiplatform project`() {
        val project = kmpProject {
            apiWatchdog { enabled = false }
            source(unacknowledgedFile)
            source(
                acknowledgedFile,
                "acknowledged",
                fileAnnotation = "@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)",
            )
        }

        val result = project.build("build", "generateApiWatchdogReport")
        result.assertNoWatchdogDiagnostics()
        result.assertTaskOutcome(":generateApiWatchdogReport", SKIPPED)
    }

    @Test
    fun `disabled watchdog keeps exemption annotations when it is applied before the Android plugins`() =
        disabledWatchdogBeforeAndroidPluginCase(AGP8)

    @Test
    fun `disabled watchdog keeps exemption annotations when it is applied before AGP 9`() =
        disabledWatchdogBeforeAndroidPluginCase(AGP9)

    private fun disabledWatchdogBeforeAndroidPluginCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            plugins(
                Plugin(WATCHDOG_PLUGIN_ID, pluginUnderTestVersion),
                Plugin("com.android.library", agp.version),
                when (agp) {
                    AGP8 -> Plugin(KOTLIN_ANDROID_PLUGIN_ID, "$TEST_COMPILER_VERSION")
                    AGP9 -> Plugin(KOTLIN_JVM_PLUGIN_ID, "$TEST_COMPILER_VERSION", apply = false)
                },
            )
            buildScript(
                """
                    android {
                        namespace = "test.consumer"
                        compileSdk = 36
                        compileOptions {
                            sourceCompatibility = JavaVersion.VERSION_17
                            targetCompatibility = JavaVersion.VERSION_17
                        }
                    }
                    kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
                """
            )
            apiWatchdog { enabled = false }
            source(
                acknowledgedFile,
                "acknowledged",
                fileAnnotation = "@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)",
            )
        }

        project.build("compileDebugKotlin").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `test sources aren't checked`() {
        val project = jvmProject {
            source(cleanMainFile)
            testOnlySource()
        }

        project.build("compileTestKotlin").assertNoTestSourceDiagnostics()
    }

    @Test
    fun `test sources aren't checked when explicit API mode is forced on every compilation`() {
        val project = jvmProject {
            explicitApi = false
            buildScript(EXPLICIT_API_FLAG)
            source(cleanMainFile)
            testOnlySource()
        }

        project.build("compileTestKotlin").assertNoTestSourceDiagnostics()
    }

    @Test
    fun `multiplatform test sources aren't checked`() {
        val project = kmpProject {
            explicitApi = false
            buildScript(EXPLICIT_API_FLAG)
            source(cleanMainFile)
            testOnlySource("commonTest")
            testOnlySource("jvmTest", "PlatformTestOnlyHelper")
        }

        project.build("compileTestKotlinJvm").assertNoTestSourceDiagnostics()
    }

    @Test
    @Category(SmokeTest::class)
    fun `expect owns contract checks and exemptions`() {
        val project = kmpProject {
            kotlinFile(
                "CommonApi",
                text = """
                    package test

                    import kotlinx.library.watchdog.ExemptionReason
                    import kotlinx.library.watchdog.IntentionallyBooleanParameter
                    import kotlinx.library.watchdog.IntentionallyNullableBoolean
                    import kotlinx.library.watchdog.IntentionallyPairOrTriple

                    /** The shared contract owns its source-shape exemptions. */
                    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
                    @IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)
                    @IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)
                    public expect fun shared(flag: Boolean): Pair<String, Boolean?>

                    /** An unexempt shared contract is reported from common source. */
                    public expect fun reported(flag: Boolean): Pair<String, Boolean?>
                """,
            )
            kotlinFile(
                "JvmApi",
                "jvmMain",
                """
                    $DEFAULT_FACADE_NAME_FILE_ANNOTATION

                    package test

                    public actual fun shared(flag: Boolean): Pair<String, Boolean?> =
                        flag.toString() to flag

                    public actual fun reported(flag: Boolean): Pair<String, Boolean?> =
                        flag.toString() to flag

                    /** Platform-only declarations remain part of the checked source API. */
                    public fun platformOnly(flag: Boolean): Int = if (flag) 1 else 0
                """,
            )
        }

        val result = project.buildAndFail("compileKotlinJvm")
        result.assertReportedIn(BOOLEAN_PARAMETER_PUBLIC_API, "CommonApi.kt", "JvmApi.kt")
        result.assertReportedIn(NULLABLE_BOOLEAN_PUBLIC_API, "CommonApi.kt")
        result.assertReportedIn(PAIR_OR_TRIPLE_PUBLIC_API, "CommonApi.kt")
    }

    @Test
    fun `JVM checks report actual once with the expect signature`() {
        val project = kmpProject {
            apiWatchdog {
                severity(WARNING, KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC, DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS)
            }
            kotlinFile(
                "CommonApi",
                text = """
                    package test

                    /** A Kotlin-only shared contract. */
                    public expect suspend fun suspended(): Int

                    /** A shared contract with a default argument. */
                    public expect fun defaulted(value: Int = 1): Int
                """,
            )
            kotlinFile(
                "JvmApi",
                "jvmMain",
                """
                    $DEFAULT_FACADE_NAME_FILE_ANNOTATION

                    package test

                    public actual suspend fun suspended(): Int = 1

                    public actual fun defaulted(value: Int): Int = value
                """,
            )
        }

        val result = project.build("compileKotlinJvm")
        result.assertReportedIn(KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC, "JvmApi.kt")
        result.assertReportedIn(DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS, "JvmApi.kt")
        result.assertOutputLacks("CommonApiKt")
    }

    @Test
    fun `implementation checks honor expect exemptions`() {
        val project = kmpProject {
            kotlinFile(
                "CommonApi",
                text = """
                    package test

                    import kotlinx.library.watchdog.ExemptionReason
                    import kotlinx.library.watchdog.IntentionallyDataClass
                    import kotlinx.library.watchdog.IntentionallyInlinedLogic
                    import kotlinx.library.watchdog.IntentionallyWithoutEqualsHashCodeOrToString

                    /** A platform data holder. */
                    @IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
                    public expect class Model(value: Int) {
                        /** Its value. */
                        public val value: Int
                    }

                    /** A platform state holder. */
                    @IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.API_DESIGN)
                    public expect class Stateful(value: Int) {
                        /** Its value. */
                        public val value: Int
                    }

                    /** Platform logic deliberately inlined into callers. */
                    @IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
                    public expect inline fun increment(value: Int): Int
                """,
            )
            kotlinFile(
                "JvmApi",
                "jvmMain",
                """
                    $DEFAULT_FACADE_NAME_FILE_ANNOTATION

                    package test

                    public actual data class Model public actual constructor(public actual val value: Int)

                    public actual class Stateful public actual constructor(public actual val value: Int)

                    public actual inline fun increment(value: Int): Int = value + 1
                """,
            )
        }

        project.build("compileKotlinJvm").assertNotReported(
            Diagnostic.DATA_CLASS_PUBLIC_API,
            STATEFUL_CLASS_WITHOUT_EQUALS,
            STATEFUL_CLASS_WITHOUT_HASH_CODE,
            STATEFUL_CLASS_WITHOUT_TO_STRING,
            Diagnostic.INLINE_FUNCTION_WITH_LOGIC,
        )
    }

    @Test
    fun `compilations named after a test variant aren't checked`() {
        // A local compilation has the shape of the Android names (`debugUnitTest`, `hostTest`)
        // and doesn't need an Android SDK.
        val project = jvmProject {
            explicitApi = false
            buildScript(
                """
                    kotlin {
                        compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") }
                        target.compilations.create("integrationTest") {
                            associateWith(target.compilations.getByName("main"))
                        }
                    }
                """
            )
            source(cleanMainFile)
            unexemptedOpenClassSource("integrationTest")
        }

        project.build("compileIntegrationTestKotlin").assertNoTestSourceDiagnostics()
    }

    @Test
    fun `Android test source compilations aren't checked`() = androidTestSourceCompilationsCase(AGP8)

    @Test
    fun `Android test source compilations aren't checked with built-in Kotlin`() = androidTestSourceCompilationsCase(AGP9)

    private fun androidTestSourceCompilationsCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            explicitApi = false
            kotlin("""compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") }""")
            source(cleanMainFile)
            unexemptedOpenClassSource("test")
            unexemptedOpenClassSource("androidTest", "DeviceTestOnlyHelper")
        }

        project.build("compileDebugUnitTestKotlin", "compileDebugAndroidTestKotlin").assertNoTestSourceDiagnostics()
    }

    @Test
    fun `multiplatform Android target main sources are checked`() = multiplatformAndroidTargetMainSourcesCase(LIBRARY_AGP8)

    @Test
    fun `multiplatform Android target main sources are checked on AGP 9`() = multiplatformAndroidTargetMainSourcesCase(LIBRARY_AGP9)

    @Test
    fun `legacy multiplatform Android target main sources are checked`() = multiplatformAndroidTargetMainSourcesCase(LEGACY)

    private fun multiplatformAndroidTargetMainSourcesCase(target: KmpAndroidTarget) {
        val project = kmpAndroidProject(target) {
            source(cleanMainFile)
            unexemptedOpenClassSource("androidMain", "AndroidOnlyApi")
        }

        project.buildAndFail(target.mainCompileTask)
            .assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, NULLABLE_BOOLEAN_PUBLIC_API)
    }

    @Test
    fun `multiplatform Android target test sources aren't checked`() = multiplatformAndroidTargetTestSourcesCase(LIBRARY_AGP8)

    @Test
    fun `multiplatform Android target test sources aren't checked on AGP 9`() = multiplatformAndroidTargetTestSourcesCase(LIBRARY_AGP9)

    @Test
    fun `legacy multiplatform Android target test sources aren't checked`() = multiplatformAndroidTargetTestSourcesCase(LEGACY)

    private fun multiplatformAndroidTargetTestSourcesCase(target: KmpAndroidTarget) {
        val project = kmpAndroidProject(target) {
            explicitApi = false
            buildScript(EXPLICIT_API_FLAG)
            source(cleanMainFile)
            unexemptedOpenClassSource("commonTest")
            unexemptedOpenClassSource(target.hostTestSourceSet, "HostTestOnlyHelper")
        }

        project.build(target.hostTestCompileTask).assertNoTestSourceDiagnostics()
    }

    @Test
    fun `internal annotation marker exempts across modules`() {
        val project = jvmProject {
            source(markerConsumerFile, "consumer", imports = listOf("test.lib.InternalLibApi"))
            dependencies(implementation(":lib"))
            subproject("lib") { source(markerLibraryFile, "markers", "test.lib") }
        }

        val result = project.build("build")
        result.assertWarning("`WatchedClass` doesn't have `KDoc`")
        result.assertOutputLacks("InternalOpenClass")
        result.assertOutputLacks("memberOfInternal")
        result.assertOutputLacks("InternalEnum")
        result.assertOutputLacks("INTERNAL_ENTRY")
        result.assertOutputLacks("internalFunction")
        result.assertNotReported(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, EXHAUSTIVE_PUBLIC_API)
    }

    @Test
    fun `configured internal API annotations exempt like the marker`() {
        val project = jvmProject {
            buildScript(REPORT_ALL_WARNINGS_FLAG)
            apiWatchdog { internalApiAnnotations("test.lib.ThirdPartyInternalApi") }
            source(
                configuredInternalApiConsumerFile,
                "consumer",
                imports = listOf("test.lib.ThirdPartyInternalApi", "test.lib.ThirdPartyInternalType"),
            )
            dependencies(implementation(":lib"))
            subproject("lib") { source(configuredInternalApiLibraryFile, "markers", "test.lib") }
        }

        val result = project.buildAndFail("build")
        result.assertWarning("`WatchedClass` doesn't have `KDoc`")
        result.assertError(
            "publicly exposes `test.lib.ThirdPartyInternalType`, but that type is marked as internal API " +
                    "with `@ThirdPartyInternalApi`",
        )
        result.assertOutputLacks("InternalOpenClass")
        result.assertOutputLacks("memberOfInternal")
        result.assertOutputLacks("InternalEnum")
        result.assertOutputLacks("INTERNAL_ENTRY")
        result.assertOutputLacks("internalFunction")
        result.assertNotReported(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, EXHAUSTIVE_PUBLIC_API)
    }

    @Test
    fun `warns when explicit API mode isn't enabled and the requirement is WARN`() {
        val project = jvmProject {
            explicitApi = false
            apiWatchdog { explicitApiRequirement = ExplicitApiRequirement.WARN }
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("build")
        result.assertOutputHas("doesn't enable explicit API mode")
        result.assertOutputHas("explicitApi()")
        result.assertOutputLacks("ExplicitApiRequirement.WARN")
        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, EXHAUSTIVE_PUBLIC_API, MUTABLE_COLLECTION_PUBLIC_API)
    }

    @Test
    fun `explicit API requirement is silent when explicit API mode is enabled`() {
        jvmProject().build("compileKotlin").assertOutputLacks("doesn't enable explicit API mode")
    }

    @Test
    fun `explicit API requirement sees the raw compiler flag`() {
        val project = jvmProject {
            explicitApi = false
            buildScript("""kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=strict") } }""")
        }

        project.build("compileKotlin").assertOutputLacks("doesn't enable explicit API mode")
    }

    @Test
    fun `suggests ABI validation when it isn't enabled`() {
        val result = jvmProject().build("help")

        result.assertOutputHas("but doesn't enable binary compatibility validation")
        assertEquals(
            legacyAbiValidationDsl,
            result.output.contains("abiValidation {"),
            "The warning should display the ABI validation setup for the tested KGP version",
        )
        assertEquals(
            !legacyAbiValidationDsl,
            result.output.contains("abiValidation()"),
            "The warning should display the ABI validation setup for the tested KGP version",
        )
        result.assertOutputHas("https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html")
        result.assertOutputHas("https://github.com/Kotlin/binary-compatibility-validator")
        result.assertOutputHas("suggestAbiValidation = false")
    }

    @Test
    fun `ABI validation suggestion can be disabled`() {
        val project = jvmProject {
            apiWatchdog { suggestAbiValidation = false }
        }

        project.build("help").assertOutputLacks("but doesn't enable binary compatibility validation")
    }

    @Test
    fun `ABI validation suggestion is silent when built-in ABI validation is enabled`() {
        val abiValidationCall = if (legacyAbiValidationDsl) "abiValidation { enabled.set(true) }" else "abiValidation()"
        val project = jvmProject {
            buildScript(
                """
                    kotlin {
                        @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
                        $abiValidationCall
                    }
                """
            )
        }

        project.build("help").assertOutputLacks("but doesn't enable binary compatibility validation")
    }

    @Test
    fun `ABI validation suggestion is silent when standalone validator is applied`() {
        // The buildSrc plugin registers the real plugin id, so the test doesn't fetch the real plugin.
        val project = jvmProject {
            buildScript("""apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")""")
            buildSrc {
                plugins(Plugin.javaGradle)
                buildScript(
                    """
                        gradlePlugin {
                            plugins {
                                create("fakeBcv") {
                                    id = "org.jetbrains.kotlinx.binary-compatibility-validator"
                                    implementationClass = "test.FakeBcvPlugin"
                                }
                            }
                        }
                    """
                )
                javaFile(
                    "FakeBcvPlugin",
                    """
                        package test;

                        import org.gradle.api.Plugin;
                        import org.gradle.api.Project;
                        import org.jspecify.annotations.NonNull;

                        public class FakeBcvPlugin implements Plugin<Project> {
                            @Override
                            public void apply(@NonNull Project project) {}
                        }
                    """,
                )
            }
        }

        project.build("help").assertOutputLacks("but doesn't enable binary compatibility validation")
    }

    /** [testOnlyFile] in a test source set. */
    private fun ModuleSpec.testOnlySource(sourceSet: String = "test", className: String = "TestOnlyHelper") {
        source(testOnlyFile(className), className, sourceSet = sourceSet)
    }

    /** [unexemptedTestOnlyFile] in any source set. It doesn't need an annotations dependency. */
    private fun ModuleSpec.unexemptedOpenClassSource(sourceSet: String, className: String = "TestOnlyHelper") {
        source(unexemptedTestOnlyFile(className), className, sourceSet = sourceSet, defaultImports = false)
    }

    private fun BuildResult.assertNoTestSourceDiagnostics() {
        assertNotReported(
            OPEN_API_WITHOUT_SUBCLASS_OPT_IN,
            UNDOCUMENTED_PUBLIC_API,
            NULLABLE_BOOLEAN_PUBLIC_API,
            EXEMPTION_WITHOUT_EXPLANATION,
        )
        assertOutputLacks("TestOnlyHelper")
    }

    private fun BuildResult.hasStatefulDiagnostic(className: String, missingMember: String): Boolean =
        output.lineSequence().any { "`$className`" in it && "inherits $missingMember" in it }

    /** The Poko release for a Kotlin version, from the compatibility table of the Poko README. */
    private fun pokoVersionFor(kotlin: KotlinVersion): String? = when {
        kotlin >= KotlinVersion(2, 4) -> "0.23.2"
        kotlin >= KotlinVersion(2, 3, 20) -> "0.22.1"
        kotlin >= KotlinVersion(2, 3) -> "0.21.3"
        kotlin >= KotlinVersion(2, 2, 20) -> "0.20.2"
        else -> null
    }

    private fun ideaGenerateShortcut(): String =
        if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) "⌘N" else "Alt+Insert"
}

private const val EXPLICIT_API_FLAG = """kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") } }"""

/** The compiler omits warnings when the compilation fails. This flag reports them. */
private const val REPORT_ALL_WARNINGS_FLAG =
    """kotlin { compilerOptions { freeCompilerArgs.add("-Xreport-all-warnings") } }"""

private const val DEFAULT_FACADE_NAME_FILE_ANNOTATION =
    "@file:kotlinx.library.watchdog.IntentionallyDefaultFacadeName(" +
            "reason = kotlinx.library.watchdog.ExemptionReason.API_DESIGN)"

/** A declaration that several checkers report, with a bare exemption for `EXEMPTION_WITHOUT_EXPLANATION`. */
@Language("kotlin")
private fun testOnlyFile(className: String) = """
    @IntentionallyOpen
    public open class $className {
        public fun flag(): Boolean? = null
    }
""".trimIndent()

/** [testOnlyFile] without the exemption. */
@Language("kotlin")
private fun unexemptedTestOnlyFile(className: String) = """
    public open class $className {
        public fun flag(): Boolean? = null
    }
""".trimIndent()

/** The diagnostics of [unacknowledgedFile] and [internalApiExposureFile]. */
private val singleModuleDiagnostics: List<Diagnostic> =
    Diagnostic.entries - setOf(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, EXEMPTION_WITHOUT_EXPLANATION) -
            alwaysWarningDiagnostics

/** One specimen of every configurable diagnostic. */
@Suppress("RedundantSuspendModifier", "MayBeConstant")
@Language("kotlin")
internal val unacknowledgedFile = """
    public open class UnprotectedOpenClass

    /** A base class whose subclass opt-in doesn't list any markers and so doesn't restrict anything. */
    @SubclassOptInRequired
    public open class UnmarkedOptIn

    public enum class UnmarkedEnum { A, B }

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

    /** A DSL marker with tidy targets, misapplied to an inert type position below. */
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
    public annotation class ScopedDsl

    /** A parameter type carrying a DSL marker that doesn't restrict anything. */
    public fun processTag(tag: @ScopedDsl UnprotectedOpenClass) { }

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
""".trimIndent()

@Language("kotlin")
private val unexplainedExemptionFile = """
    /** Returns a deliberately mutable result. */
    public fun unexplainedExemption(): @IntentionallyMutableCollection MutableList<Int> = mutableListOf()
""".trimIndent()

@Language("kotlin")
private val annotationIgnoreFile = """
    /** Marks classes whose value members are generated. */
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.SOURCE)
    public annotation class GeneratedValueMembers

    /** An alternate annotation that generates the same value members. */
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.SOURCE)
    public annotation class AlternateGeneratedValueMembers

    /** State whose equals, hashCode, and toString implementations are generated. */
    @GeneratedValueMembers
    public class GeneratedState(public val value: Int) {
        /** State nested in the annotated class. */
        public class NestedState(public val value: Int)
    }

    /** State whose value members are generated under the alternate annotation. */
    @AlternateGeneratedValueMembers
    public class AlternateGeneratedState(public val value: Int)

    /** State without generated value members. */
    public class RegularState(public val value: Int)
""".trimIndent()

@Language("kotlin")
private val markerLibraryFile = """
    /** Flags declarations that are public for technical reasons but aren't supported API. */
    @InternalAnnotationMarker
    @Target(
        AnnotationTarget.CLASS,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY,
    )
    public annotation class InternalLibApi
""".trimIndent()

@Language("kotlin")
private val markerConsumerFile = """
    @InternalLibApi
    public open class InternalOpenClass {
        public fun memberOfInternal() {}
    }

    @InternalLibApi
    public enum class InternalEnum { INTERNAL_ENTRY }

    @InternalLibApi
    public fun internalFunction() {}

    public class WatchedClass
""".trimIndent()

@Language("kotlin")
private val configuredInternalApiLibraryFile = """
    /** The dependency's own internal-API annotation. It doesn't reference the watchdog. */
    @Target(
        AnnotationTarget.CLASS,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY,
    )
    public annotation class ThirdPartyInternalApi

    /** A type the dependency marks as internal API. */
    @ThirdPartyInternalApi
    public class ThirdPartyInternalType
""".trimIndent()

@Language("kotlin")
private val configuredInternalApiConsumerFile = """
    @ThirdPartyInternalApi
    public open class InternalOpenClass {
        public fun memberOfInternal() {}
    }

    @ThirdPartyInternalApi
    public enum class InternalEnum { INTERNAL_ENTRY }

    @ThirdPartyInternalApi
    public fun internalFunction() {}

    /** Supported entry point leaking the dependency's internal type. */
    public object Api {
        /** Leaks an explicitly unsupported type of the dependency. */
        public fun loadInternal(): ThirdPartyInternalType = ThirdPartyInternalType()
    }

    public class WatchedClass
""".trimIndent()

@Language("kotlin")
private val internalApiExposureFile = """
    /** Marks declarations that are public only for technical reasons. */
    @InternalAnnotationMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    public annotation class InternalLibApi

    /** Another internal API with a different annotation. */
    @InternalAnnotationMarker
    @Target(AnnotationTarget.CLASS)
    public annotation class OtherInternalApi

    @InternalLibApi
    public class InternalModel

    @OtherInternalApi
    public class OtherInternalModel

    /** Supported entry points. */
    public object Api {
        /** Leaks an explicitly unsupported type. */
        public fun loadModel(): InternalModel = InternalModel()

        /** Leaks types governed by different internal API annotations. */
        public fun useModels(first: InternalModel, second: OtherInternalModel) {}
    }
""".trimIndent()

@Language("kotlin")
private val exemptedInternalApiExposureFile = """
    /** Marks declarations that are public only for technical reasons. */
    @InternalAnnotationMarker
    @Target(AnnotationTarget.CLASS)
    public annotation class InternalLibApi

    @InternalLibApi
    public open class InternalModel

    /** Published entry points that keep an unsupported type in their signatures. */
    public object Api {
        /** Shipped in 1.0 with the unsupported return type. */
        @IntentionallyExposedInternalApiForBackwardsCompatibility
        public fun loadModel(): InternalModel = InternalModel()

        /** Shipped in 1.0 with the unsupported parameter type. */
        public fun useModel(@IntentionallyExposedInternalApiForBackwardsCompatibility model: InternalModel) {}

        /** Shipped in 1.0 with the unsupported type argument. */
        public fun listModels(): List<@IntentionallyExposedInternalApiForBackwardsCompatibility InternalModel> = emptyList()
    }

    /** A published subclass of an unsupported type. */
    @IntentionallyExposedInternalApiForBackwardsCompatibility(description = "Shipped in 1.0.")
    public class Special : InternalModel() {
        /** Returns the unsupported type, with its own exemption. */
        @IntentionallyExposedInternalApiForBackwardsCompatibility
        public fun model(): InternalModel = InternalModel()
    }
""".trimIndent()

@Language("kotlin")
private val partiallyExemptedInternalApiExposureFile = """
    /** Marks declarations that are public only for technical reasons. */
    @InternalAnnotationMarker
    @Target(AnnotationTarget.CLASS)
    public annotation class InternalLibApi

    @InternalLibApi
    public open class InternalModel

    /** A published subclass whose exemption covers the supertype only. */
    @IntentionallyExposedInternalApiForBackwardsCompatibility
    public class Exempted : InternalModel() {
        /** Returns the unsupported type without an exemption of its own. */
        public fun member(): InternalModel = InternalModel()
    }
""".trimIndent()

@Language("kotlin")
private val redundantInternalApiExposureExemptionFile = """
    /** Supported entry points. */
    public object Api {
        /** Returns a supported type. */
        @IntentionallyExposedInternalApiForBackwardsCompatibility
        public fun supported(): String = ""
    }
""".trimIndent()

@Suppress("RedundantSuspendModifier", "MayBeConstant")
@Language("kotlin")
private val redundantExemptionFile = """
    /** A deliberately stable data holder. */
    @IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
    public data class StableData(
        /** The only coordinate. */
        val x: Int,
    )

    /** A regular class with a data class exemption. */
    @IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
    public class Settled

""".trimIndent()

@Language("kotlin")
private val acknowledgedFile = """
    /** A deliberately open class. */
    @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
    public open class DeliberatelyOpenClass

    /** A deliberately exhaustive enum. */
    @IntentionallyExhaustive(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
    public enum class MarkedEnum {
        /** The first entry. */
        A,

        /** The second entry. */
        B,
    }

    @IntentionallyUndocumented(description = "Self-explanatory.")
    public class DeliberatelyUndocumentedClass

    /** A deliberate function type alias. */
    @IntentionallyFunctionTypeAlias(reason = ExemptionReason.API_DESIGN)
    public typealias DeliberateCallback = (Int) -> Unit

    /** A deliberately stable data holder. */
    @IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
    public data class DeliberateData(
        /** The only coordinate. */
        val x: Int,
    )

    /** A deliberately opaque credentials holder. */
    @IntentionallyWithoutEquals(reason = ExemptionReason.API_DESIGN)
    @IntentionallyWithoutHashCode(reason = ExemptionReason.API_DESIGN)
    @IntentionallyWithoutToString(description = "The token must not leak into logs.", reason = ExemptionReason.API_DESIGN)
    public class OpaqueCredentials(
        /** The secret that must not leak into logs. */
        public val token: String,
    )

    /** A deliberately shared mutable buffer. */
    @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
    public fun sharedBuffer(): MutableList<String> = mutableListOf()

    /** Deliberately shared mutable batches, acknowledged on the type usage. */
    public fun sharedBatches(): List<@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<String>> = emptyList()

    /** A deliberately exposed coordinate pair. */
    @IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)
    public fun originPoint(): Pair<Int, Int> = 0 to 0

    /** A deliberately Boolean-switched toggle. */
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    public fun setEnabled(enabled: Boolean) {}

    /** A deliberately three-state query result. */
    @IntentionallyNullableBoolean(description = "Mirrors the wire format's optional flag.", reason = ExemptionReason.EXTERNAL_CONTRACT)
    public fun consentState(): Boolean? = null

    /** A legacy signature keeping its required parameter behind an optional one. */
    @IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
    @IntentionallyWithoutJvmOverloads(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
    public fun legacyRetry(retries: Int = 3, host: String) {}

    /** A deliberately Kotlin-only refresher left visible to Java. */
    @IntentionallyKotlinOnlyApi(description = "Coroutine-first API. Java is served by a blocking facade.", reason = ExemptionReason.IGNORE_JAVA_INTEROP)
    public suspend fun refreshAccounts(): Int = 0

    /** A holder whose companion deliberately serves Java callers through the instance. */
    public class KotlinFacingCoordinator {
        /** The companion acknowledged as companion-instance-only for Java callers. */
        @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
        public companion object {
            /** A factory reached through the Companion instance. */
            public fun instance(): KotlinFacingCoordinator = KotlinFacingCoordinator()

            /** A constant read through the Companion instance getter. */
            public val DEFAULT_LABEL: String = "coordinator"
        }
    }

    /** An overload setting the parameter order convention. */
    public fun renderShape(x: Int, y: Int) {}

    /** An overload with a deliberately different parameter order. */
    @IntentionallyInconsistentParameterOrder(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
    public fun renderShape(y: Int, x: Int, alpha: Long) {}

    /** A deliberately inlined fast path. */
    @IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
    @Suppress("NOTHING_TO_INLINE")
    public inline fun cubed(value: Int): Int = value * value * value

    /** An account handle compiled to its underlying type. */
    @JvmInline
    public value class AccountHandle(
        /** The raw handle value. */
        public val raw: String,
    )

    /** A deliberately Kotlin-only lookup. */
    @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
    public fun findAccount(handle: AccountHandle) {}

    /** A DSL marker with only effective targets. */
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
    public annotation class TidyDsl

    /** A legacy DSL marker whose wrong target set is kept for backwards compatibility. */
    @IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    public annotation class LegacyDsl
""".trimIndent()
