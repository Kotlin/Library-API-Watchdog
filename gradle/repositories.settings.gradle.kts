// CONTRIBUTING.md ("Build") describes the proxies and the token.
val useProxies = providers.gradleProperty("watchdog.proxies").orNull != "false"
fun tokenPart(property: String, variable: String): String? =
    listOf(providers.gradleProperty(property), providers.environmentVariable(variable))
        .firstNotNullOfOrNull { it.orNull?.takeIf(String::isNotEmpty) }

val spaceUsername = tokenPart("space.username.watchdog", "WATCHDOG_PROXY_USERNAME")
val spacePassword = tokenPart("space.password.watchdog", "WATCHDOG_PROXY_PASSWORD")
val hasToken = spaceUsername != null && spacePassword != null

// Space can challenge a public read on a GitHub runner. With an empty username, Gradle retries the challenge as an
// anonymous request.
fun MavenArtifactRepository.anonymousCredentials() {
    credentials {
        username = ""
        password = ""
    }
}

@Suppress("UnstableApiUsage")
listOf(pluginManagement.repositories, dependencyResolutionManagement.repositories).forEach { repositories ->
    if (!useProxies) {
        repositories.gradlePluginPortal()
        repositories.mavenCentral()
        return@forEach
    }
    for (proxy in listOf("build-deps", "build-deps-eap", "build-deps-ide")) {
        repositories.maven("https://packages.jetbrains.team/maven/p/kt-lib/$proxy") {
            if (hasToken) {
                credentials {
                    username = spaceUsername
                    password = spacePassword
                }
                // A proxy answers an anonymous request for a new version with 404, not with a challenge. Without this
                // scheme, Gradle sends the token only after a challenge.
                authentication { create<BasicAuthentication>("basic") }
            } else {
                anonymousCredentials()
            }
        }
    }
}
