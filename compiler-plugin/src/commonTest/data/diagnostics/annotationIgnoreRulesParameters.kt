// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// IGNORE_WHEN_ANNOTATED: BOOLEAN_PARAMETER_PUBLIC_API:foo.bar.Flag MUTABLE_COLLECTION_PUBLIC_API:foo.bar.Flag NULLABLE_BOOLEAN_PUBLIC_API:foo.bar.Flag REQUIRED_PARAMETER_AFTER_OPTIONAL:foo.bar.Flag
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME -DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS

package foo.bar

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
public annotation class Flag

// A rule covers what a check reports on the annotated parameter.

public fun setLogging(@Flag enabled: Boolean) {}

public fun setBoth(@Flag enabled: Boolean, <!BOOLEAN_PARAMETER_PUBLIC_API!>verbose<!>: Boolean) {}

context(@Flag enabled: Boolean)
public fun setInContext() {}

public fun consume(@Flag items: MutableList<Int>, others: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>) {}

public class Sink(@Flag items: MutableList<Int>, others: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>)

// A rule on a `val` parameter covers the property.
public class Holder(@Flag public val items: MutableList<Int>, public val others: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>)

public fun connect(host: String = "", @Flag port: Int, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>retries<!>: Int) {}

@Flag
public fun connectAnnotated(host: String = "", port: Int) {}

// A rule covers the annotated type parameter.
public fun <@Flag T : MutableList<Int>, R : <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>> convert(items: T): R = TODO()

// An annotated parameter doesn't cover the return type.
public fun toggle(@Flag enabled: Boolean): <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!> = enabled
