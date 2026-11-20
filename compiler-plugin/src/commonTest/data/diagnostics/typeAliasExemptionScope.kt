// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY
// COMPILE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/models ../messages/nonTransitiveDependency.deps/shared
// TRANSITIVE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/shared
// DEPENDENCY_DECLARATIONS: ../messages/nonTransitiveDependency.deps/models implementation "com.example:models:1.0"

// A type-usage exemption inside a type alias doesn't exempt a use of the type alias.
// The fixer test can't resolve dependencies, so this file hides `PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`,
// and each use of `Models` needs its own exemption.

// MODULE: lib
// FILE: models.kt
package com.example.models

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyOpen

/** A model type of the declared `models` dependency. */
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Model

// MODULE: main(lib)
// FILE: aliases.kt
package consumer

import com.example.models.Model
import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyExposedInternalApiForBackwardsCompatibility
import kotlinx.library.watchdog.IntentionallyMutableCollection
import kotlinx.library.watchdog.IntentionallyNonTransitiveDependency
import kotlinx.library.watchdog.IntentionallyNullableBoolean
import kotlinx.library.watchdog.IntentionallyPairOrTriple
import kotlinx.library.watchdog.InternalAnnotationMarker

@InternalAnnotationMarker
public annotation class InternalApi

@InternalApi
public class Engine

public typealias Buffers = List<<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> MutableSet<Int>>

public typealias Points = List<<!REDUNDANT_EXEMPTION!>@IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)<!> Pair<Int, Int>>

public typealias Flags = Map<String, <!REDUNDANT_EXEMPTION!>@IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)<!> Boolean?>

public typealias Models = List<@IntentionallyNonTransitiveDependency(reason = ExemptionReason.API_DESIGN) Model>

public typealias Engines = List<@IntentionallyExposedInternalApiForBackwardsCompatibility Engine>

public fun buffers(): <!MUTABLE_COLLECTION_PUBLIC_API!>Buffers<!> = emptyList()

public fun points(): <!PAIR_OR_TRIPLE_PUBLIC_API!>Points<!> = emptyList()

public fun flags(): <!NULLABLE_BOOLEAN_PUBLIC_API!>Flags<!> = emptyMap()

public fun models(): @IntentionallyNonTransitiveDependency(reason = ExemptionReason.API_DESIGN) Models = emptyList()

public fun engines(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>Engines<!> = emptyList()

public typealias Buffer = <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> MutableList<Int>

public fun buffer(items: <!MUTABLE_COLLECTION_PUBLIC_API!>Buffer<!>) {}

public fun nestedBuffers(): <!MUTABLE_COLLECTION_PUBLIC_API!>Map<String, Buffers><!> = emptyMap()

public fun exemptBuffers(): @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) Buffers = emptyList()

public typealias Wrapped<T> = List<T>

public fun wrapped(): Wrapped<@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableSet<Int>> = emptyList()

public fun wrappedBuffer(): <!MUTABLE_COLLECTION_PUBLIC_API!>Wrapped<Buffer><!> = emptyList()

// FILE: uses.kt
package consumer

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyNonTransitiveDependency

public fun buffersElsewhere(): <!MUTABLE_COLLECTION_PUBLIC_API!>Buffers<!> = emptyList()

public fun pointsElsewhere(): <!PAIR_OR_TRIPLE_PUBLIC_API!>Points<!> = emptyList()

public fun flagsElsewhere(): <!NULLABLE_BOOLEAN_PUBLIC_API!>Flags<!> = emptyMap()

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.API_DESIGN)
public fun modelsElsewhere(): Models = emptyList()

public fun enginesElsewhere(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>Engines<!> = emptyList()
