// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// INCLUDED_ANNOTATIONS: test.markers.PublicApi test.sealed.PublicConstructor
// INCLUDED_NAMES: test.api.Outer.Companion test.api.**.create* test.api.Levels.HIGH
// EXCLUDED_NAMES: test.api.ExcludedMember.createExcluded test.api.Levels.HIGH
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// FILE: markers.kt
package test.markers

import kotlinx.library.watchdog.InternalAnnotationMarker

@Target(AnnotationTarget.FILE, AnnotationTarget.FUNCTION)
public annotation class PublicApi

@InternalAnnotationMarker
@Target(AnnotationTarget.FUNCTION)
public annotation class HiddenApi

// FILE: annotatedFile.kt
// The file facade check and the overload check use the file annotation too.
@file:PublicApi

package test.api

import test.markers.PublicApi

/** Documented. */
public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS, TOP_LEVEL_API_WITHOUT_JVM_NAME!>draw<!>(x: Int, y: Int) {}

/** Documented. */
public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>draw<!>(y: Int, x: Int, scale: Double) {}

// FILE: members.kt
package test.api

import test.markers.HiddenApi

// Only a member of the public API includes its class.
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>PublicMember<!> {
    public fun <!UNDOCUMENTED_PUBLIC_API!>createPublic<!>() {}
}

public open class PrivateMember {
    private fun createPrivate() {}
}

public open class InternalMember {
    internal fun createInternal() {}
}

public open class ExcludedMember {
    public fun createExcluded() {}
}

public open class HiddenMember {
    @HiddenApi
    public fun createHidden() {}
}

// An included companion object doesn't include the enclosing class.
public open class Outer {
    public companion object {
        public fun <!COMPANION_API_WITHOUT_JVM_STATIC, UNDOCUMENTED_PUBLIC_API!>make<!>(): Outer = Outer()
    }
}

// An excluded enum entry doesn't include the enum class.
public enum class Levels {
    HIGH,
}

// FILE: sealed.kt
package test.sealed

@Target(AnnotationTarget.CONSTRUCTOR)
public annotation class PublicConstructor

// An included constructor includes its class. A constructor of a sealed class isn't public API and doesn't include the class.

/** Documented. */
public abstract class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>Abstract<!> @PublicConstructor constructor(tags: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<String><!>)

/** Documented. */
public sealed class Sealed @PublicConstructor constructor(tags: MutableList<String>)
