package kotlinx.library.watchdog.fixer

import java.nio.file.Paths
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFileAnnotationList
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal class FileFixResult(
    /** Null when no fix applies. */
    val newText: String?,
    val applied: List<AppliedFix>,
    val skipped: List<SkippedDiagnostic>,
    val warnings: List<WarningDiagnostic> = emptyList(),
    internal val edits: List<TextEdit> = emptyList(),
)

/** Adds the fix of [ExemptionRegistry] for each diagnostic recorded in one file. */
internal class ExemptionFixer(private val parser: KotlinFileParser) {

    /**
     * @param fixes whether the fixer fixes the named diagnostic. A diagnostic it doesn't fix goes
     *   into [FileFixResult.warnings].
     * @param skipReason when it isn't null, the fixer doesn't edit the file. Each diagnostic that it
     *   fixes goes into [FileFixResult.skipped] with this reason.
     */
    fun fix(
        filePath: String,
        originalText: String,
        diagnostics: List<RecordedDiagnostic>,
        rewriteLocations: Boolean = true,
        fixes: (String) -> Boolean = { true },
        skipReason: String? = null,
    ): FileFixResult {
        val mapping = LineEndingMapping.of(originalText)
        val text = mapping.normalizedText
        val ktFile = parser.parse(Paths.get(filePath).fileName.toString(), text)

        val skipped = mutableListOf<PlannedSkip>()
        val warnings = mutableListOf<PlannedWarning>()
        val resolved = mutableListOf<ResolvedFix>()
        val order = compareBy<RecordedDiagnostic>({ it.startOffset }, { it.name })

        for (diagnostic in diagnostics.sortedWith(order)) {
            val name = diagnostic.name
            val startOffset = diagnostic.startOffset.coerceAtLeast(0)

            fun skip(reason: String) {
                skipped += PlannedSkip(diagnostic, startOffset, reason)
            }

            if (!fixes(name)) {
                warnings += PlannedWarning(diagnostic, startOffset)
                continue
            }
            if (skipReason != null) {
                skip(skipReason)
                continue
            }

            val fix = when (val resolution = ExemptionRegistry.resolutionFor(name)) {
                is FixResolution.Unfixable -> {
                    skip(resolution.reason)
                    continue
                }
                is FixResolution.Fixable -> resolution.fix
            }

            if (startOffset !in text.indices) {
                skip("The recorded source position is outside the file. The file changed after the compilation")
                continue
            }

            val element = ktFile.findElementAt(startOffset) ?: run {
                skip("No source element found at the recorded position")
                continue
            }
            val target = resolveTarget(element, fix, ktFile) ?: run {
                skip("No declaration that accepts ${fix.describe()} encloses the reported position")
                continue
            }
            resolved += ResolvedFix(diagnostic, target, fix)
        }

        // An annotation that this run removes doesn't make a fix unnecessary.
        val removedEntries = resolved.mapNotNullTo(hashSetOf()) { (it.target as? FixTarget.RemovedAnnotation)?.entry }
        val plannedFixes = mutableMapOf<FixTarget, MutableMap<String, PlannedFix>>()
        for ((diagnostic, target, fix) in resolved) {
            if (target.alreadyHas(fix, removedEntries)) {
                skipped += PlannedSkip(diagnostic, diagnostic.startOffset.coerceAtLeast(0), "The target already has ${fix.describe()}")
                continue
            }
            plannedFixes.getOrPut(target) { mutableMapOf() }
                .getOrPut(fix.id) { PlannedFix(fix) }
                .reports.getOrPut(diagnostic.name) { diagnostic }
        }
        skipped.sortWith(compareBy(order) { it.diagnostic })
        plannedFixes.values.forEach { it.collapseCombinedExemptions() }

        if (plannedFixes.isEmpty()) {
            val locations = RewrittenLocations(text, text, emptyList())
            return FileFixResult(
                newText = null,
                applied = emptyList(),
                skipped = skipped.map { it.toDiagnostic(filePath, locations) },
                warnings = warnings.map { it.toDiagnostic(filePath, locations) },
            )
        }

        val imports = ImportResolver(ktFile)
        val edits = mutableListOf<TextEdit>()
        val applied = mutableListOf<PlannedFix>()
        val annotatedConstructors = plannedFixes.keys.mapNotNullTo(hashSetOf()) {
            (it as? FixTarget.BeforeDeclaration)?.declaration as? KtPrimaryConstructor
        }
        for ((target, fixesById) in plannedFixes) {
            val fixes = fixesById.values.sortedWith(compareBy({ it.fix !is PlaceholderKdocFix }, { it.fix.id }))
            edits += if (target is FixTarget.RemovedAnnotation) {
                target.entry.removal(text, removedEntries, annotatedConstructors)
            } else {
                target.render(fixes, text, imports, removedEntries)
            }
            applied += fixes
        }
        edits += imports.importEdits(unusedImports(ktFile, plannedFixes.keys, imports), text)

        val rawEdits = edits.map {
            TextEdit(
                mapping.toRawOffset(it.offset),
                mapping.toRawOffset(it.endOffset),
                it.text.replace("\n", mapping.newline),
                it.order,
            )
        }
        val newText = applyEdits(originalText, rawEdits)
        val locations = if (rewriteLocations) {
            RewrittenLocations(text, applyEdits(text, edits), edits)
        } else {
            RewrittenLocations(text, text, emptyList())
        }
        return FileFixResult(
            newText = newText,
            applied = applied.flatMap { planned ->
                planned.reports.values.map { recorded ->
                    AppliedFix(
                        recorded.name,
                        when (planned.fix) {
                            is ExemptionFix -> planned.fix.annotationShortName
                            RemoveAnnotationFix -> recorded.messageParameters.firstOrNull()
                            PlaceholderKdocFix -> null
                        },
                        filePath,
                        locations.lineAt(recorded.startOffset),
                        recorded.messageParameters,
                        locations.rangeOf(recorded),
                    )
                }
            },
            skipped = skipped.map { it.toDiagnostic(filePath, locations) },
            warnings = warnings.map { it.toDiagnostic(filePath, locations) },
            edits = rawEdits,
        )
    }
}

