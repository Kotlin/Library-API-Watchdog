package kotlinx.library.watchdog.runners

import kotlinx.library.watchdog.WatchdogComponentRegistrar
import kotlinx.library.watchdog.services.configureWatchdogOptions
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlin.config.LanguageFeature.AnnotationDefaultTargetMigrationWarning
import org.jetbrains.kotlin.config.LanguageFeature.ContextParameters
import org.jetbrains.kotlin.config.LanguageFeature.PropertyParamAnnotationDefaultTargetMode
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.LanguageSettingsDirectives.LANGUAGE

fun TestConfigurationBuilder.configureWatchdogDiagnosticTest() {
    configurePlugin(WatchdogComponentRegistrar())
    configureWatchdogOptions()
    defaultDirectives {
        LANGUAGE with listOf(
            "+$PropertyParamAnnotationDefaultTargetMode",
            "-$AnnotationDefaultTargetMigrationWarning",
            "+$ContextParameters",
        )
    }
}
