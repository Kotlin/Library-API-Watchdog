// RUN_PIPELINE_TILL: FRONTEND
// DISABLE_NEXT_PHASE_SUGGESTION: the later phase is named BACKEND before Kotlin 2.5 and CODEGEN after it
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING
// COMPILE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/models ../messages/nonTransitiveDependency.deps/shared
// TRANSITIVE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/shared
// DEPENDENCY_DECLARATIONS: ../messages/nonTransitiveDependency.deps/models implementation "com.example:models:1.0"

// A class-level exemption of an `expect` class covers the supertypes and the members that only its `actual` class declares.

// MODULE: lib
// FILE: models.kt
package com.example.models

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyOpen

/** A model type of the declared `models` dependency. */
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Model

// MODULE: common(lib)
// FILE: common.kt
package foo.bar

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyNonTransitiveDependency

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.API_DESIGN)
public expect class SupertypeOnly

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.API_DESIGN)
public expect class MemberOnly

<!REDUNDANT_EXEMPTION!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.API_DESIGN)<!>
public expect class Plain

// MODULE: jvm(lib)()(common)
// FILE: jvm.kt
package foo.bar

import com.example.models.Model
import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyNonTransitiveDependency

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.API_DESIGN)
public actual class SupertypeOnly : Model()

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.API_DESIGN)
public actual class MemberOnly {
    public fun extra(): Model = Model()
}

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.API_DESIGN)
public actual class Plain
