package kotlinx.library.watchdog.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest

open class AbstractJvmDiagnosticTest : DevKitJvmDiagnosticTest({
    configureWatchdogDiagnosticTest()
})
