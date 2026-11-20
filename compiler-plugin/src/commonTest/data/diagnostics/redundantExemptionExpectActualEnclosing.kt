// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// A class exemption of an `actual` class covers the exemptions of an `expect` member, so it isn't redundant.

// MODULE: common
// FILE: common.kt

package foo.bar

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyMangledJvmName

@JvmInline
public value class V(public val raw: Int)

public expect class Holder <!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!> constructor(<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!> v: V) {
    public val v: V
}

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyMangledJvmName

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public actual class Holder @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN) actual constructor(@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN) public actual val v: V)