internal class PlannedFix(val fix: Fix) {
    val reports: MutableMap<String, RecordedDiagnostic> = linkedMapOf()
}

private data class ResolvedFix(val diagnostic: RecordedDiagnostic, val target: FixTarget, val fix: Fix)

private fun Fix.describe(): String = when (this) {
    is ExemptionFix -> "@$annotationShortName"
    PlaceholderKdocFix -> "a KDoc"
    RemoveAnnotationFix -> "a removal"
}

private fun unusedImports(
    ktFile: KtFile,
    targets: Set<FixTarget>,
    imports: ImportResolver,
): List<KtImportDirective> {
    val removedEntries = targets.filterIsInstance<FixTarget.RemovedAnnotation>().map { it.entry }
    if (removedEntries.isEmpty()) return emptyList()

    val remainingNames = ktFile.collectDescendantsOfType<KtSimpleNameExpression>()
        .filter { reference ->
            PsiTreeUtil.getParentOfType(reference, KtImportDirective::class.java) == null &&
                    removedEntries.none { PsiTreeUtil.isAncestor(it, reference, false) }
        }
        .mapTo(hashSetOf()) { it.getReferencedName() }
    ktFile.collectDescendantsOfType<KDocName>().mapTo(remainingNames) { it.text.substringBefore('.') }
    // The entry references its annotation class and, in the `reason` argument, `ExemptionReason`.
    val removedNames = removedEntries.flatMapTo(linkedSetOf()) { entry ->
        entry.collectDescendantsOfType<KtSimpleNameExpression>().map { it.getReferencedName() }
    }
    return removedNames
        .filter { name -> name !in remainingNames && !imports.references(name) }
        .mapNotNull { name ->
            ktFile.importDirectives.firstOrNull {
                it.aliasName == null && it.importedFqName?.asString() == "${ExemptionRegistry.ANNOTATIONS_PACKAGE}.$name"
            }
        }
}

