package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration

internal actual val FirClassLikeDeclaration.isLocalCompat: Boolean
    get() = isLocal
