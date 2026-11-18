// The settings script of every build applies this script in `pluginManagement`. The three proxies are the only Maven
// repositories of the plugins and of the dependencies. Gradle searches them in this order:
// - `build-deps`: the release versions.
// - `build-deps-eap`: the compiler-plugin-dev-kit and the Kotlin development builds.
// - `build-deps-ide`: the Kotlin builds of the IDEs.
// A proxy takes a package from one upstream repository only, so the versions of one package that are in different
// upstream repositories need different proxies.
//
// An anonymous request gets only the versions that a proxy already has. The first request of a new version needs the
// token in `space.username.watchdog` and `space.password.watchdog` of the Gradle properties of the user home.
//
// `-Pwatchdog.proxies=false` replaces the proxies with the original repositories for one run.
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
                // Without this scheme, Gradle sends the token only after a challenge.
                authentication { create<BasicAuthentication>("basic") }
            } else {
                anonymousCredentials()
            }
        }
    }
}