private fun MutableMap<String, PlannedFix>.collapseCombinedExemptions() {
    for ((combined, parts) in ExemptionRegistry.combinedExemptions) {
        if (!keys.containsAll(parts)) continue
        val individual = parts.map { getValue(it) }
        val merged = PlannedFix(
            ExemptionFix(combined, individual.first().fix.targetStrategy, hasReasonParameter = true)
        )
        individual.forEach { planned ->
            planned.reports.forEach { (name, diagnostic) -> merged.reports.getOrPut(name) { diagnostic } }
        }
        parts.forEach { remove(it) }
        put(combined, merged)
    }
}

private class PlannedSkip(val diagnostic: RecordedDiagnostic, val sourceOffset: Int, val reason: String) {
    fun toDiagnostic(filePath: String, locations: RewrittenLocations) =
        SkippedDiagnostic(
            diagnostic.name, filePath, locations.lineAt(sourceOffset), reason, diagnostic.messageParameters, locations.rangeOf(diagnostic),
        )
}

private class PlannedWarning(val diagnostic: RecordedDiagnostic, val sourceOffset: Int) {
    fun toDiagnostic(filePath: String, locations: RewrittenLocations) =
        WarningDiagnostic(
            diagnostic.name, filePath, locations.lineAt(sourceOffset), diagnostic.messageParameters, locations.rangeOf(diagnostic),
        )
}

private class RewrittenLocations(
    private val originalText: String,
    private val rewrittenText: String,
    private val edits: List<TextEdit>,
) {
    fun lineAt(sourceOffset: Int): Int =
        rewrittenText.take(rewrittenOffsetAt(sourceOffset)).count { it == '\n' } + 1

    fun rangeOf(diagnostic: RecordedDiagnostic): DiagnosticSourceRange? {
        val start = diagnostic.reportedStartOffset ?: return null
        val end = diagnostic.reportedEndOffset ?: return null
        if (start < 0 || end <= start || end > originalText.length) return null
        if (edits.any { it.offset < it.endOffset && it.offset < end && it.endOffset > start }) return null
        val rewrittenStart = rewrittenOffsetAt(start)
        val rewrittenEnd = rewrittenOffsetAt(end, includeInsertionsAtOffset = false)
        return DiagnosticSourceRange(
            rewrittenText.take(rewrittenStart).count { it == '\n' } + 1,
            rewrittenStart - rewrittenText.lastIndexOf('\n', rewrittenStart - 1),
            rewrittenText.take(rewrittenEnd).count { it == '\n' } + 1,
            rewrittenEnd - rewrittenText.lastIndexOf('\n', rewrittenEnd - 1),
        )
    }

    private fun rewrittenOffsetAt(sourceOffset: Int, includeInsertionsAtOffset: Boolean = true): Int {
        val boundedOffset = sourceOffset.coerceIn(0, originalText.length)
        val rewrittenOffset = boundedOffset + edits.sumOf { edit ->
            // An edit that ends at the offset is in front of the token. An edit that covers the
            // offset replaced the token on the same line, so it doesn't move the line.
            if (edit.endOffset < boundedOffset || edit.endOffset == boundedOffset &&
                (includeInsertionsAtOffset || edit.offset < edit.endOffset)
            ) edit.lengthDelta else 0
        }
        return rewrittenOffset.coerceIn(0, rewrittenText.length)
    }
}

/** The order of the insertions at one offset. */
internal enum class EditOrder { FILE_ANNOTATION, IMPORT, DECLARATION }

/** [text] replaces the range `[offset, endOffset)`. Edits never overlap. */
internal class TextEdit(val offset: Int, val endOffset: Int, val text: String, val order: EditOrder = EditOrder.DECLARATION) {
    constructor(offset: Int, text: String, order: EditOrder = EditOrder.DECLARATION) : this(offset, offset, text, order)

    val lengthDelta: Int get() = text.length - (endOffset - offset)
}

/**
 * Applies the edits back to front. At one offset the widest edit goes first, so an insertion
 * goes in front of a replacement and not inside it.
 */
internal fun applyEdits(text: String, edits: List<TextEdit>): String {
    val builder = StringBuilder(text)
    var appliedFrom = text.length
    edits
        .sortedWith(
            compareByDescending<TextEdit> { it.offset }
                .thenByDescending { it.endOffset }
                .thenByDescending { it.order }
                .thenByDescending { it.text }
        )
        .forEach {
            check(it.endOffset <= appliedFrom) { "The edits at the offsets ${it.offset} and $appliedFrom overlap" }
            builder.replace(it.offset, it.endOffset, it.text)
            appliedFrom = it.offset
        }
    return builder.toString()
}

