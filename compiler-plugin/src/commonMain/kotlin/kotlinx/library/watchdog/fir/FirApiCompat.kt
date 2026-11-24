package kotlinx.library.watchdog.fir

import java.nio.file.Path
import kotlin.reflect.KClass
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory3
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory4
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass
import org.jetbrains.kotlin.name.Name

internal expect val ConeTypeParameterType.typeParameterSymbol: FirTypeParameterSymbol

internal expect fun SourceElement.klibPathCompat(): Path?

// Remove it when the dev kit has this method: https://github.com/Kotlin/compiler-plugin-dev-kit/issues/120
context(c: SessionHolder)
internal expect fun FirAnnotation.getStringArgumentCompat(name: Name): String?

internal expect val FirProperty.isLocalCompat: Boolean

internal expect val FirClassLikeDeclaration.isLocalCompat: Boolean

internal expect val KotlinJvmBinaryClass.containingLibraryPathCompat: String?

internal expect val FirStatement.functionTypeConversionArgument: FirExpression?

internal enum class SourceToken { IDENTIFIER, CONSTRUCTOR_KEYWORD, KDOC }

// The first direct child of the token type, from the light tree (CLI) or from PSI (Analysis API).
context(c: SessionHolder)
internal expect fun KtSourceElement.childToken(token: SourceToken): KtSourceElement?

/** `renderReadable` shows a function type as `Function1<String, Boolean>` before Kotlin 2.3.20. */
internal expect fun ConeKotlinType.renderReadableCompat(): String

// The constructor of a diagnostic factory has the `psiType` parameter before Kotlin 2.5.20-dev-5899.
internal expect fun diagnosticFactory0(
    name: String,
    severity: Severity,
    positioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory0

internal expect fun <A> diagnosticFactory1(
    name: String,
    severity: Severity,
    positioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory1<A>

internal expect fun <A, B> diagnosticFactory2(
    name: String,
    severity: Severity,
    positioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory2<A, B>

internal expect fun <A, B, C> diagnosticFactory3(
    name: String,
    severity: Severity,
    positioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory3<A, B, C>

internal expect fun <A, B, C, D> diagnosticFactory4(
    name: String,
    severity: Severity,
    positioningStrategy: AbstractSourceElementPositioningStrategy,
    psiType: KClass<*>,
    rendererFactory: BaseDiagnosticRendererFactory,
): KtDiagnosticFactory4<A, B, C, D>
