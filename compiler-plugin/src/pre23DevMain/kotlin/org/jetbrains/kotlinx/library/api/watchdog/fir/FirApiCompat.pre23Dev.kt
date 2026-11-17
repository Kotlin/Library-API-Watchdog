package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.isLocal

internal actual val FirClassLikeDeclaration.isLocalCompat: Boolean
    get() = isLocal