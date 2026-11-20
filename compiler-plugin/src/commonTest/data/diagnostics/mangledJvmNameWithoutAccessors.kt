// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME -COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS

package foo.bar

// A `const val` is a static field without a getter.

public object Limits {
    public const val MAX: UInt = 10u

    public val <!MANGLED_JVM_NAME_PUBLIC_API!>min<!>: UInt = 0u
}

public class Quota {
    public companion object {
        public const val DEFAULT: UInt = 10u
    }
}

// An annotation class has no constructor on the JVM, and the JVM name of its property isn't mangled.

public annotation class Limit(public val max: UInt)

public annotation class Range(public val from: UInt, public val to: UInt = 10u)
