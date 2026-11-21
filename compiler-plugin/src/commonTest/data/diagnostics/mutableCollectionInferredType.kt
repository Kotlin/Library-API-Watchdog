// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING

// A type-usage exemption doesn't exempt a declaration that infers the type.

package foo.bar

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyMutableCollection

public fun buffered(): @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<Int> = mutableListOf()

public fun nested(): List<@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableSet<Int>> = emptyList()

public fun shared() = <!MUTABLE_COLLECTION_PUBLIC_API!>buffered()<!>

public val sharedProperty = <!MUTABLE_COLLECTION_PUBLIC_API!>nested()<!>

public val computed get() = <!MUTABLE_COLLECTION_PUBLIC_API!>buffered()<!>

@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
public fun exempt() = buffered()

<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!>
internal fun hidden() = buffered()

public class Holder {
    public val items = <!MUTABLE_COLLECTION_PUBLIC_API!>buffered()<!>
}
