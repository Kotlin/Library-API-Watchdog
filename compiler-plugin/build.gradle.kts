import kotlinx.library.watchdog.conventions.ciSingleVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

plugins {
    id("devkit.compiler")
    id("library-api-watchdog.benchmark-conventions")
    id("library-api-watchdog.compiler-plugin-conventions")
    id("library-api-watchdog.space-publishing-conventions")
    id("library-api-watchdog.maven-central-publishing-conventions")
}

pluginDevKit {
    // The dev kit's IDE mapping table has no IntelliJ 2027.1 (271) builds, so `ideaVersions` adds no build for the
    // compiler of a nightly. Remove both lines when the table has them:
    // https://github.com/Kotlin/compiler-plugin-dev-kit/issues/111
    // A nightly compiler loads the newest 2.5.20 dev build of the plugin that isn't newer than the compiler.
    if (!ciSingleVersion) {
        // The compiler of nightly 271.1622. Kotlin commit 5a87800b73 moved `getChild`, `unsubstitutedScope`, and
        // `getTargetAnnotation` to other files, and 642f8df9f6 reverted the move. Dev builds 2285 to 2331 have the
        // moved layout.
        testAgainst("2.5.20-dev-2285", forIde = true)
        // The first nightly compiler after the revert. Without this build, a later nightly loads the `2285` build
        // and fails: https://github.com/Kotlin/Library-API-Watchdog/issues/58
        testAgainst("2.5.20-dev-2623", forIde = true)
    }

    pluginPackage = "kotlinx.library.watchdog"
    componentRegistrar = "kotlinx.library.watchdog.WatchdogComponentRegistrar"
    commandLineProcessor = "kotlinx.library.watchdog.WatchdogCommandLineProcessor"
    companionLibrary("library-api-watchdog-plugin-annotations")

    versionHierarchy {
        splitDev(2, 5)
        val hasWarningsForWErrorVersion = KotlinToolingVersion("2.4.0-dev-4697")
        val hasErrorsVersion = KotlinVersion(2, 3, 20)
        post(hasWarningsForWErrorVersion, "postHasWarningsForWError")
        groupVersions("preHasWarningsForWError") { it < hasWarningsForWErrorVersion && it.toKotlinVersion() >= hasErrorsVersion }
        splitDev(2, 4)

        val pathKlibVersion = KotlinToolingVersion("2.4.20-Beta2")
        pre(pathKlibVersion, "legacyKlib")
        post(pathKlibVersion, "pathKlib")
        splitDev(2, 3, 20)
        splitDev(2, 3, 0)
        split("2.2.20-Beta2")
        // `split` puts the versions that match the condition into the `pre` source set.
        // `pre`: `FirProperty.isLocal` is an extension. `post`: it's a member.
        split("FirPropertyIsLocalMember") {
            it.toKotlinVersion() < KotlinVersion(2, 3, 20) &&
                    it >= KotlinToolingVersion("2.2.20-dev-8619") &&
                    it != KotlinToolingVersion("2.2.20-Beta1")
        }
        // `pre`: `KtDiagnostic` has no `firstRange`. `post`: it has one.
        split("SourcelessDiagnostics") {
            it < KotlinToolingVersion("2.2.20-dev-6624") || it == KotlinToolingVersion("2.2.20-Beta1")
        }
        // `pre`: `getChild` takes `IElementType` values. `post`: it takes syntax ids and a `FirSyntaxComponent` context.
        split("SyntaxComponent") {
            it < KotlinToolingVersion("2.5.20-dev-5318")
        }
        // `pre`: the constructor of a diagnostic factory takes the PSI type. `post`: it doesn't.
        split("DiagnosticFactoryPsiType") {
            it < KotlinToolingVersion("2.5.20-dev-5899")
        }
        // `pre`: a SAM conversion is a `FirSamConversionExpression`. `post`: it is a `FirFunctionTypeConversionExpression`.
        split("FunctionTypeConversion") {
            it < KotlinToolingVersion("2.4.0-Beta2")
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

// The fuzz tests read these. The KDoc of RedundantExemptionFuzzTest lists them.
tasks.withType<Test>().configureEach {
    for (property in listOf("watchdog.fuzz.programs", "watchdog.fuzz.seed", "watchdog.fuzz.file")) {
        providers.gradleProperty(property).orNull?.let { systemProperty(property, it) }
    }
}
