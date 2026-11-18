plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.devkit.settings)
    implementation(libs.devkit.plugins)
    // Each build applies the Kotlin Gradle plugin of its own version. The plugin-annotations build uses an older one.
    compileOnly(libs.kotlin.gradle.plugin)
    // The dev kit applies the plugin and puts it on the runtime classpath.
    compileOnly(libs.buildconfig.gradle.plugin)
    implementation(libs.maven.publish.gradle.plugin)
    implementation(libs.plugin.publish.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)
    testImplementation(kotlin("test-junit"))
}

gradlePlugin {
    plugins {
        register("benchmarkConventions") {
            id = "library-api-watchdog.benchmark-conventions"
            implementationClass = "kotlinx.library.watchdog.conventions.BenchmarkConventionPlugin"
        }
        register("compilerPluginConventions") {
            id = "library-api-watchdog.compiler-plugin-conventions"
            implementationClass = "kotlinx.library.watchdog.conventions.CompilerPluginConventionPlugin"
        }
        register("dokkaConventions") {
            id = "library-api-watchdog.dokka-conventions"
            implementationClass = "kotlinx.library.watchdog.conventions.DokkaConventionPlugin"
        }
        register("exemptsFixerConventions") {
            id = "library-api-watchdog.exempts-fixer-conventions"
            implementationClass = "kotlinx.library.watchdog.conventions.ExemptsFixerConventionPlugin"
        }
        register("gradlePluginConventions") {
            id = "library-api-watchdog.gradle-plugin-conventions"
            implementationClass = "kotlinx.library.watchdog.conventions.GradlePluginConventionPlugin"
        }
        register("gradlePluginTestConventions") {
            id = "library-api-watchdog.gradle-plugin-test-conventions"
            implementationClass =
                "kotlinx.library.watchdog.conventions.GradlePluginTestConventionPlugin"
        }
        register("jvmTargetConventions") {
            id = "library-api-watchdog.jvm-target-conventions"
            implementationClass = "kotlinx.library.watchdog.conventions.JvmTargetConventionPlugin"
        }
        register("rootConventions") {
            id = "library-api-watchdog.root-conventions"
            implementationClass = "kotlinx.library.watchdog.conventions.RootConventionPlugin"
        }
        register("spacePublishingConventions") {
            id = "library-api-watchdog.space-publishing-conventions"
            implementationClass = "kotlinx.library.watchdog.conventions.SpacePublishingConventionPlugin"
        }
        register("mavenCentralPublishingConventions") {
            id = "library-api-watchdog.maven-central-publishing-conventions"
            implementationClass =
                "kotlinx.library.watchdog.conventions.MavenCentralPublishingConventionPlugin"
        }
        register("pluginPortalPublishingConventions") {
            id = "library-api-watchdog.plugin-portal-publishing-conventions"
            implementationClass =
                "kotlinx.library.watchdog.conventions.PluginPortalPublishingConventionPlugin"
        }
    }
}
