package kotlinx.library.watchdog

import org.gradle.api.GradleException
import org.gradle.util.GradleVersion

/** The first version with `ProjectDependency.getPath`. */
private val MINIMUM_GRADLE_VERSION = GradleVersion.version("8.11")

@InternalWatchdogApi
public fun requireMinimumGradleVersion(pluginName: String) {
    val current = GradleVersion.current()
    if (current.baseVersion >= MINIMUM_GRADLE_VERSION) return
    throw GradleException(
        "$pluginName needs Gradle ${MINIMUM_GRADLE_VERSION.version} or later, and this build runs on Gradle " +
                "${current.version}. Update the Gradle version of the build.",
    )
}
