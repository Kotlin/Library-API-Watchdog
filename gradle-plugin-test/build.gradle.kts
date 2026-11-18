import org.jetbrains.kotlin.compiler.plugin.devkit.ProjectOrGav

plugins {
    id("devkit.gradle")
    id("library-api-watchdog.gradle-plugin-test-conventions")
}

// The dev kit publishes the included builds of the functional test classpath for the tests. The Gradle plugin brings
// the report aggregation plugin of the same build.
dependencies {
    functionalTestRuntimeOnly("$group:kotlin-library-api-watchdog-gradle-plugin:$version")
}

pluginDevKit {
    pluginPackage = "org.jetbrains.kotlinx.library.api.watchdog"
    companionLibrary("kotlin-library-api-watchdog-plugin-annotations")
    compilerPlugin = ProjectOrGav.LocalProject(project(":kotlin-library-api-watchdog-compiler-plugin").isolated)

    // Every TestKit build gets these Gradle properties. A TestKit daemon otherwise uses the JVM default heap of a
    // quarter of the machine's RAM. The 16 GB CI runners cannot fit several of those heaps next to the other JVMs
    // of a test lane.
    // The `org.gradle.jvmargs` value replaces the dev kit line with the same key, so it repeats that line's flags.
    // The dev kit joins the properties with commas, so a value cannot contain one.
    functionalTestProperties.addAll(
        "org.gradle.jvmargs=-Dfile.encoding=UTF-8 -Xmx1500m -XX:+HeapDumpOnOutOfMemoryError -XX:MaxMetaspaceSize=512m",
        "kotlin.daemon.jvmargs=-Xmx1g",
    )
}
