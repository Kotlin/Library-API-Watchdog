package kotlinx.library.watchdog.fir

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirSamConversionExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement

internal actual val FirStatement.functionTypeConversionArgument: FirExpression?
    get() = (this as? FirSamConversionExpression)?.expression
