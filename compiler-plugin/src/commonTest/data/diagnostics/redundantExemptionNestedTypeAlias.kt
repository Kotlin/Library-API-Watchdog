// RUN_PIPELINE_TILL: FRONTEND
// DISABLE_NEXT_PHASE_SUGGESTION: the later phase is named BACKEND before Kotlin 2.5 and CODEGEN after it
// EXPLICIT_API_MODE: WARNING
// LANGUAGE: +NestedTypeAliases
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY
// COMPILE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/models ../messages/nonTransitiveDependency.deps/shared
// TRANSITIVE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/shared
// DEPENDENCY_DECLARATIONS: ../messages/nonTransitiveDependency.deps/models implementation "com.example:models:1.0"

// MODULE: lib
// FILE: models.kt
package com.example.models

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyOpen

/** A model type of the declared `models` dependency. */
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Model

// FILE: shared.kt
package com.example.shared

/** A type of the transitive `shared` dependency. */
public class Shared

// MODULE: main(lib)
// FILE: exposure.kt
package consumer

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyNonTransitiveDependency

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
public class Tasks {
    public typealias Model = com.example.models.Model
}

<!REDUNDANT_EXEMPTION!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")<!>
public class SharedTasks {
    public typealias Shared = com.example.shared.Shared
}

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
public class CoveredTasks {
    <!REDUNDANT_EXEMPTION!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")<!>
    public typealias Model = com.example.models.Model
}