internal sealed interface FixTarget {
    fun alreadyAnnotated(annotationShortName: String, removedEntries: Set<KtAnnotationEntry>): Boolean

    fun alreadyDocumented(): Boolean = false

    fun alreadyHas(fix: Fix, removedEntries: Set<KtAnnotationEntry>): Boolean = when (fix) {
        is ExemptionFix -> alreadyAnnotated(fix.annotationShortName, removedEntries)
        PlaceholderKdocFix -> alreadyDocumented()
        RemoveAnnotationFix -> false
    }

    /** Fixes that share an insertion point go into one edit, so [applyEdits] can't reorder them. */
    fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver, removedEntries: Set<KtAnnotationEntry>): List<TextEdit>

    data class BeforeDeclaration(val declaration: KtDeclaration) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String, removedEntries: Set<KtAnnotationEntry>) =
            declaration.hasExemptionNamed(annotationShortName, removedEntries)

        override fun alreadyDocumented() = declaration.docComment != null

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver, removedEntries: Set<KtAnnotationEntry>): List<TextEdit> {
            var anchor: PsiElement = declaration
            var child = declaration.firstChild
            while (child is PsiComment || child is PsiWhiteSpace) {
                child = child.nextSibling
            }
            if (child != null) {
                anchor = child
            }

            val anchorOffset = anchor.textRange.startOffset
            val lineStart = text.lastIndexOf('\n', anchorOffset - 1) + 1
            val indent = text.substring(lineStart, anchorOffset)
            return if (indent.isBlank()) {
                listOf(TextEdit(lineStart, fixes.joinToString("") { indent + it.insertionText(imports) + "\n" }))
            } else {
                listOf(TextEdit(anchorOffset, fixes.joinToString("") { it.insertionText(imports) + " " }))
            }
        }
    }

    data class InlineBefore(val element: KtElement) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String, removedEntries: Set<KtAnnotationEntry>) =
            (element as? KtDeclaration)?.hasExemptionNamed(annotationShortName, removedEntries) ?: false

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver, removedEntries: Set<KtAnnotationEntry>): List<TextEdit> {
            val offset = (element.firstKeptChild(removedEntries) ?: element).textRange.startOffset
            return listOf(TextEdit(offset, fixes.joinToString("") { it.insertionText(imports) + " " }))
        }
    }

    /** The exemption replaces the reported annotation. */
    data class ReplacedAnnotation(val entry: KtAnnotationEntry) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String, removedEntries: Set<KtAnnotationEntry>) = false

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver, removedEntries: Set<KtAnnotationEntry>): List<TextEdit> {
            val owner = entry.annotatedDeclaration()
            val missing = fixes.filterNot { owner?.hasExemptionNamed(it.exemption.annotationShortName, removedEntries) == true }
            if (missing.isEmpty()) {
                return entry.removal(text, setOf(entry))
            }

            val replacement = missing.joinToString(" ") {
                if (entry.annotationList == null) it.annotationText(imports) else it.annotationCall(imports)
            }
            return listOf(TextEdit(entry.textRange.startOffset, entry.textRange.endOffset, replacement))
        }
    }

    /** The apply loop calls `removal` with every removed entry instead of `render`, so a fully removed list takes its brackets too. */
    data class RemovedAnnotation(val entry: KtAnnotationEntry) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String, removedEntries: Set<KtAnnotationEntry>) = false

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver, removedEntries: Set<KtAnnotationEntry>): List<TextEdit> =
            entry.removal(text, setOf(entry))
    }

    data class ImplicitPrimaryConstructor(val constructor: KtPrimaryConstructor) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String, removedEntries: Set<KtAnnotationEntry>) =
            constructor.hasExemptionNamed(annotationShortName, removedEntries)

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver, removedEntries: Set<KtAnnotationEntry>): List<TextEdit> {
            val annotations = fixes.joinToString(" ") { it.insertionText(imports) }
            return listOf(TextEdit(constructor.textRange.startOffset, " $annotations constructor"))
        }
    }

    data class WholeFile(val file: KtFile) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String, removedEntries: Set<KtAnnotationEntry>) =
            file.fileAnnotationList?.hasExemptionNamed(annotationShortName, removedEntries) ?: false

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver, removedEntries: Set<KtAnnotationEntry>): List<TextEdit> {
            val annotationList = file.fileAnnotationList
            if (annotationList != null) {
                return fixes.map {
                    TextEdit(
                        annotationList.textRange.startOffset,
                        it.annotationText(imports, useSite = "file:") + "\n",
                        EditOrder.FILE_ANNOTATION,
                    )
                }
            }
            val anchorOffset = file.packageDirective?.takeIf { it.textLength > 0 }?.textRange?.startOffset
                ?: run {
                    // A file without a package has an empty package directive in front of its header comment.
                    var child = file.firstChild
                    while (child != null && (child is PsiComment || child is PsiWhiteSpace || child.textLength == 0)) {
                        child = child.nextSibling
                    }
                    text.lineStartIfIndented(child?.textRange?.startOffset ?: 0)
                }

            return fixes.map {
                TextEdit(anchorOffset, it.annotationText(imports, useSite = "file:") + "\n\n", EditOrder.FILE_ANNOTATION)
            }
        }
    }
}

