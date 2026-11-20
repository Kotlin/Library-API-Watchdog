@file:Suppress("RedundantVisibilityModifier", "FunctionName")

package kotlinx.library.watchdog

import com.autonomousapps.kit.gradle.Dependency.Companion.api
import com.autonomousapps.kit.gradle.Dependency.Companion.compileOnly
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import kotlinx.library.watchdog.AgpVersion.AGP8
import kotlinx.library.watchdog.AgpVersion.AGP9
import kotlinx.library.watchdog.Diagnostic.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY
import kotlinx.library.watchdog.KmpAndroidTarget.LEGACY
import kotlinx.library.watchdog.KmpAndroidTarget.LIBRARY_AGP8
import kotlinx.library.watchdog.KmpAndroidTarget.LIBRARY_AGP9
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.experimental.categories.Category

class PublicDependencyExposureTest {
    @Test
    fun `public type from implementation dependency is an error`() {
        val project = jvmProject {
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject()
        }

        val result = project.buildAndFail("build")
        result.assertNonTransitiveExposureReported("test.model.ExternalModel")
        result.assertOutputHas(
            "        implementation(project(\":model\"))\n        apiWatchdogExemptNonTransitive(project(\":model\"))"
        )
        result.assertError("The type alias `ExternalModels` publicly exposes")
        result.assertError("The supertype of `Consumer` publicly exposes")
        result.assertError("The function receiver `acceptModels` publicly exposes")
        result.assertError("The parameter `models` publicly exposes")
        result.assertError("The function `protectedModel` publicly exposes")
    }

