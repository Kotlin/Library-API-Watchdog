plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.devkit.settings)
    implementation(libs.develocity.gradle.plugin)
    implementation(libs.artifacts.validator.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("develocityConventions") {
            id = "library-api-watchdog.develocity-conventions"
            implementationClass = "kotlinx.library.watchdog.conventions.DevelocitySettingsConventionPlugin"
        }
    }
}
