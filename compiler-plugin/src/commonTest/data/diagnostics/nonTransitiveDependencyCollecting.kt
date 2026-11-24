// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// EXEMPTING_EXISTING_API
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING
// COMPILE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/models ../messages/nonTransitiveDependency.deps/shared
// TRANSITIVE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/shared
// DEPENDENCY_DECLARATIONS: ../messages/nonTransitiveDependency.deps/models implementation "com.example:models:1.0"

// The collection mode of `exemptExistingApi` reports the exposure as a warning and judges the exemptions.

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

import com.example.models.Model
import com.example.shared.Shared
import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyNonTransitiveDependency

public val leakedProperty: <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!> = Model()

public val <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>.leakedReceiver: String
    get() = ""

public fun leakedReturn(): <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!> = Model()

public fun leakedParameter(value: <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>) {}

public fun leakedNested(): <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>List<Model><!> = emptyList()

public fun <T : <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>> leakedFunctionBound(value: T) {}

public class LeakedConstructor(value: <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>)

public class LeakedConstructorProperty(public val value: <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>)

public class LeakedSupertype : <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>() {
    public fun member(): <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!> = Model()
}

public class LeakedClassBound<T : <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>>

public typealias LeakedTypeAlias = <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>

public fun transitive(shared: Shared) {}

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
public fun exempted(): Model = Model()

<!REDUNDANT_EXEMPTION!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)<!>
public fun redundant(): Shared = Shared()
