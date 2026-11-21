// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

// An inferred type and an expanded type alias have the type annotations of another declaration.

// FILE: declarations.kt
package foo.bar

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyMutableCollection

public fun buffer(): @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<Int> = mutableListOf()

public fun buffers(): List<@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableSet<Int>> = emptyList()

public fun view(): <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> List<Int> = emptyList()

internal fun fill() {
    val target = buffer()
    target.add(1)
}

private val shared = buffer()

internal val computed get() = buffer()

internal val delegated by lazy { buffer() }

internal fun inferred() = buffers()

internal fun inferredView() = view()

internal class Holder {
    val items = buffer()
}

public typealias Buffer = <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> MutableList<Int>

internal fun aliased(): Buffer = mutableListOf()

public fun <T> definitelyNotNull(value: <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> T & Any) {}

// FILE: uses.kt
package foo.bar

private val elsewhere = buffer()

internal fun fillElsewhere() {
    val target = buffers()
    val copy = view()
    val alias: Buffer = aliased()
}
