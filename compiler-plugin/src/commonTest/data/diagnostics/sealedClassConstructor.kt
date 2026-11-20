// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXHAUSTIVE_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION

package foo.bar

import kotlinx.library.watchdog.IntentionallyWithoutJvmOverloads

/** A value class. */
@JvmInline
public value class UserId(
    /** The raw value. */
    public val raw: String,
)

// No code outside the module can call a constructor of a sealed class, so it isn't public API.

/** A sealed class with a default value. */
public sealed class Shape(label: String = "")

/** A sealed class with a value class parameter. */
public sealed class Event(id: UserId)

/** A sealed class with parameters that the style checks report in a public constructor. */
public sealed class Command(tags: MutableList<String>, enabled: Boolean = true, name: String, flag: Boolean?)

/** A sealed class with secondary constructors. */
public sealed class Message {
    protected constructor(x: Int, y: Int)

    protected constructor(y: Int, x: Int, scale: Long)

    private constructor(text: String = "")
}

/** The property of a `val` parameter stays public API. */
public sealed class Tagged(
    /** The tags. */
    public val tags: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<String><!>,
    /** The owner. */
    public val <!MANGLED_JVM_NAME_PUBLIC_API!>owner<!>: UserId,
)

/** An exemption on a constructor of a sealed class is redundant. */
public sealed class Exempt <!REDUNDANT_EXEMPTION!>@IntentionallyWithoutJvmOverloads<!> constructor(label: String = "")

// A constructor of an abstract class is public API.

/** An abstract class. */
public abstract class Base<!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>(label: String = "")<!> {
    /** A secondary constructor. */
    <!MANGLED_JVM_NAME_PUBLIC_API!>protected constructor(id: UserId) : this("")<!>
}
