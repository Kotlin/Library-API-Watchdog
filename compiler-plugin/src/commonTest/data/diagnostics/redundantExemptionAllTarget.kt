// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// LANGUAGE: +AnnotationAllUseSiteTarget
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// An `@all:` exemption also has a copy on the setter parameter. The property or the constructor judges it one time.

// FILE: all.kt
package foo.bar

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyBooleanParameter
import kotlinx.library.watchdog.IntentionallyMutableCollection

public class Store {
    @all:IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
    public var items: MutableList<Int> = mutableListOf()

    <!REDUNDANT_EXEMPTION!>@all:IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!>
    public var views: List<Int> = emptyList()
}

public class Holder(
    @all:IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) public var items: MutableList<Int>,
    <!REDUNDANT_EXEMPTION!>@all:IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> public var on: Boolean,
)