    @Test
    fun `public type from API dependency is accepted`() {
        val project = jvmProject {
            consumerSource()
            dependencies(api(":model"))
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `public type from implementation dependency is accepted when intentionally exempted`() {
        val project = jvmProject {
            source(exemptedDependencyTypeFile, "Consumer", "test.consumer")
            dependencies(implementation(":model"))
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `non-transitive dependency exemption on a class covers only that class`() {
        val project = jvmProject {
            source(partiallyExemptedDependencyTypeFile, "Api", "test.consumer")
            dependencies(implementation(":model"))
            modelSubproject()
        }

        val result = project.buildAndFail("build")
        result.assertError("The function `unexemptedModel` publicly exposes")
        result.assertOutputLacks("The function `exemptedModel` publicly exposes")
        result.assertOutputLacks("The supertype of `ExemptedApi` publicly exposes")
    }

    @Test
    fun `public type from implementation dependency is an error in multiplatform projects`() {
        // The JVM target reads a jar and the JS target a klib. `--continue` runs the second
        // compilation after the first one fails.
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject()
        }

        val result = project.buildAndFail("compileKotlinJvm", "compileKotlinJs", "--continue")
        result.assertTaskOutcome(":compileKotlinJvm", FAILED)
        result.assertTaskOutcome(":compileKotlinJs", FAILED)
        result.assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun `public type from API dependency is accepted in multiplatform projects`() {
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            consumerSource()
            dependencies(api(":model"))
            modelSubproject()
        }

        project.build("compileKotlinJvm", "compileKotlinJs").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `public Java type from implementation dependency is an error`() {
        val project = jvmProject {
            source(exposesJavaDependencyTypeFile, "JavaConsumer", "test.consumer")
            dependencies(implementation(":javaModel"))
            subproject("javaModel") { javaFile("ExternalJavaModel", javaDependencyTypeFile) }
        }

        project.buildAndFail("build").assertNonTransitiveExposureReported("test.model.ExternalJavaModel")
    }

    @Test
    fun `public Java type from API dependency is accepted`() {
        val project = jvmProject {
            source(exposesJavaDependencyTypeFile, "JavaConsumer", "test.consumer")
            dependencies(api(":javaModel"))
            subproject("javaModel") { javaFile("ExternalJavaModel", javaDependencyTypeFile) }
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `public type from implementation dependency is an error in Android libraries`() =
        publicTypeFromImplementationDependencyIsAnErrorInAndroidLibrariesCase(AGP8)

    @Test
    @Category(SmokeTest::class)
    fun `public type from implementation dependency is an error in Android libraries with built-in Kotlin`() =
        publicTypeFromImplementationDependencyIsAnErrorInAndroidLibrariesCase(AGP9)

    private fun publicTypeFromImplementationDependencyIsAnErrorInAndroidLibrariesCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject(jvm = true)
        }

        project.buildAndFail("compileDebugKotlin").assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun `public type from API dependency is accepted in Android libraries`() =
        publicTypeFromApiDependencyIsAcceptedInAndroidLibrariesCase(AGP8)

    @Test
    fun `public type from API dependency is accepted in Android libraries with built-in Kotlin`() =
        publicTypeFromApiDependencyIsAcceptedInAndroidLibrariesCase(AGP9)

    private fun publicTypeFromApiDependencyIsAcceptedInAndroidLibrariesCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            consumerSource()
            dependencies(api(":model"))
            modelSubproject(jvm = true)
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `Android platform types aren't dependencies in Android libraries`() = androidPlatformTypesAreNotDependenciesCase(AGP8)

    @Test
    fun `Android platform types aren't dependencies in Android libraries with built-in Kotlin`() =
        androidPlatformTypesAreNotDependenciesCase(AGP9)

    private fun androidPlatformTypesAreNotDependenciesCase(agp: AgpVersion) {
        // `android.jar` is the boot classpath, not a Gradle dependency.
        val project = androidLibrary(agp) {
            source(exposesAndroidPlatformTypeFile, "PlatformFacing", "test.consumer")
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `public type from implementation AAR project dependency is an error in Android libraries`() =
        publicTypeFromImplementationAarProjectDependencyIsAnErrorCase(AGP8)

    @Test
    fun `public type from implementation AAR project dependency is an error in Android libraries with built-in Kotlin`() =
        publicTypeFromImplementationAarProjectDependencyIsAnErrorCase(AGP9)

    private fun publicTypeFromImplementationAarProjectDependencyIsAnErrorCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject()
        }

        project.buildAndFail("compileDebugKotlin").assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun `public type from API AAR project dependency is accepted in Android libraries`() =
        publicTypeFromApiAarProjectDependencyIsAcceptedCase(AGP8)

    @Test
    fun `public type from API AAR project dependency is accepted in Android libraries with built-in Kotlin`() =
        publicTypeFromApiAarProjectDependencyIsAcceptedCase(AGP9)

    private fun publicTypeFromApiAarProjectDependencyIsAcceptedCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            consumerSource()
            dependencies(api(":model"))
            modelSubproject()
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `exempt non-transitive AAR project dependency covers its transitive AARs in Android libraries`() =
        exemptNonTransitiveAarProjectDependencyCoversTheAarsItPullsInCase(AGP8)

    @Test
    fun `exempt non-transitive AAR project dependency covers its transitive AARs in Android libraries with built-in Kotlin`() =
        exemptNonTransitiveAarProjectDependencyCoversTheAarsItPullsInCase(AGP9)

    private fun exemptNonTransitiveAarProjectDependencyCoversTheAarsItPullsInCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            buildScript(exemptScript("project(\":facade\")"))
            consumerSource()
            dependencies(implementation(":facade"))
            subproject("facade") { dependencies(api(":model")) }
            modelSubproject()
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `public type from implementation external AAR dependency is an error in Android libraries`() =
        publicTypeFromImplementationExternalAarDependencyIsAnErrorCase(AGP8)

    @Test
    fun `public type from implementation external AAR dependency is an error in Android libraries with built-in Kotlin`() =
        publicTypeFromImplementationExternalAarDependencyIsAnErrorCase(AGP9)

    private fun publicTypeFromImplementationExternalAarDependencyIsAnErrorCase(agp: AgpVersion) {
        // A published AAR comes from the Gradle transforms cache, not from a build directory.
        val project = androidLibrary(agp) {
            source(exposesStartupInitializerFile, "StartupHooks", "test.consumer")
            dependencies(implementation(STARTUP_RUNTIME))
        }

        project.buildAndFail("compileDebugKotlin").assertNonTransitiveExposureReported("androidx.startup.Initializer")
    }

    @Test
    fun `public type from API external AAR dependency is accepted in Android libraries`() =
        publicTypeFromApiExternalAarDependencyIsAcceptedCase(AGP8)

    @Test
    fun `public type from API external AAR dependency is accepted in Android libraries with built-in Kotlin`() =
        publicTypeFromApiExternalAarDependencyIsAcceptedCase(AGP9)

    private fun publicTypeFromApiExternalAarDependencyIsAcceptedCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            source(exposesStartupInitializerFile, "StartupHooks", "test.consumer")
            dependencies(api(STARTUP_RUNTIME))
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `exempt non-transitive external AAR dependency is accepted in Android libraries`() =
        exemptNonTransitiveExternalAarDependencyIsAcceptedCase(AGP8)

    @Test
    fun `exempt non-transitive external AAR dependency is accepted in Android libraries with built-in Kotlin`() =
        exemptNonTransitiveExternalAarDependencyIsAcceptedCase(AGP9)

    private fun exemptNonTransitiveExternalAarDependencyIsAcceptedCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            buildScript(exemptScript(STARTUP_RUNTIME))
            source(exposesStartupInitializerFile, "StartupHooks", "test.consumer")
            dependencies(implementation(STARTUP_RUNTIME))
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `multiplatform library is matched through its Android variant in Android libraries`() =
        multiplatformLibraryIsMatchedThroughItsAndroidVariantCase(AGP8)

    @Test
    fun `multiplatform library is matched through its Android variant in Android libraries with built-in Kotlin`() =
        multiplatformLibraryIsMatchedThroughItsAndroidVariantCase(AGP9)

    private fun multiplatformLibraryIsMatchedThroughItsAndroidVariantCase(agp: AgpVersion) {
        // An Android compilation selects the `-android` variant of the library, a JVM one `-jvm`.
        val lifecycleHooks: AndroidLibrarySpec.() -> Unit = {
            source(exposesLifecycleTypeFile, "LifecycleHooks", "test.consumer")
            dependencies(implementation(LIFECYCLE_RUNTIME))
        }

        androidLibrary(agp, lifecycleHooks)
            .buildAndFail("compileDebugKotlin")
            .assertNonTransitiveExposureReported("androidx.lifecycle.LifecycleRegistry")

        androidLibrary(agp) { lifecycleHooks(); buildScript(exemptScript(LIFECYCLE_RUNTIME)) }
            .build("compileDebugKotlin")
            .assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `build type API dependency is transitive only for its variant in Android libraries`() =
        buildTypeApiDependencyIsTransitiveOnlyForItsVariantCase(AGP8)

    @Test
    fun `build type API dependency is transitive only for its variant in Android libraries with built-in Kotlin`() =
        buildTypeApiDependencyIsTransitiveOnlyForItsVariantCase(AGP9)

    private fun buildTypeApiDependencyIsTransitiveOnlyForItsVariantCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            consumerSource()
            dependencies("debug", api(":model"))
            dependencies("release", implementation(":model"))
            modelSubproject(jvm = true)
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
        project.buildAndFail("compileReleaseKotlin").assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun `flavor API dependency is transitive only for its flavor in Android libraries`() =
        flavorApiDependencyIsTransitiveOnlyForItsFlavorCase(AGP8)

    @Test
    fun `flavor API dependency is transitive only for its flavor in Android libraries with built-in Kotlin`() =
        flavorApiDependencyIsTransitiveOnlyForItsFlavorCase(AGP9)

    private fun flavorApiDependencyIsTransitiveOnlyForItsFlavorCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            android(
                """
                    flavorDimensions += "tier"
                    productFlavors {
                        create("free") { dimension = "tier" }
                        create("paid") { dimension = "tier" }
                    }
                """
            )
            consumerSource()
            dependencies("free", api(":model"))
            dependencies("paid", implementation(":model"))
            modelSubproject(jvm = true)
        }

        project.build("compileFreeDebugKotlin").assertNoNonTransitiveExposureReported()
        project.buildAndFail("compilePaidDebugKotlin").assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun `compileOnly dependencies can be exempted as a whole in Android libraries`() =
        compileOnlyDependenciesCanBeExemptedAsAWholeCase(AGP8)

    @Test
    fun `compileOnly dependencies can be exempted as a whole in Android libraries with built-in Kotlin`() =
        compileOnlyDependenciesCanBeExemptedAsAWholeCase(AGP9)

    private fun compileOnlyDependenciesCanBeExemptedAsAWholeCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            buildScript(EXEMPT_COMPILE_ONLY_SCRIPT)
            consumerSource()
            dependencies(compileOnly(":model"))
            modelSubproject(jvm = true)
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `public type from implementation dependency is an error in multiplatform Android targets`() =
        publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargetsCase(LIBRARY_AGP8)

    @Test
    fun `public type from implementation dependency is an error in multiplatform Android targets on AGP 9`() =
        publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargetsCase(LIBRARY_AGP9)

    @Test
    fun `public type from implementation dependency is an error in legacy multiplatform Android targets`() =
        publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargetsCase(LEGACY)

    private fun publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargetsCase(target: KmpAndroidTarget) {
        val project = kmpAndroidProject(target) {
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject()
        }

        project.buildAndFail(target.mainCompileTask).assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun `public type from API dependency is accepted in multiplatform Android targets`() =
        publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargetsCase(LIBRARY_AGP8)

    @Test
    fun `public type from API dependency is accepted in multiplatform Android targets on AGP 9`() =
        publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargetsCase(LIBRARY_AGP9)

    @Test
    fun `public type from API dependency is accepted in legacy multiplatform Android targets`() =
        publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargetsCase(LEGACY)

    private fun publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargetsCase(target: KmpAndroidTarget) {
        val project = kmpAndroidProject(target) {
            consumerSource()
            dependencies(api(":model"))
            modelSubproject()
        }

        project.build(target.mainCompileTask, "compileKotlinJvm").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `Android source set API dependency is accepted in multiplatform Android targets`() =
        androidSourceSetDependenciesCase(LIBRARY_AGP8)

    @Test
    fun `Android source set API dependency is accepted in multiplatform Android targets on AGP 9`() =
        androidSourceSetDependenciesCase(LIBRARY_AGP9)

    @Test
    fun `Android source set API dependency is accepted in legacy multiplatform Android targets`() =
        androidSourceSetDependenciesCase(LEGACY)

    private fun androidSourceSetDependenciesCase(target: KmpAndroidTarget) {
        kmpAndroidProject(target) {
            consumerSource("androidMain")
            dependencies("androidMain", api(":model"))
            modelSubproject()
        }.build(target.mainCompileTask).assertNoNonTransitiveExposureReported()

        kmpAndroidProject(target) {
            consumerSource("androidMain")
            dependencies("androidMain", implementation(":model"))
            modelSubproject()
        }.buildAndFail(target.mainCompileTask).assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun `Android platform types aren't dependencies in multiplatform Android targets`() =
        androidPlatformTypesAreNotDependenciesInMultiplatformCase(LIBRARY_AGP8)

    @Test
    fun `Android platform types aren't dependencies in multiplatform Android targets on AGP 9`() =
        androidPlatformTypesAreNotDependenciesInMultiplatformCase(LIBRARY_AGP9)

    @Test
    fun `Android platform types aren't dependencies in legacy multiplatform Android targets`() =
        androidPlatformTypesAreNotDependenciesInMultiplatformCase(LEGACY)

    private fun androidPlatformTypesAreNotDependenciesInMultiplatformCase(target: KmpAndroidTarget) {
        val project = kmpAndroidProject(target) {
            source(exposesAndroidPlatformTypeFile, "PlatformFacing", "test.consumer", "androidMain")
        }

        project.build(target.mainCompileTask).assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `external AAR dependency is checked and exemptable in multiplatform Android targets`() =
        externalAarDependencyInMultiplatformCase(LIBRARY_AGP8)

    @Test
    fun `external AAR dependency is checked and exemptable in multiplatform Android targets on AGP 9`() =
        externalAarDependencyInMultiplatformCase(LIBRARY_AGP9)

    @Test
    fun `external AAR dependency is checked and exemptable in legacy multiplatform Android targets`() =
        externalAarDependencyInMultiplatformCase(LEGACY)

    private fun externalAarDependencyInMultiplatformCase(target: KmpAndroidTarget) {
        val startupHooks: ProjectSpec.() -> Unit = {
            source(exposesStartupInitializerFile, "StartupHooks", "test.consumer", "androidMain")
            dependencies("androidMain", implementation(STARTUP_RUNTIME))
        }

        kmpAndroidProject(target, startupHooks)
            .buildAndFail(target.mainCompileTask)
            .assertNonTransitiveExposureReported("androidx.startup.Initializer")

        kmpAndroidProject(target) { startupHooks(); buildScript(exemptScript(STARTUP_RUNTIME)) }
            .build(target.mainCompileTask)
            .assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `multiplatform library is matched through its Android variant in multiplatform Android targets`() =
        multiplatformLibraryAndroidVariantInMultiplatformCase(LIBRARY_AGP8)

    @Test
    fun `multiplatform library is matched through its Android variant in multiplatform Android targets on AGP 9`() =
        multiplatformLibraryAndroidVariantInMultiplatformCase(LIBRARY_AGP9)

    @Test
    fun `multiplatform library is matched through its Android variant in legacy multiplatform Android targets`() =
        multiplatformLibraryAndroidVariantInMultiplatformCase(LEGACY)

    private fun multiplatformLibraryAndroidVariantInMultiplatformCase(target: KmpAndroidTarget) {
        val lifecycleHooks: ProjectSpec.() -> Unit = {
            source(exposesLifecycleTypeFile, "LifecycleHooks", "test.consumer")
            dependencies(implementation(LIFECYCLE_RUNTIME))
        }

        val result = kmpAndroidProject(target, lifecycleHooks)
            .buildAndFail(target.mainCompileTask, "compileKotlinJvm", "--continue")
        result.assertNonTransitiveExposureReported("androidx.lifecycle.LifecycleRegistry")
        result.assertTaskOutcome(":${target.mainCompileTask}", FAILED)
        result.assertTaskOutcome(":compileKotlinJvm", FAILED)

        kmpAndroidProject(target) { lifecycleHooks(); buildScript(exemptScript(LIFECYCLE_RUNTIME)) }
            .build(target.mainCompileTask, "compileKotlinJvm")
            .assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `build type API dependency is transitive only for its variant in legacy multiplatform Android targets`() {
        val project = kmpAndroidProject(LEGACY) {
            consumerSource("androidMain")
            dependencies("debug", api(":model"))
            dependencies("release", implementation(":model"))
            modelSubproject()
        }

        project.build("compileDebugKotlinAndroid").assertNoNonTransitiveExposureReported()
        project.buildAndFail("compileReleaseKotlinAndroid").assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun `public dependency exposure check is disabled by the module switch`() {
        val project = jvmProject {
            apiWatchdog { publicTypesMustBeTransitiveDependencies = false }
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `standard library type is accepted when the standard library is a compile-only dependency`() {
        val project = jvmProject {
            buildScript("dependencies { compileOnly(kotlin(\"stdlib\")) }")
            source(
                """
                    /** A source of lazy values. */
                    public class LazyValues {
                        /** A lazy value. */
                        public fun value(): Lazy<Int> = lazy { 1 }
                    }
                """
            )
        }

        project.build("build", "-Pkotlin.stdlib.default.dependency=false").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `public type from exempt non-transitive project dependency is accepted`() {
        val project = jvmProject {
            buildScript(exemptScript("project(\":model\")"))
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `exempt non-transitive dependency covers its transitive dependencies`() {
        val project = jvmProject {
            buildScript(exemptScript("project(\":facade\")"))
            consumerSource()
            dependencies(implementation(":facade"))
            subproject("facade") { dependencies(api(":model")) }
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `exempt non-transitive dependency leaves other dependencies checked`() {
        val project = jvmProject {
            buildScript(exemptScript("project(\":other\")"))
            consumerSource()
            dependencies(implementation(":model"), implementation(":other"))
            modelSubproject()
            subproject("other") { source(dependencyTypeFile, "OtherModel", "test.other") }
        }

        project.buildAndFail("build").assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun `exempt non-transitive dependencies extend from a whole configuration`() {
        val project = jvmProject {
            buildScript(EXEMPT_COMPILE_ONLY_SCRIPT)
            consumerSource()
            dependencies(compileOnly(":model"))
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `exempt non-transitive module dependency is resolved consistently with the compile classpath`() {
        // The `available-at` redirect of the module brings the `-jvm` artifact that holds the classes.
        val project = jvmProject {
            buildScript(exemptScript(COROUTINES))
            source(exposesCoroutinesTypeFile, "Jobs", "test.consumer")
            dependencies(implementation(COROUTINES))
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `exempt non-transitive dependency from a version catalog`() {
        val project = jvmProject {
            buildScript("dependencies { apiWatchdogExemptNonTransitive(libs.coroutines) }")
            versionCatalog(COROUTINES_CATALOG)
            source(exposesCoroutinesTypeFile, "Jobs", "test.consumer")
            dependencies(implementation(COROUTINES))
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun `public type from unexempted module dependency is an error`() {
        val project = jvmProject {
            source(exposesCoroutinesTypeFile, "Jobs", "test.consumer")
            dependencies(implementation(COROUTINES))
        }

        val result = project.buildAndFail("build")
        result.assertNonTransitiveExposureReported("kotlinx.coroutines.Job")
        // The snippet names the declared dependency, not the platform artifact that holds the type.
        result.assertOutputHas(
            "        implementation(\"$COROUTINES\")\n        apiWatchdogExemptNonTransitive(\"$COROUTINES\")"
        )
    }

    @Test
    fun `exempt non-transitive dependencies aren't on the compile classpath`() {
        val project = jvmProject {
            buildScript(exemptScript("project(\":model\")"))
            consumerSource()
            modelSubproject()
        }

        project.buildAndFail("build").assertOutputHas("Unresolved reference")
    }

    @Test
    fun `transitive dependency configurations are named after the compilation`() {
        // KGP renders the missing disambiguation classifier of the JVM target as a literal "null".
        val project = jvmProject {
            buildScript(printWatchdogConfigurationsTask)
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("compileKotlin", "printWatchdogConfigurations", "--continue")
        result.assertOutputHas(
            "WATCHDOG_CONFIGURATIONS=[apiWatchdogMainExemptNonTransitiveDependencies, " +
                    "apiWatchdogMainTransitiveDependencies]"
        )
    }

    @Test
    fun `transitive dependency configurations are named after the target in multiplatform projects`() {
        val project = kmpProject {
            buildScript(printWatchdogConfigurationsTask)
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("compileKotlinJvm", "printWatchdogConfigurations", "--continue")
        result.assertOutputHas("apiWatchdogJvmMainTransitiveDependencies")
        result.assertOutputLacks("apiWatchdogNull")
    }

    private fun BuildResult.assertNonTransitiveExposureReported(type: String) {
        assertError("publicly exposes `$type`, but the dependency with that type isn't published transitively")
    }

    private fun BuildResult.assertNoNonTransitiveExposureReported() {
        assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)
    }

    /** Exempts [dependency] without its version. */
    private fun exemptScript(dependency: String): String {
        val notation = if (dependency.startsWith("project(")) dependency else "\"${dependency.substringBeforeLast(':')}\""
        return "dependencies { apiWatchdogExemptNonTransitive($notation) }"
    }
}

private const val JVM_AND_JS_TARGETS = "jvm()\njs { nodejs() }"

private const val EXEMPT_COMPILE_ONLY_SCRIPT =
    "configurations.apiWatchdogExemptNonTransitive { extendsFrom(configurations.compileOnly.get()) }"

private const val COROUTINES = "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"

private const val COROUTINES_CATALOG = """
    [libraries]
    coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version = "1.10.2" }
"""

/** An AAR from the Google Maven repository. */
private const val STARTUP_RUNTIME = "androidx.startup:startup-runtime:1.2.0"

/**
 * A multiplatform library with an AAR variant and a JVM variant. `LifecycleRegistry` is in each
 * variant, unlike `Lifecycle` from the `lifecycle-common` jar.
 */
private const val LIFECYCLE_RUNTIME = "androidx.lifecycle:lifecycle-runtime:2.8.7"

@Language("kotlin")
private val exemptedDependencyTypeFile = """
    import test.model.ExternalContract
    import test.model.ExternalModel

    /** A public alias whose nested type argument comes from a dependency the host supplies. */
    @IntentionallyNonTransitiveDependency(
        reason = ExemptionReason.INTEROP,
        description = "The host application ships the model module.",
    )
    public typealias ExternalModels = List<ExternalModel>

    /** A documented API owner whose exemption covers its supertype and every member. */
    @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
    @IntentionallyNonTransitiveDependency(
        reason = ExemptionReason.INTEROP,
        description = "The host application ships the model module.",
    )
    public open class Consumer : ExternalContract {
        /** Returns a model the host application makes available. */
        public fun model(): ExternalModel = ExternalModel()

        /** Returns a dependency type from protected API. */
        protected fun protectedModel(): ExternalModel = ExternalModel()

        /** Uses a dependency type as both a receiver and a nested parameter type. */
        public fun ExternalModel.acceptModels(models: ExternalModels): ExternalModel = this
    }

    /** Exempts single parameters and type usages without touching the declaration. */
    public object Combiner {
        /** Counts [models] after storing [model]. */
        public fun combine(
            @IntentionallyNonTransitiveDependency(
                reason = ExemptionReason.INTEROP,
                description = "The host application ships the model module.",
            )
            model: ExternalModel,
            models: List<@IntentionallyNonTransitiveDependency(
                reason = ExemptionReason.INTEROP,
                description = "The host application ships the model module.",
            ) ExternalModel>,
        ): Int = models.size

        /** Bounds a type parameter by a type the host application supplies. */
        public fun <@IntentionallyNonTransitiveDependency(
            reason = ExemptionReason.INTEROP,
            description = "The host application ships the model module.",
        ) T : ExternalModel> describe(model: T): String = model.toString()
    }
""".trimIndent()

@Language("kotlin")
private val partiallyExemptedDependencyTypeFile = """
    import test.model.ExternalContract
    import test.model.ExternalModel

    /** The exemption on the class covers only its supertype and its members. */
    @IntentionallyNonTransitiveDependency(
        reason = ExemptionReason.INTEROP,
        description = "The host application ships the model module.",
    )
    public object ExemptedApi : ExternalContract {
        /** Returns a model the host application makes available. */
        public fun exemptedModel(): ExternalModel = ExternalModel()
    }

    /** A sibling declaration without the exemption. */
    public object UnexemptedApi {
        /** Returns a model consumers can't resolve. */
        public fun unexemptedModel(): ExternalModel = ExternalModel()
    }
""".trimIndent()

@Language("kotlin")
private val exposesCoroutinesTypeFile = """
    import kotlinx.coroutines.Job

    /** Jobs the host application manages. */
    public object Jobs {
        /** Hands out a job the host application manages. */
        public fun hostJob(): Job = Job()
    }
""".trimIndent()

/** The configurations are registered per compilation, so the task runs after the compile tasks. */
@Language("kts")
private val printWatchdogConfigurationsTask = """
    tasks.register("printWatchdogConfigurations") {
        mustRunAfter(tasks.matching { it.name.startsWith("compileKotlin") })
        doLast {
            val names = configurations.names.filter { it.endsWith("TransitiveDependencies") }.sorted()
            println("WATCHDOG_CONFIGURATIONS=" + names)
        }
    }
""".trimIndent()

@Language("kotlin")
private val exposesAndroidPlatformTypeFile = """
    import android.content.Context
    import android.os.Bundle

    /** A documented API owner whose signatures mention the Android platform. */
    public class PlatformFacing {
        /** Names the package of the platform context. */
        public fun describe(context: Context, extras: Bundle?): String = context.packageName
    }
""".trimIndent()

@Language("kotlin")
private val exposesStartupInitializerFile = """
    import androidx.startup.Initializer

    /** A documented API owner whose signature mentions a type from an AAR dependency. */
    public class StartupHooks {
        /** Returns the initializer the host runs, if any. */
        public fun initializer(): Initializer<Unit>? = null
    }
""".trimIndent()

@Language("kotlin")
private val exposesLifecycleTypeFile = """
    import androidx.lifecycle.LifecycleRegistry

    /** A documented API owner whose signature mentions a type compiled into each platform variant. */
    public class LifecycleHooks {
        /** Returns the lifecycle registry the host drives, if any. */
        public fun registry(): LifecycleRegistry? = null
    }
""".trimIndent()

@Language("java")
private val javaDependencyTypeFile = """
    package test.model;

    public final class ExternalJavaModel {}
""".trimIndent()

@Language("kotlin")
private val exposesJavaDependencyTypeFile = """
    import test.model.ExternalJavaModel

    /** A documented and otherwise settled API owner. */
    public class JavaConsumer {
        /** Returns a Java model whose class must be available to consumers. */
        public fun model(): ExternalJavaModel = ExternalJavaModel()
    }
""".trimIndent()
