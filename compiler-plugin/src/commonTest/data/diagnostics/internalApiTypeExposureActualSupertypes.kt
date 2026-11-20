// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -EXEMPTION_WITHOUT_EXPLANATION

// An `actual` class reports the supertypes that its `expect` class doesn't declare.

// MODULE: common
// FILE: common.kt

package foo.bar

import kotlinx.library.watchdog.InternalAnnotationMarker

@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS)
public annotation class LibInternal

@LibInternal
public open class HiddenBase

@LibInternal
public interface HiddenContract

public interface Contract

public expect class Client

public expect class Service : <!PUBLIC_TYPE_WITH_INTERNAL_API!>HiddenContract<!>

public expect class Plain : Contract

public expect class Exempt

public expect class ExemptSupertype

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

import kotlinx.library.watchdog.IntentionallyExposedInternalApiForBackwardsCompatibility

public actual class Client : <!PUBLIC_TYPE_WITH_INTERNAL_API!>HiddenBase<!>()

// The `expect` class reports a supertype that it declares.
public actual class Service : <!PUBLIC_TYPE_WITH_INTERNAL_API!>HiddenBase<!>(), HiddenContract

public actual class Plain : Contract

@IntentionallyExposedInternalApiForBackwardsCompatibility
public actual class Exempt : HiddenBase()

public actual class ExemptSupertype : @IntentionallyExposedInternalApiForBackwardsCompatibility HiddenBase()
