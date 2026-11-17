package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.io.File
import java.nio.file.Paths
import org.jetbrains.kotlin.cli.jvm.compiler.jarfs.FastJarHandler
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass
import org.jetbrains.kotlin.load.kotlin.VirtualFileKotlinClass

private val FAST_JAR_VIRTUAL_FILE = try {
    Class.forName("org.jetbrains.kotlin.cli.jvm.compiler.jarfs.FastJarVirtualFile")
} catch (_: ClassNotFoundException) {
    null
}

// com.intellij APIs are shadowed in CLI, so we use reflection to circumvent that
private val VIRTUAL_FILE_KOTLIN_CLASS_FILE_FIELD = try {
    VirtualFileKotlinClass::class.java.getDeclaredField("file").takeIf { it.trySetAccessible() }
} catch (_: NoSuchFieldException) {
    null
}

private val FAST_JAR_HANDLER_FIELD = try {
    FAST_JAR_VIRTUAL_FILE?.getDeclaredField("handler")?.takeIf { it.trySetAccessible() }
} catch (_: NoSuchFieldException) {
    null
}
private val FAST_JAR_HANDLER_FILE_FIELD = try {
    FastJarHandler::class.java.getDeclaredField("file").takeIf { it.trySetAccessible() }
} catch (_: NoSuchFieldException) {
    null
}

internal actual val KotlinJvmBinaryClass.containingLibraryPathCompat: String?
    get() {
        if (this is VirtualFileKotlinClass) {
            val file = VIRTUAL_FILE_KOTLIN_CLASS_FILE_FIELD?.get(this)
            if (FAST_JAR_VIRTUAL_FILE?.isInstance(file) == true) {
                val handler = FAST_JAR_HANDLER_FIELD?.get(file) as? FastJarHandler ?: return null
                val file = FAST_JAR_HANDLER_FILE_FIELD?.get(handler) as? File ?: return null
                return file.toPath().toString()
            }
        }
        return containingLibrary?.let { Paths.get(it) }?.toString()
    }