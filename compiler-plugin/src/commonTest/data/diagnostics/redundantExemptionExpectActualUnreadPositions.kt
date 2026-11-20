// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXHAUSTIVE_PUBLIC_API -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// The copy of an `expect` exemption on an `actual` enum entry or setter parameter isn't reported.

// MODULE: common
// FILE: common.kt

package foo.bar

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyBooleanParameter
import kotlinx.library.watchdog.IntentionallyUndocumented

public expect enum class Copied {
    <!REDUNDANT_EXEMPTION, REDUNDANT_EXEMPTION{METADATA}!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!>
    A,
    B,
}

public expect enum class Uncopied {
    <!REDUNDANT_EXEMPTION, REDUNDANT_EXEMPTION{METADATA}!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!>
    A,
    B,
}

public expect class Settings() {
    <!REDUNDANT_EXEMPTION, REDUNDANT_EXEMPTION{METADATA}!>@setparam:IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
    public var copied: Boolean

    <!REDUNDANT_EXEMPTION, REDUNDANT_EXEMPTION{METADATA}!>@setparam:IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
    public var uncopied: Boolean
}

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyBooleanParameter
import kotlinx.library.watchdog.IntentionallyUndocumented

public actual enum class Copied {
    @IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)
    A,
    B,
}

public actual enum class Uncopied {
    A,
    B,
}

public actual class Settings actual constructor() {
    @setparam:IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    public actual var copied: Boolean = false

    public actual var uncopied: Boolean = false
}
