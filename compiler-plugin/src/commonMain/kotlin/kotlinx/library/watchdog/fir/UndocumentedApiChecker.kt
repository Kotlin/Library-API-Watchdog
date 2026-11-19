package kotlinx.library.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.name.ClassId

/**
 * The KDoc lookup isn't cached: in the IDE the session outlives a KDoc edit, and the source
 * element of the declaration stays the same object.
 */
internal class UndocumentedApiChecker(
    severities: WatchdogDiagnosticSeverities,
    private val reportClassProperties: Boolean,
) : FirBasicDeclarationChecker(MppCheckerKind.Common), ExemptingChecker {
    override val exemptions: Set<ClassId> = setOf(WatchdogClassIds.IntentionallyUndocumented)
    override val pairSide: PairSide = PairSide.EXPECT

    private val factory = severities[WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API]

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        val factory = factory ?: return

        if (declaration !is FirMemberDeclaration) {
            return
        }

        val kind = declaration.watchedKind() ?: return
        if (!declaration.isWatchedPublicSourceApi()) {
            return
        }

        if (declaration.source.hasKdoc()) {
            return
        }

        if (declaration.hasExemption(WatchdogClassIds.IntentionallyUndocumented)) {
            return
        }

        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = WatchdogDiagnosticMessages.parameterValueFor(factory.name, kind),
            b = declaration.watchedName(context) ?: return,
        )
    }

    /**
     * The kind word of `diagnostics.json`, or null when another declaration can hold the KDoc:
     *
     * - the class for a primary constructor
     * - the members for a default-named companion
     * - the class for a member property, unless [reportClassProperties] is on
     * - the overridden or the `expect` declaration for an override or an actualized declaration
     */
    context(context: CheckerContext)
    private fun FirMemberDeclaration.watchedKind(): String? = when {
        isActualizedDeclaration() -> null
        this is FirRegularClass -> if (isDefaultNamedCompanion) null else kindParameterValue
        this is FirTypeAlias -> "typeAlias"
        this is FirNamedFunction -> if (isOverride) null else "function"
        this is FirProperty -> when {
            isOverride -> null
            !reportClassProperties && context.containingClassSymbol != null -> null
            else -> "property"
        }
        this is FirConstructor -> if (isPrimary) null else "constructor"
        else -> null
    }

    private val FirRegularClass.isDefaultNamedCompanion: Boolean
        get() = isCompanion && name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT

    private fun FirMemberDeclaration.watchedName(context: CheckerContext): Name? = when (this) {
        is FirRegularClass -> name
        is FirTypeAlias -> name
        is FirNamedFunction -> name
        is FirProperty -> name
        is FirConstructor -> context.containingClassSymbol?.classId?.shortClassName ?: symbol.name
        else -> null
    }

    // KDoc isn't in FIR.
    context(context: CheckerContext)
    private fun KtSourceElement?.hasKdoc(): Boolean = this?.childToken(SourceToken.KDOC) != null
}
