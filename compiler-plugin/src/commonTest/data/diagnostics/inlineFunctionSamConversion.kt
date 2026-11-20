// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// FULL_JDK
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -NOTHING_TO_INLINE -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -OPEN_API_WITHOUT_SUBCLASS_OPT_IN

package foo.bar

import java.util.concurrent.Executor

@PublishedApi
internal val executor: Executor = Executor { it.run() }

public fun interface Task {
    public fun run()
}

@PublishedApi
internal fun submit(task: Task) {}

@PublishedApi
internal fun log(message: String) {}

// A lambda that converts to a Java or Kotlin functional interface follows the rules of a lambda.

public inline fun runLater(crossinline action: () -> Unit) {
    executor.execute { action() }
}

public inline fun submitLater(crossinline action: () -> Unit) {
    submit { action() }
}

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>runLoggedLater<!>(crossinline action: () -> Unit) {
    executor.execute {
        log("run")
        action()
    }
}

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>submitLoggedLater<!>(crossinline action: () -> Unit) {
    submit {
        log("run")
        action()
    }
}
