// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// EXEMPTING_EXISTING_API
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME
// COMPILE_DEPENDENCY_PATHS: nonTransitiveDependency.deps/models
// DEPENDENCY_DECLARATIONS: nonTransitiveDependency.deps/models implementation "com.example:models:1.0"

// The collection mode of `exemptExistingApi` reports the exposure as a warning.

// MODULE: lib
// FILE: models.kt
package com.example.models

/** A model type of the declared `models` dependency. */
public class Model

// MODULE: main(lib)
// FILE: exposure.kt
package consumer

import com.example.models.Model

public fun leakedReturn(): <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!> = Model()
