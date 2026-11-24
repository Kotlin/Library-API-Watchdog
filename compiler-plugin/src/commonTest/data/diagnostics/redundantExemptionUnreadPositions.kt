// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -EXHAUSTIVE_PUBLIC_API -MUTABLE_COLLECTION_PUBLIC_API

// Exemptions on a constructor parameter, an enum entry, and a setter parameter, where no check reads them.

// FILE: declarations.kt
package foo.bar

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyBooleanParameter
import kotlinx.library.watchdog.IntentionallyMutableCollection
import kotlinx.library.watchdog.IntentionallyUndocumented

public class Flags(<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> public val on: Boolean)

public class BareFlags(public val on: Boolean)

public class PlainFlags(<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> on: Boolean)

public class TargetedFlags(
    <!REDUNDANT_EXEMPTION!>@param:IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> public var on: Boolean,
    @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) public val items: MutableList<Int>,
)

public enum class Mode {
    <!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!>
    FAST,

    <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!>
    SAFE,
}

public class Settings {
    <!REDUNDANT_EXEMPTION!>@setparam:IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!>
    public var items: MutableList<Int> = mutableListOf()

    public var exemptItems: @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<Int> = mutableListOf()

    <!REDUNDANT_EXEMPTION!>@setparam:IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
    public var on: Boolean = false

    public var explicit: Boolean = false
        set(<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> value) {
            field = value
        }

    public var explicitItems: List<Int> = emptyList()
        set(<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> value) {
            field = value
        }
}
