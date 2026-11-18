package kotlinx.library.watchdog.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent

/** Registered only with the `ignoreDeprecatedApi` option. */
internal class IgnoredDeprecatedApi(session: FirSession) : FirExtensionSessionComponent(session)

internal val FirSession.ignoredDeprecatedApi: IgnoredDeprecatedApi?
        by FirSession.nullableSessionComponentAccessor()
