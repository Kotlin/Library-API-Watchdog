import kotlinx.library.watchdog.conventions.config

plugins {
    kotlin("compiler.plugin.devkit.gradle-plugin")
}

pluginDevKit { config() }
