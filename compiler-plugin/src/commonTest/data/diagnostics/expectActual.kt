// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -NOTHING_TO_INLINE

// Source-shape checks report the `expect`. Body, generated-member, and JVM checks report the
// `actual` and inherit the exemptions of its `expect`.

// MODULE: common
// FILE: common.kt

package foo.bar

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyBooleanParameter
import kotlinx.library.watchdog.IntentionallyDataClass
import kotlinx.library.watchdog.IntentionallyInlinedLogic
import kotlinx.library.watchdog.IntentionallyKotlinOnlyApi
import kotlinx.library.watchdog.IntentionallyNullableBoolean
import kotlinx.library.watchdog.IntentionallyPairOrTriple
import kotlinx.library.watchdog.IntentionallyWithoutEqualsHashCodeOrToString
import kotlinx.library.watchdog.IntentionallyWithoutJvmOverloads
import kotlinx.library.watchdog.InternalAnnotationMarker

@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS)
public annotation class PlatformInternalApi

public expect fun reported(
    <!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean,
): <!NULLABLE_BOOLEAN_PUBLIC_API, PAIR_OR_TRIPLE_PUBLIC_API!>Pair<String, Boolean?><!>

@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
@IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)
@IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)
public expect fun exemptContract(flag: Boolean): Pair<String, Boolean?>

@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public expect class ExemptData(value: Int) {
    public val value: Int
}

public expect class ReportedData(value: Int) {
    public val value: Int
}

@IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.API_DESIGN)
public expect class ExemptState(value: Int) {
    public val value: Int
}

public expect class ReportedState(value: Int) {
    public val value: Int
}

@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public expect inline fun exemptInline(value: Int): Int

public expect inline fun reportedInline(value: Int): Int

// The default-parameter check reads the default value from the `expect` signature.

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public expect suspend fun exemptSuspend(): Int

public expect suspend fun reportedSuspend(): Int

@IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN)
public expect fun exemptDefault(value: Int = 1): Int

public expect fun reportedDefault(value: Int = 1): Int

// An `expect` can't expose a type that exists only in an actual source set, so the expansion
// of an `actual typealias` is checked.

public expect class PlatformAlias

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

public actual fun reported(flag: Boolean): Pair<String, Boolean?> = flag.toString() to flag

public actual fun exemptContract(flag: Boolean): Pair<String, Boolean?> = flag.toString() to flag

// A declaration without an `expect` is checked as usual.
public fun platformOnly(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean): Int = if (flag) 1 else 0

public actual data class ExemptData public actual constructor(public actual val value: Int)

public actual data class <!DATA_CLASS_PUBLIC_API!>ReportedData<!> public actual constructor(
    public actual val value: Int,
)

public actual class ExemptState public actual constructor(public actual val value: Int)

public actual class <!STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE, STATEFUL_CLASS_WITHOUT_TO_STRING!>ReportedState<!> public actual constructor(
    public actual val value: Int,
)

public actual inline fun exemptInline(value: Int): Int = value + 1

public actual inline fun <!INLINE_FUNCTION_WITH_LOGIC!>reportedInline<!>(value: Int): Int = value + 1

public actual suspend fun exemptSuspend(): Int = 1

public actual suspend fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>reportedSuspend<!>(): Int = 1

public actual fun exemptDefault(value: Int): Int = value

public actual fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>reportedDefault<!>(value: Int): Int = value

@PlatformInternalApi
public class PlatformInternalType

public actual typealias PlatformAlias = <!PUBLIC_TYPE_WITH_INTERNAL_API!>PlatformInternalType<!>
