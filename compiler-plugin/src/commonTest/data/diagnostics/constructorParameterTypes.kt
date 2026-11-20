// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// IGNORE_DEPRECATED_API
// EXCLUDED_NAMES: foo.bar.ExcludedProperty.items
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -UNDOCUMENTED_PUBLIC_API -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY
// COMPILE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/models
// DEPENDENCY_DECLARATIONS: ../messages/nonTransitiveDependency.deps/models implementation "com.example:models:1.0"

// MODULE: lib
// FILE: models.kt
package com.example.models

/** A model type of the `models` dependency. */
public class Model

// MODULE: main(lib)
// FILE: main.kt
package foo.bar

import com.example.models.Model
import kotlinx.library.watchdog.IntentionallyMutableCollection
import kotlinx.library.watchdog.IntentionallyNonTransitiveDependency
import kotlinx.library.watchdog.InternalAnnotationMarker

@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS)
public annotation class LibInternal

@LibInternal
public class Hidden

// The property of a public `val` parameter reports the type one time.
public class PublicItems(public val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>)

// The public constructor has the type of a property that isn't public API.

public class PrivateItems(private val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>)

public class InternalItems(internal val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>)

public class PrivateVarItems(private var items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>)

public class PrivatePair(private val pair: <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!>)

public class PrivateFlag(private val flag: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>)

public class PrivateHidden(private val hidden: <!PUBLIC_TYPE_WITH_INTERNAL_API!>Hidden<!>)

// The style checks skip an overriding property, so the constructor parameter reports the type.

public interface Source {
    public val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>
}

public class ListSource(override val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>) : Source

// An exemption on the parameter covers the constructor too.
public class ExemptItems(@IntentionallyMutableCollection private val items: MutableList<Int>)

public class ExemptPublicItems(@IntentionallyMutableCollection public val items: MutableList<Int>)

public class InternalConstructor internal constructor(private val items: MutableList<Int>)

public class PublicPropertyOfInternalConstructor internal constructor(
    public val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>,
)

internal class InternalClass(private val items: MutableList<Int>)

// The fixer tests compile this file without the dependency options, so the marker of
// PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY is off. An exemption that isn't redundant shows the report.

public class PrivateModel(@IntentionallyNonTransitiveDependency private val model: Model)

public class InternalConstructorModel internal constructor(
    <!REDUNDANT_EXEMPTION!>@IntentionallyNonTransitiveDependency<!> private val model: Model,
)

// The checks skip a property with an internal API marker, a deprecated property, and an excluded property.
// The constructor parameter reports the type.

@InternalAnnotationMarker
@Target(AnnotationTarget.PROPERTY)
public annotation class LibInternalProperty

public class MarkedProperty(@LibInternalProperty public val hidden: <!PUBLIC_TYPE_WITH_INTERNAL_API!>Hidden<!>)

public class DeprecatedProperty(@Deprecated("Use another class.") public val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>)

public class ExcludedProperty(public val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>)
