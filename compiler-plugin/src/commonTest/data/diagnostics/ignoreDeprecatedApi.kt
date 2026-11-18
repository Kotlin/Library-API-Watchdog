// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// IGNORE_DEPRECATED_API
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -DEPRECATION

package foo.bar

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyBooleanParameter

// The public API checks skip a `@Deprecated` declaration and the declarations nested in it.

@Deprecated("Use enable.")
public fun toggle(on: Boolean) {}

@Deprecated("Use enable.", level = DeprecationLevel.HIDDEN)
public fun hiddenToggle(on: Boolean) {}

@Deprecated("Use names.")
public val mutableNames: MutableList<String> = mutableListOf()

@Deprecated("Use Endpoint.")
public data class LegacyEndpoint(public val host: String) {
    public fun connect(secure: Boolean) {}

    public data class Options(public val port: Int)
}

public class Server {
    public constructor()

    @Deprecated("Use the constructor without parameters.")
    public constructor(names: MutableList<String>)

    @Deprecated("Use start.")
    public fun start(secure: Boolean) {}
}

// A declaration without `@Deprecated` is checked.

public fun enable(<!BOOLEAN_PARAMETER_PUBLIC_API!>on<!>: Boolean) {}

// A deprecated class isn't an internal API type.

public fun endpoint(): LegacyEndpoint = LegacyEndpoint("localhost")

// A deprecated overload isn't compared.

@Deprecated("Use move(x, y).")
public fun move(scale: Double, x: Int, y: Int) {}

public fun move(x: Int, y: Int) {}

// An exemption on a deprecated declaration is redundant.

@Deprecated("Use enable.")
<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.FOR_DEPRECATION)<!>
public fun legacyToggle(on: Boolean) {}
