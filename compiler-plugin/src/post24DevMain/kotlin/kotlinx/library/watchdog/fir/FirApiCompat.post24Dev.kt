package kotlinx.library.watchdog.fir

import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.name.Name

context(c: SessionHolder)
internal actual fun FirAnnotation.getStringArgumentCompat(name: Name): String? = getStringArgument(name)
