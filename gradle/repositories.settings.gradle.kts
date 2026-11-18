// The settings script of every build applies this script in `pluginManagement`. The two proxies are the only Maven
// repositories of the plugins and of the dependencies. Gradle searches `build-deps` first. `build-deps-eap` has only the
// development versions that the upstream repositories of `build-deps` don't have.
//
// An anonymous request gets only the versions that a proxy already has. The first request of a new version needs the
// token in `space.username.watchdog` and `space.password.watchdog` of the Gradle properties of the user home.
val spaceUsername = providers.gradleProperty("space.username.watchdog").orNull
val spacePassword = providers.gradleProperty("space.password.watchdog").orNull
val hasToken = spaceUsername != null && spacePassword != null

@Suppress("UnstableApiUsage")
listOf(pluginManagement.repositories, dependencyResolutionManagement.repositories).forEach { repositories ->
    for (proxy in listOf("build-deps", "build-deps-eap")) {
        repositories.maven("https://packages.jetbrains.team/maven/p/kt-lib/$proxy") {
            if (hasToken) {
                credentials {
                    username = spaceUsername
                    password = spacePassword
                }
                // Without this scheme, Gradle sends the token only after a challenge.
                authentication { create<BasicAuthentication>("basic") }
            } else {
                // Space can challenge a public read on a GitHub runner. With an empty username, Gradle
                // retries the challenge as an anonymous request.
                credentials {
                    username = ""
                    password = ""
                }
            }
        }
    }
}
