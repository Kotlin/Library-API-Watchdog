package kotlinx.library.watchdog.fixer

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective

internal class ImportResolver(private val ktFile: KtFile) {
    private val samePackage = ktFile.packageFqName.asString() == ExemptionRegistry.ANNOTATIONS_PACKAGE

    private val wildcardImports = ktFile.importDirectives.filter { it.isAllUnder }

    /** Another wildcard import can bring the same short name, so only a sole wildcard suffices. */
    private val wildcardImportSuffices =
        wildcardImports.any { it.importedFqName?.asString() == ExemptionRegistry.ANNOTATIONS_PACKAGE } &&
                wildcardImports.all { it.importedFqName?.asString() == ExemptionRegistry.ANNOTATIONS_PACKAGE }

    private val visibleNameByFqName: Map<String, String> = buildMap {
        ktFile.importDirectives.forEach { directive ->
            if (directive.isAllUnder) return@forEach
            val fqName = directive.importedFqName?.asString() ?: return@forEach
            put(fqName, directive.aliasName ?: fqName.substringAfterLast('.'))
        }
    }

    private val takenShortNames: Set<String> = visibleNameByFqName.values.toSet()

    private val importsToAdd = sortedSetOf<String>()

    private val referencedFqNames = hashSetOf<String>()

    fun references(shortName: String): Boolean =
        "${ExemptionRegistry.ANNOTATIONS_PACKAGE}.$shortName" in referencedFqNames

    fun reference(shortName: String): String {
        val fqName = "${ExemptionRegistry.ANNOTATIONS_PACKAGE}.$shortName"
        referencedFqNames += fqName
        visibleNameByFqName[fqName]?.let { return it }
        if (samePackage || wildcardImportSuffices) return shortName
        if (fqName in importsToAdd) return shortName
        if (shortName in takenShortNames) return fqName
        importsToAdd += fqName
        return shortName
    }

    fun importEdits(unusedImports: List<KtImportDirective>, text: String): List<TextEdit> {
        val edits = mutableListOf<TextEdit>()
        val unused = unusedImports.sortedBy { it.textRange.startOffset }
        val remaining = ktFile.importDirectives.filter { it !in unused }
        if (unused.isNotEmpty() && remaining.isEmpty()) {
            val start = unused.first().textRange.startOffset
            var end = text.lineEndAfter(unused.last().textRange.endOffset)
            if (importsToAdd.isEmpty() && end < text.length && text[end] == '\n') end++
            edits += TextEdit(start, end, "")
        } else {
            unused.mapTo(edits) { TextEdit(it.textRange.startOffset, text.lineEndAfter(it.textRange.endOffset), "") }
        }

        if (importsToAdd.isNotEmpty()) {
            val importLines = importsToAdd.joinToString("\n") { "import $it" }
            edits += when {
                remaining.isNotEmpty() -> TextEdit(remaining.last().endWithTrailingComment(), "\n$importLines", EditOrder.IMPORT)
                // The insertion at the offset of the removal goes in front of it.
                unused.isNotEmpty() -> TextEdit(unused.first().textRange.startOffset, "$importLines\n", EditOrder.IMPORT)
                else -> {
                    val packageDirective = ktFile.packageDirective?.takeIf { it.textLength > 0 }
                    if (packageDirective != null) {
                        TextEdit(packageDirective.endWithTrailingComment(), "\n\n$importLines", EditOrder.IMPORT)
                    } else {
                        val offset = text.lineStartIfIndented(ktFile.declarations.firstOrNull()?.textRange?.startOffset ?: 0)
                        TextEdit(offset, "$importLines\n\n", EditOrder.IMPORT)
                    }
                }
            }
        }
        return edits
    }

    private fun PsiElement.endWithTrailingComment(): Int {
        var end = textRange.endOffset
        var next = PsiTreeUtil.nextLeaf(this)
        // An empty import list follows the package directive.
        while (next != null && (next.textLength == 0 || (next is PsiWhiteSpace || next is PsiComment) && '\n' !in next.text)) {
            if (next is PsiComment) end = next.textRange.endOffset
            next = PsiTreeUtil.nextLeaf(next)
        }
        return end
    }

    private fun String.lineEndAfter(offset: Int): Int = indexOf('\n', offset).let { if (it < 0) length else it + 1 }
}