private fun PsiElement.firstKeptChild(removedEntries: Set<KtAnnotationEntry>): PsiElement? {
    var child = firstChild
    while (child != null) {
        val kept = when (child) {
            is PsiComment, is PsiWhiteSpace -> null
            is KtAnnotationEntry -> child.takeUnless { it in removedEntries }
            is KtAnnotation -> child.takeUnless { list -> list.entries.all { it in removedEntries } }
            is KtModifierList -> child.firstKeptChild(removedEntries)
            else -> child
        }
        if (kept != null) return kept
        child = child.nextSibling
    }
    return null
}

private val KtAnnotationEntry.annotationList: KtAnnotation? get() = parent as? KtAnnotation

/**
 * [removedEntries] holds every entry that this run removes, this one included.
 * [annotatedConstructors] holds the constructors that get an annotation in this run.
 */
private fun KtAnnotationEntry.removal(
    text: String,
    removedEntries: Set<KtAnnotationEntry>,
    annotatedConstructors: Set<KtPrimaryConstructor> = emptySet(),
): List<TextEdit> {
    val constructor = (annotatedDeclaration() as? KtPrimaryConstructor)
        ?.takeIf { it !in annotatedConstructors && it.keywordGoesWith(removedEntries) }
    if (constructor != null) {
        // The fixer wrote the keyword for the annotations, so the first removed entry takes the keyword too.
        if (constructor.modifierList?.annotationEntries?.firstOrNull() != this) return emptyList()
        val start = (constructor.prevSibling as? PsiWhiteSpace)?.textRange?.startOffset ?: constructor.textRange.startOffset
        val end = constructor.valueParameterList?.textRange?.startOffset ?: constructor.textRange.endOffset
        return listOf(TextEdit(start, end, ""))
    }

    val removed = removedElement(removedEntries) ?: return emptyList()
    val line = removed.wholeLine(text) ?: run {
        val end = removed.textRange.endOffset
        return listOf(TextEdit(removed.textRange.startOffset, end + text.substring(end).takeWhile { it == ' ' || it == '\t' }.length, ""))
    }

    // A removed line between two blank lines takes one of them, so a removed `@file:` exemption leaves no gap.
    // The removed lines before this one don't count, because their edits remove them.
    val removedLines = removedEntries.mapNotNull { it.removedElement(removedEntries)?.wholeLine(text) }
    var before = line.first
    while (true) {
        before = removedLines.firstOrNull { it.last + 1 == before }?.first ?: break
    }
    val blankBefore = before <= 1 || text[before - 2] == '\n'
    val blankAfter = line.last + 1 < text.length && text[line.last + 1] == '\n'
    return listOf(TextEdit(line.first, if (blankBefore && blankAfter) line.last + 2 else line.last + 1, ""))
}

