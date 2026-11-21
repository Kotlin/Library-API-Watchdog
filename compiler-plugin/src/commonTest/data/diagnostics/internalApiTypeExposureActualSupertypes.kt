// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -EXEMPTION_WITHOUT_EXPLANATION -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// An `actual` class reports the supertypes that its `expect` class doesn't declare.

// MODULE: common
// FILE: common.kt

package foo.bar

import kotlinx.library.watchdog.InternalAnnotationMarker
import kotlinx.library.watchdog.IntentionallyExposedInternalApiForBackwardsCompatibility

@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS)
public annotation class LibInternal

@LibInternal
public open class HiddenBase

@LibInternal
public interface HiddenContract

public interface Contract

public interface Box<A, B>

public expect class Client

public expect class Service : <!PUBLIC_TYPE_WITH_INTERNAL_API!>HiddenContract<!>

public expect class Plain : Contract

public expect class Exempt

public expect class ExemptSupertype

@IntentionallyExposedInternalApiForBackwardsCompatibility
public expect class Paired

@IntentionallyExposedInternalApiForBackwardsCompatibility
public expect class ExpectOnly

public expect class Shifted : <!REDUNDANT_EXEMPTION, REDUNDANT_EXEMPTION{METADATA}!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!> Contract

<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
public expect class OwnAlias

public expect class Bounded<T : <!REDUNDANT_EXEMPTION, REDUNDANT_EXEMPTION{METADATA}!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!> Contract>

public expect class Args : Box<@IntentionallyExposedInternalApiForBackwardsCompatibility HiddenContract, <!REDUNDANT_EXEMPTION, REDUNDANT_EXEMPTION{METADATA}!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!> Contract>

public expect class Paths : Box<@IntentionallyExposedInternalApiForBackwardsCompatibility HiddenContract, Contract>

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

@IntentionallyExposedInternalApiForBackwardsCompatibility
public actual class Paired : HiddenBase()

public actual class ExpectOnly : HiddenBase()

public actual class Shifted : @IntentionallyExposedInternalApiForBackwardsCompatibility HiddenBase(), @IntentionallyExposedInternalApiForBackwardsCompatibility Contract

@LibInternal
public class HiddenOne

@IntentionallyExposedInternalApiForBackwardsCompatibility
public actual typealias OwnAlias = HiddenOne

public actual class Bounded<T : @IntentionallyExposedInternalApiForBackwardsCompatibility Contract>

public actual class Args : Box<@IntentionallyExposedInternalApiForBackwardsCompatibility HiddenContract, @IntentionallyExposedInternalApiForBackwardsCompatibility Contract>

public actual class Paths : Box<@IntentionallyExposedInternalApiForBackwardsCompatibility HiddenContract, <!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!> Contract>
