// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// A use of an `actual typealias` reads the exemption that the use writes.

// MODULE: common
// FILE: common.kt
package foo.bar

public expect open class Store<E>

// MODULE: jvm()()(common)
// FILE: jvm.kt
package foo.bar

import kotlinx.library.watchdog.ExemptionReason
import kotlinx.library.watchdog.IntentionallyMutableCollection

public actual typealias Store<E> = java.util.ArrayList<E>

public fun exempt(): @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) Store<Int> = Store()

public fun bare(): <!MUTABLE_COLLECTION_PUBLIC_API!>Store<Int><!> = Store()
