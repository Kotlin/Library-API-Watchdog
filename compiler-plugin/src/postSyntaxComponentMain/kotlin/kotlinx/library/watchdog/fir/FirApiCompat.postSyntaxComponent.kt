package kotlinx.library.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.analysis.getChild
import org.jetbrains.kotlin.fir.syntax.syntaxComponent
import org.jetbrains.kotlin.syntax.KotlinSyntaxTokenIds

context(c: SessionHolder)
internal actual fun KtSourceElement.childToken(token: SourceToken): KtSourceElement? {
    val syntaxId = when (token) {
        SourceToken.IDENTIFIER -> KotlinSyntaxTokenIds.IDENTIFIER
        SourceToken.CONSTRUCTOR_KEYWORD -> KotlinSyntaxTokenIds.CONSTRUCTOR_KEYWORD
        SourceToken.KDOC -> KotlinSyntaxTokenIds.DOC_COMMENT
    }
    return with(c.session.syntaxComponent) { getChild(syntaxId, depth = 1) }
}
