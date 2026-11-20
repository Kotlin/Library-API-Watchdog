// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -NOTHING_TO_INLINE

// A `private set` or an `internal set` isn't public API.

// FILE: mangled.kt
@file:JvmName("Mangled")

package foo.bar

@JvmInline
public value class UserId(public val raw: String)

public var current: UserId = UserId("x")
    private set

public var shared: UserId = UserId("x")
    internal set

public open class Ledger {
    // Only the getter is mangled.
    public var <!MANGLED_JVM_NAME_PUBLIC_API!>owner<!>: UserId = UserId("x")
        private set

    @get:JvmName("getEditorNamed")
    public var editor: UserId = UserId("x")
        private set

    @get:JvmName("getAuditorNamed")
    public var <!MANGLED_JVM_NAME_PUBLIC_API!>auditor<!>: UserId = UserId("x")
        protected set
}

// FILE: inline.kt
@file:JvmName("Inline")

package foo.bar

@PublishedApi
internal var storedLimit: Int = 0

public inline var privateSetter: Int
    get() = storedLimit
    private set(value) {
        storedLimit = if (value < 0) 0 else value
    }

public inline var internalSetter: Int
    get() = storedLimit
    internal set(value) {
        storedLimit = if (value < 0) 0 else value
    }

@PublishedApi
internal inline var <!INLINE_FUNCTION_WITH_LOGIC!>publishedSetter<!>: Int
    get() = storedLimit
    set(value) {
        storedLimit = if (value < 0) 0 else value
    }

// FILE: hiddenGetter.kt
package foo.bar

// The getter is the only Java entry point.
@get:JvmSynthetic
public var counter: Int = 0
    private set

// FILE: publishedSetter.kt
package foo.bar

@PublishedApi
@get:JvmSynthetic
internal var <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>publishedCounter<!>: Int = 0
