package kotlinx.library.watchdog.fir

import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.analysis.getChild
import org.jetbrains.kotlin.lexer.KtTokens

context(c: SessionHolder)
internal actual fun KtSourceElement.childToken(token: SourceToken): KtSourceElement? =
    getChild(elementTypes.getValue(token), depth = 1)

/**
 * The jar runs in the CLI and in `kotlin-compiler-embeddable`, which relocates `com.intellij`. A direct reference to
 * `KDocTokens.KDOC` puts the field type in the bytecode and fails to link in one of them. The generic signature
 * erases to `java.util.Map`.
 */
@Suppress("UNCHECKED_CAST")
private val elementTypes: Map<SourceToken, Set<IElementType>> = mapOf(
    SourceToken.IDENTIFIER to setOf(KtTokens.IDENTIFIER),
    SourceToken.CONSTRUCTOR_KEYWORD to setOf(KtTokens.CONSTRUCTOR_KEYWORD),
    SourceToken.KDOC to setOf(Class.forName("org.jetbrains.kotlin.kdoc.lexer.KDocTokens").getField("KDOC").get(null)),
) as Map<SourceToken, Set<IElementType>>
