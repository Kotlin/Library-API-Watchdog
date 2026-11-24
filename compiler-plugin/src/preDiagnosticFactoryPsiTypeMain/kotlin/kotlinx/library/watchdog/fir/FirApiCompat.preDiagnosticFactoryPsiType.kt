package kotlinx.library.watchdog.fir

import kotlin.reflect.KClass
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory3
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory4
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory

internal actual fun diagnosticFactory0(
    name: String,
    severity: Severity,
    positioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory0 =
    KtDiagnosticFactory0(name, severity, positioningStrategy, psiType, rendererFactory)

internal actual fun <A> diagnosticFactory1(
    name: String,
    severity: Severity,
    positioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory1<A> =
    KtDiagnosticFactory1(name, severity, positioningStrategy, psiType, rendererFactory)

internal actual fun <A, B> diagnosticFactory2(
    name: String,
    severity: Severity,
    positioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory2<A, B> =
    KtDiagnosticFactory2(name, severity, positioningStrategy, psiType, rendererFactory)

internal actual fun <A, B, C> diagnosticFactory3(
    name: String,
    severity: Severity,
    positioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory3<A, B, C> =
    KtDiagnosticFactory3(name, severity, positioningStrategy, psiType, rendererFactory)

internal actual fun <A, B, C, D> diagnosticFactory4(
    name: String,
    severity: Severity,
    positioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory4<A, B, C, D> =
    KtDiagnosticFactory4(name, severity, positioningStrategy, psiType, rendererFactory)
