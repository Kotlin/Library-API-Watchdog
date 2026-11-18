// CONTRIBUTING.md ("Build") describes the proxies and the token.
val useProxies = providers.gradleProperty("watchdog.proxies").orNull != "false"
val spaceUsername = providers.gradleProperty("space.username.watchdog").orNull
val spacePassword = providers.gradleProperty("space.password.watchdog").orNull
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
        repositories.maven("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap") {
            anonymousCredentials()
        }
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
