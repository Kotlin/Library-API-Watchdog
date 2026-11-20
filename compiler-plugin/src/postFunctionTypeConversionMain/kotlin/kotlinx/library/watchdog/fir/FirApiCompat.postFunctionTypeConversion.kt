package kotlinx.library.watchdog.fir

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionTypeConversionExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement

internal actual val FirStatement.functionTypeConversionArgument: FirExpression?
    get() = (this as? FirFunctionTypeConversionExpression)?.expression
