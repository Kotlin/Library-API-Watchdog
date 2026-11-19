import kotlinx.library.watchdog.conventions.ciSingleVersion
import org.jetbrains.kotlin.compiler.plugin.devkit.ProjectOrGav

plugins {
    id("devkit.gradle")
    id("library-api-watchdog.gradle-plugin-test-conventions")
}

// The dev kit publishes the included builds of the functional test classpath for the tests. The Gradle plugin brings
// the report aggregation plugin of the same build.
dependencies {
    functionalTestRuntimeOnly("$group:library-api-watchdog-gradle-plugin:$version")
}

pluginDevKit {
    pluginPackage = "kotlinx.library.watchdog"
    companionLibrary("library-api-watchdog-plugin-annotations")
    compilerPlugin = ProjectOrGav.LocalProject(project(":library-api-watchdog-compiler-plugin").isolated)

    // A TestKit daemon without a heap limit uses a quarter of the machine's RAM, and the 16 GB CI runners can't fit
    // several of them.
    // The `org.gradle.jvmargs` value replaces the dev kit line, so it repeats the flags of that line.
    // A metaspace limit of 512m fails some builds with `OutOfMemoryError: Metaspace`.
    // The dev kit joins the properties with commas, so a value can't contain one.
    if (ciSingleVersion) {
        functionalTestProperties.addAll(
            "org.gradle.jvmargs=-Dfile.encoding=UTF-8 -Xmx1500m -XX:+HeapDumpOnOutOfMemoryError -XX:MaxMetaspaceSize=1024m",
            "kotlin.daemon.jvmargs=-Xmx1g",
        )
    }
}
