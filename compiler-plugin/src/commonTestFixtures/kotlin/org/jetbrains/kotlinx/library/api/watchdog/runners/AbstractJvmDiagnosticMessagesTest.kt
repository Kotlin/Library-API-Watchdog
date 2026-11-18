package org.jetbrains.kotlinx.library.api.watchdog.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.DISABLE_FIR_DUMP_HANDLER
import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlinx.library.api.watchdog.handlers.WatchdogDiagnosticMessagesHandler

/** Checks the markers and the `.messages.txt` dump. The FIR dump is off. */
open class AbstractJvmDiagnosticMessagesTest : DevKitJvmDiagnosticTest({
    configureWatchdogDiagnosticTest()
    defaultDirectives {
        +DISABLE_FIR_DUMP_HANDLER
    }
    configureFirHandlersStep {
        // Before Kotlin 2.3.0, the test framework ends the step when `NoFirCompilationErrorsHandler` fails, and every
        // messages test has an error. A handler from `useHandlers` then never runs, and the test passes without a check.
        useHandlersAtFirst(::WatchdogDiagnosticMessagesHandler)
    }
})
