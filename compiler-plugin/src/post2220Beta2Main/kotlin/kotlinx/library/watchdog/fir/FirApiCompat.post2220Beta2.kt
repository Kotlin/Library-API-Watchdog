package kotlinx.library.watchdog.fir

import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass

internal actual val KotlinJvmBinaryClass.containingLibraryPathCompat: String?
    get() = containingLibraryPath?.path?.toString()
