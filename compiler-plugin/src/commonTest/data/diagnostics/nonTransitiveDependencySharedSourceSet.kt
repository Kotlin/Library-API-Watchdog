// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// EXEMPTING_EXISTING_API
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING
// COMPILE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/models ../messages/nonTransitiveDependency.deps/shared
// TRANSITIVE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/shared
// SOURCE_SETS_WITHOUT_DEPENDENCY_CHECK: common

// The dependency check doesn't report the common module and doesn't judge its exemptions or their copies.

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
package consumer

import com.example.models.Model
import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyNonTransitiveDependency

public fun leaked(): Model = Model()

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
public fun count(): Int = 1

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
public expect fun total(): Int

// MODULE: jvm(lib)()(common)
// FILE: jvm.kt
package consumer

import com.example.models.Model
import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyNonTransitiveDependency

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
public actual fun total(): Int = 1

public fun leakedOnJvm(): <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!> = Model()

<!REDUNDANT_EXEMPTION!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")<!>
public fun countOnJvm(): Int = 1
