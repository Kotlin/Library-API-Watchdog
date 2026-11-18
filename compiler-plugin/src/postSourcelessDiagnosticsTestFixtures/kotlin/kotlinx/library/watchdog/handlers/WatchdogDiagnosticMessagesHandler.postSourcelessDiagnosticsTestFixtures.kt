package kotlinx.library.watchdog.handlers

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.diagnostics.KtDiagnostic

actual val KtDiagnostic.firstRangeCompat: TextRange
    get() = firstRange
