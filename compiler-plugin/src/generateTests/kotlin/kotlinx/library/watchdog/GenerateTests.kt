package kotlinx.library.watchdog

import kotlinx.library.watchdog.runners.AbstractJvmDiagnosticMessagesTest
import kotlinx.library.watchdog.runners.AbstractJvmDiagnosticTest
import kotlinx.library.watchdog.runners.AbstractPsiJvmDiagnosticTest
import org.jetbrains.kotlin.compiler.plugin.devkit.generateDevKitTestsWithJUnit5

fun main() = generateDevKitTestsWithJUnit5 {
    testClass<AbstractJvmDiagnosticTest> {
        model("diagnostics")
    }
    testClass<AbstractPsiJvmDiagnosticTest> {
        model("diagnostics")
    }
    testClass<AbstractJvmDiagnosticMessagesTest> {
        model("messages")
    }
}
