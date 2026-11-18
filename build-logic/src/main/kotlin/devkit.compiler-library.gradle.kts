import kotlinx.library.watchdog.conventions.config

plugins {
    kotlin("compiler.plugin.devkit.compiler-library")
}

pluginDevKit { config() }
