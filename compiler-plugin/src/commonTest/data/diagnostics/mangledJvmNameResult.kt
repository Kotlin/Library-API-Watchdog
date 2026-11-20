// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME -FINAL_UPPER_BOUND

package foo.bar

// A parameter, receiver, or context parameter of type `kotlin.Result` doesn't mangle the JVM name.

public fun handle(result: Result<String>) {}

public fun handleNullable(result: Result<String>?) {}

public fun Result<String>.describe(): String = "result"

context(result: Result<String>)
public fun describeContext(): String = "result"

public fun <T : Result<String>> handleBounded(t: T) {}

public val Result<String>.pretty: String get() = "result"

public var topResult: Result<String> = Result.success("x")

public class Outcome(public val <!MANGLED_JVM_NAME_PUBLIC_API!>result<!>: Result<String>)

// The function isn't mangled, so the Kotlin-only shape is reported.
public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>onResult<!>(result: Result<String>, action: (Int) -> Unit) {}

// A `Result` return type mangles the name of a member.

public fun topLevel(): Result<String> = Result.success("x")

public class Loader {
    public fun <!MANGLED_JVM_NAME_PUBLIC_API!>load<!>(): Result<String> = Result.success("x")

    public val <!MANGLED_JVM_NAME_PUBLIC_API!>last<!>: Result<String> get() = Result.success("x")

    // Only the getter is mangled.
    public var <!MANGLED_JVM_NAME_PUBLIC_API!>latest<!>: Result<String> = Result.success("x")

    @get:JvmName("getNamed")
    public var named: Result<String> = Result.success("x")

    public fun store(result: Result<String>) {}
}
