@file:JvmName("ExemptsFixerMain")

package kotlinx.library.watchdog.fixer

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFileAttributeView
import kotlin.system.exitProcess

/**
 * The argument is the path of a [FixerRequest] file. A non-zero exit code means that the fixer
 * failed, not that the project has diagnostics.
 */
fun main(args: Array<String>) {
    val request = FixerRequest.parse(Paths.get(args.singleOrNull() ?: error("Usage: <request file>")))
    val response = FixerResponse()
    try {
        run(request, response)
    } catch (e: Throwable) {
        response.error = e.stackTraceToString()
        response.writeTo(request.responseFile)
        exitProcess(1)
    }
    response.writeTo(request.responseFile)
}

internal fun run(request: FixerRequest, response: FixerResponse) {
    val (pairs, recorded) = RecordedDiagnostic.parseReports(request.reportFiles).partition { it.name == EXPECT_ACTUAL_PAIR }
    val diagnostics = recorded.redundantCopiesAsRemovals()
    if (diagnostics.isEmpty() && request.sourceFiles.isEmpty()) {
        return
    }

    KotlinFileParser().use { parser ->
        val copies = ExpectActualCopies(parser) { path -> Paths.get(path).takeIf { Files.isRegularFile(it) }?.let { SourceFile.read(it).text } }
            .copies(diagnostics, pairs, request::fixes)
        val diagnosticsByFile = (diagnostics + copies).groupBy { it.filePath }
        val fixer = ExemptionFixer(parser)
        for ((filePath, diagnostics) in diagnosticsByFile.toSortedMap()) {
            val file = Paths.get(filePath)
            if (!Files.isRegularFile(file)) {
                diagnostics.forEach {
                    response.skipped += SkippedDiagnostic(
                        it.name, filePath, 0, "the source file no longer exists", it.messageParameters,
                    )
                }
                continue
            }
            val source = SourceFile.read(file)
            fun fix(skipReason: String?): FileFixResult = fixer.fix(
                filePath,
                source.text,
                diagnostics,
                rewriteLocations = request.updateSources,
                fixes = request::fixes,
                skipReason = skipReason,
            )

            var fixResult = try {
                fix(
                    when {
                        request.isGenerated(file) ->
                            "`exemptExistingApi` doesn't edit a file in the build directory. Add the exemption in the generator of the file"
                        !source.isValidUtf8 -> "The file isn't valid UTF-8. Save the file as UTF-8"
                        request.updateSources && !Files.isWritable(file) -> "The file is read-only. Make the file writable"
                        else -> null
                    }
                )
            } catch (e: Exception) {
                System.err.println("$filePath: ${e.stackTraceToString()}")
                fix("`exemptExistingApi` can't edit the file because of an internal error. Add the exemptions by hand")
            }
            val newText = fixResult.newText
            if (request.updateSources && newText != null) {
                try {
                    replaceText(file, newText)
                    response.modifiedFiles += filePath
                } catch (e: IOException) {
                    fixResult = fix(e.writeFailureReason())
                }
            }
            response.applied += fixResult.applied
            response.skipped += fixResult.skipped
            response.warnings += fixResult.warnings
        }

        val scanner = ExemptionScanner(parser)
        request.sourceFiles.distinct().sorted().forEach { file ->
            if (Files.isRegularFile(file)) {
                response.exemptions += scanner.scan(file.toString(), SourceFile.read(file).text)
            }
        }
    }
}

/**
 * Replaces the file in one step, so a failure doesn't leave a part of [text] in the file. When the directory doesn't
 * accept a new file or the move fails, writes [text] into the file, and a failure of that write throws
 * [IncompleteWriteException].
 */
private fun replaceText(file: Path, text: String) {
    val target = file.toRealPath()
    val temporary = try {
        Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
    } catch (e: AccessDeniedException) {
        writeInPlace(target, text)
        return
    }
    try {
        Files.writeString(temporary, text)
        Files.getFileAttributeView(target, PosixFileAttributeView::class.java)?.let { attributes ->
            Files.setPosixFilePermissions(temporary, attributes.readAttributes().permissions())
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            // On Windows, the move fails while another process has the file open.
            Files.deleteIfExists(temporary)
            writeInPlace(target, text)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}

private class IncompleteWriteException(cause: IOException) : IOException(cause)

private fun writeInPlace(target: Path, text: String) {
    try {
        Files.writeString(target, text)
    } catch (e: IOException) {
        throw IncompleteWriteException(e)
    }
}

private fun IOException.writeFailureReason(): String {
    val incomplete = this is IncompleteWriteException
    val reason = ((if (incomplete) cause else this) as? FileSystemException)?.reason?.removeSuffix(".")
    val action = if (incomplete) {
        "The file can be incomplete. Restore the file from version control"
    } else {
        "Fix the cause and run `exemptExistingApi` again"
    }
    return "`exemptExistingApi` can't write the file${reason?.let { ": $it" }.orEmpty()}. $action"
}

private class SourceFile(val text: String, val isValidUtf8: Boolean) {
    companion object {
        fun read(file: Path): SourceFile {
            val bytes = Files.readAllBytes(file)
            // Replaces a malformed byte sequence as the compiler does.
            val text = String(bytes, Charsets.UTF_8)
            return SourceFile(text, bytes.contentEquals(text.toByteArray(Charsets.UTF_8)))
        }
    }
}
