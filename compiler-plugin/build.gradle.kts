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
    // The IntelliJ 2027.1 nightly (271.1622) bundles a compiler from the window between Kotlin
    // commit 5a87800b73, which moved `getChild` and `unsubstitutedScope` to other files, and its
    // revert 642f8df9f6. Public dev builds 2285 to 2331 share that layout. The dev kit's IDE
    // mapping table doesn't have the 271 builds yet. Remove the line when it has them:
    // https://github.com/Kotlin/compiler-plugin-dev-kit/issues/111
    // A nightly with a compiler after the revert loads this build too and fails:
    // https://github.com/Kotlin/Library-API-Watchdog/issues/58
    if (!ciSingleVersion) {
        testAgainst("2.5.20-dev-2285", forIde = true)
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