private fun KtAnnotationEntry.removedElement(removedEntries: Set<KtAnnotationEntry>): PsiElement? {
    // `@[]` isn't valid Kotlin. When the run removes every entry of a list, the first one takes the brackets.
    val list = annotationList?.takeIf { annotationList -> annotationList.entries.all { it in removedEntries } }
    if (list != null && list.entries.first() != this) return null
    return list ?: this
}

internal fun String.lineStartIfIndented(offset: Int): Int {
    val lineStart = lastIndexOf('\n', offset - 1) + 1
    return if (substring(lineStart, offset).isBlank()) lineStart else offset
}

private fun PsiElement.wholeLine(text: String): IntRange? {
    val start = textRange.startOffset
    val end = textRange.endOffset
    val lineStart = text.lastIndexOf('\n', start - 1) + 1
    val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it + 1 }
    val alone = text.substring(lineStart, start).isBlank() && text.substring(end, lineEnd).isBlank()
    return (lineStart until lineEnd).takeIf { alone }
}

private fun KtPrimaryConstructor.keywordGoesWith(removedEntries: Set<KtAnnotationEntry>): Boolean {
    val modifiers = modifierList ?: return false
    val entries = modifiers.annotationEntries
    return getConstructorKeyword() != null &&
            docComment == null &&
            modifiers.node.getChildren(KtTokens.MODIFIER_KEYWORDS).isEmpty() &&
            entries.isNotEmpty() &&
            entries.all { it in removedEntries }
}

private fun PlannedFix.insertionText(imports: ImportResolver, useSite: String = ""): String = when (fix) {
    is ExemptionFix -> annotationText(imports, useSite)
    PlaceholderKdocFix -> "/** ${PlaceholderKdocFix.TEXT} */"
    RemoveAnnotationFix -> error("A removal doesn't insert anything")
}

private fun PlannedFix.annotationText(imports: ImportResolver, useSite: String = ""): String =
    "@$useSite${annotationCall(imports)}"

private val PlannedFix.exemption: ExemptionFix
    get() = fix as? ExemptionFix ?: error("${fix.id} isn't an exemption annotation")

private fun PlannedFix.annotationCall(imports: ImportResolver): String {
    val fix = exemption
    val name = imports.reference(fix.annotationShortName)
    val arguments = if (fix.hasReasonParameter) {
        "(reason = ${imports.reference(ExemptionRegistry.REASON_CLASS)}.${ExemptionRegistry.REASON_ENTRY})"
    } else {
        ""
    }
    return "$name$arguments"
}

private fun KtFileAnnotationList.hasExemptionNamed(shortName: String, removedEntries: Set<KtAnnotationEntry>): Boolean =
    annotationEntries.any { it.shortName?.asString() == shortName && it !in removedEntries }

/** The checks read an exemption on the declaration and on the parameter of a constructor property. */
private val CHECKED_USE_SITE_TARGETS = setOf(null, "property", "param", "all")

private fun KtAnnotated.hasExemptionNamed(shortName: String, removedEntries: Set<KtAnnotationEntry>): Boolean =
    annotationEntries.any {
        it.shortName?.asString() == shortName && it !in removedEntries &&
                it.useSiteTarget?.getAnnotationUseSiteTarget()?.renderName in CHECKED_USE_SITE_TARGETS
    }

private fun KtAnnotationEntry.annotatedDeclaration(): KtAnnotated? =
    parent.ancestorsWithSelf().filterIsInstance<KtDeclaration>().firstOrNull()

