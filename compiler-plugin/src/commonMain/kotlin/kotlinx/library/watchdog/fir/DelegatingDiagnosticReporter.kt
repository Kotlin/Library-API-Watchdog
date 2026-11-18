package kotlinx.library.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic

internal expect fun delegatingDiagnosticReporter(
    delegate: DiagnosticReporter,
    onReport: (diagnostic: KtDiagnostic?, context: DiagnosticContext) -> Unit,
): DiagnosticReporter