internal fun resolveTarget(element: PsiElement, fix: Fix, ktFile: KtFile): FixTarget? =
    when (fix.targetStrategy) {
        TargetStrategy.CONTAINING_FILE -> FixTarget.WholeFile(ktFile)

        TargetStrategy.ENCLOSING_CLASS ->
            element.ancestorsWithSelf()
                .filterIsInstance<KtClassOrObject>()
                .firstOrNull { it !is KtEnumEntry }
                ?.let { FixTarget.BeforeDeclaration(it) }

        TargetStrategy.ENCLOSING_CLASS_OR_TYPE_ALIAS ->
            element.ancestorsWithSelf()
                .firstOrNull { it is KtTypeAlias || it is KtClassOrObject && it !is KtEnumEntry }
                ?.let { FixTarget.BeforeDeclaration(it as KtDeclaration) }

        TargetStrategy.ENCLOSING_ANNOTATION_CLASS ->
            element.ancestorsWithSelf()
                .filterIsInstance<KtClass>()
                .firstOrNull { it.isAnnotation() }
                ?.let { FixTarget.BeforeDeclaration(it) }

        TargetStrategy.REPORTED_DECLARATION -> element.resolveDeclarationTarget()

        TargetStrategy.DOCUMENTED_DECLARATION -> element.resolveDocumentedTarget()

        TargetStrategy.REPORTED_ANNOTATION ->
            element.ancestorsWithSelf()
                .filterIsInstance<KtAnnotationEntry>()
                .firstOrNull()
                ?.let { if (fix is RemoveAnnotationFix) FixTarget.RemovedAnnotation(it) else FixTarget.ReplacedAnnotation(it) }

        TargetStrategy.ENCLOSING_CALLABLE -> element.resolveCallableTarget()

        TargetStrategy.ENCLOSING_FUNCTION_OR_CONSTRUCTOR -> element.resolveFunctionOrConstructorTarget()
    }

private fun PsiElement.resolveDeclarationTarget(): FixTarget? {
    for (candidate in ancestorsWithSelf()) {
        when (candidate) {
            // No check reports an enum entry, and the exemption must not go to the enum class.
            is KtEnumEntry -> return null
            is KtParameter -> if (candidate.hasValOrVar()) return FixTarget.InlineBefore(candidate)
            is KtPropertyAccessor -> return FixTarget.BeforeDeclaration(candidate.property)
            is KtPrimaryConstructor -> return candidate.asTarget()
            is KtNamedFunction, is KtSecondaryConstructor, is KtProperty, is KtClassOrObject, is KtTypeAlias ->
                return FixTarget.BeforeDeclaration(candidate as KtDeclaration)
        }
    }
    return null
}

private fun PsiElement.resolveDocumentedTarget(): FixTarget? {
    for (candidate in ancestorsWithSelf()) {
        when (candidate) {
            is KtParameter -> return if (candidate.hasValOrVar()) FixTarget.BeforeDeclaration(candidate) else null
            is KtPropertyAccessor -> return FixTarget.BeforeDeclaration(candidate.property)
            // The class KDoc documents the primary constructor.
            is KtPrimaryConstructor -> return null
            is KtNamedFunction, is KtSecondaryConstructor, is KtProperty, is KtClassOrObject, is KtTypeAlias ->
                return FixTarget.BeforeDeclaration(candidate as KtDeclaration)
        }
    }
    return null
}

private fun PsiElement.resolveCallableTarget(): FixTarget? {
    for (candidate in ancestorsWithSelf()) {
        when (candidate) {
            // A type parameter of a class doesn't have an enclosing callable, so the annotation goes inline.
            is KtTypeParameter -> return FixTarget.InlineBefore(candidate)
            is KtParameter -> if (candidate.hasValOrVar()) return FixTarget.InlineBefore(candidate)
            is KtPropertyAccessor -> return FixTarget.BeforeDeclaration(candidate.property)
            is KtPrimaryConstructor -> return candidate.asTarget()
            is KtNamedFunction, is KtSecondaryConstructor, is KtProperty ->
                return FixTarget.BeforeDeclaration(candidate as KtDeclaration)
            is KtClassOrObject -> return null
        }
    }
    return null
}

private fun PsiElement.resolveFunctionOrConstructorTarget(): FixTarget? {
    for (candidate in ancestorsWithSelf()) {
        when (candidate) {
            is KtPrimaryConstructor -> return candidate.asTarget()
            is KtNamedFunction, is KtSecondaryConstructor ->
                return FixTarget.BeforeDeclaration(candidate as KtDeclaration)
            is KtClass -> return candidate.primaryConstructor?.asTarget()
            is KtClassOrObject -> return null
        }
    }
    return null
}

private fun KtPrimaryConstructor.asTarget(): FixTarget =
    if (getConstructorKeyword() != null) {
        FixTarget.BeforeDeclaration(this)
    } else {
        FixTarget.ImplicitPrimaryConstructor(this)
    }

private fun PsiElement.ancestorsWithSelf(): Sequence<PsiElement> = generateSequence(this) { it.parent }
