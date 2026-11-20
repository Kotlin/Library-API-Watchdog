package kotlinx.library.watchdog.fixer

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExemptsFixerMainTest {
    private val tempDir = Files.createTempDirectory("fixer-main-test").toRealPath()
    private val reportFile = tempDir.resolve("report.tsv")
    private val reportLines = mutableListOf<String>()
    private val posix = "posix" in FileSystems.getDefault().supportedFileAttributeViews()

    @AfterTest
    fun tearDown() {
        val paths = Files.walk(tempDir).use { it.iterator().asSequence().toList() }
        paths.forEach { it.toFile().setWritable(true) }
        paths.asReversed().forEach { Files.deleteIfExists(it) }
    }

    private fun source(name: String, bytes: ByteArray, directory: String = "src"): Path =
        Files.createDirectories(tempDir.resolve(directory)).resolve(name).also { Files.write(it, bytes) }

    private fun source(name: String, text: String = API, directory: String = "src"): Path =
        source(name, text.toByteArray(), directory)

    private fun reportOpenClass(file: Path, compilerText: String = API) {
        val start = compilerText.indexOf("public open class")
        reportLines += "OPEN_API_WITHOUT_SUBCLASS_OPT_IN\t$file\t$start\t${compilerText.length}"
    }

    private fun runFixer(sourceFiles: List<Path> = emptyList()): FixerResponse {
        Files.write(reportFile, reportLines)
        val request = FixerRequest(
            listOf(reportFile), sourceFiles, emptyMap(), updateSources = true, tempDir.resolve("response.txt"),
            buildDirectory = tempDir.resolve("build"),
        )
        return FixerResponse().also { run(request, it) }
    }

    @Test
    fun `a file with a byte order mark is fixed after the mark`() {
        val file = source("Api.kt", BYTE_ORDER_MARK + API.toByteArray())
        reportOpenClass(file)

        val response = runFixer(sourceFiles = listOf(file))

        assertContentEquals(BYTE_ORDER_MARK + EXEMPTED_API.toByteArray(), Files.readAllBytes(file))
        assertEquals(listOf(AppliedExemption("IntentionallyOpen", file.toString(), 6)), response.exemptions)
        assertEquals(emptyList(), response.skipped)
    }

    @Test
    fun `a file that isn't valid UTF-8 is skipped and the other files are fixed`() {
        val valid = source("A.kt")
        val invalidBytes = "package p\n\n// caf".toByteArray() + 0xE9.toByte() + "\npublic open class Legacy\n".toByteArray()
        val invalid = source("B.kt", invalidBytes)
        reportOpenClass(valid)
        reportOpenClass(invalid, "package p\n\n// caf" + REPLACEMENT_CHARACTER + "\npublic open class Legacy\n")

        val response = runFixer(sourceFiles = listOf(valid, invalid))

        assertEquals(EXEMPTED_API, Files.readString(valid))
        assertContentEquals(invalidBytes, Files.readAllBytes(invalid))
        assertEquals(listOf(valid.toString()), response.modifiedFiles)
        val skipped = response.skipped.single()
        assertEquals(invalid.toString(), skipped.filePath)
        assertEquals(4, skipped.line)
        assertContains(skipped.reason, "UTF-8")
    }

    @Test
    fun `the exemptions of a file that isn't valid UTF-8 are listed`() {
        val bytes = "package p\n\nimport kotlinx.library.watchdog.*\n\n// caf".toByteArray() + 0xE9.toByte() +
                "\n@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)\npublic open class Legacy\n".toByteArray()
        val file = source("Legacy.kt", bytes)

        val response = runFixer(sourceFiles = listOf(file))

        assertEquals(listOf(AppliedExemption("IntentionallyOpen", file.toString(), 6)), response.exemptions)
    }

    @Test
    fun `a read-only file is skipped and the other files are fixed`() {
        val writable = source("A.kt")
        val readOnly = source("B.kt")
        reportOpenClass(writable)
        reportOpenClass(readOnly)
        assertTrue(readOnly.toFile().setWritable(false))

        val response = runFixer()

        assertEquals(EXEMPTED_API, Files.readString(writable))
        assertEquals(API, Files.readString(readOnly))
        assertEquals(listOf(writable.toString()), response.modifiedFiles)
        assertEquals(listOf(writable.toString()), response.applied.map { it.filePath })
        val skipped = response.skipped.single()
        assertEquals(readOnly.toString(), skipped.filePath)
        assertEquals(3, skipped.line)
        assertContains(skipped.reason, "read-only")
    }

    @Test
    fun `a file that the fixer fails on is skipped and the other files are fixed`() {
        val failing = source("A.kt", CONFLICTING)
        val fixed = source("B.kt")
        // The replacement and the removal of one annotation are overlapping edits.
        val start = CONFLICTING.indexOf("@SubclassOptInRequired")
        reportLines += "SUBCLASS_OPT_IN_WITHOUT_MARKERS\t$failing\t$start\t${CONFLICTING.length}"
        reportLines += "REDUNDANT_EXEMPTION\t$failing\t$start\t${CONFLICTING.length}"
        reportOpenClass(fixed)

        val response = runFixer()

        assertEquals(CONFLICTING, Files.readString(failing))
        assertEquals(EXEMPTED_API, Files.readString(fixed))
        assertEquals(listOf(fixed.toString()), response.modifiedFiles)
        assertEquals(listOf(fixed.toString()), response.applied.map { it.filePath })
        assertEquals(listOf("REDUNDANT_EXEMPTION", "SUBCLASS_OPT_IN_WITHOUT_MARKERS"), response.skipped.map { it.diagnostic }.sorted())
        response.skipped.forEach { skipped ->
            assertEquals(3, skipped.line)
            assertContains(skipped.reason, "internal error")
        }
    }

    @Test
    fun `a fixed file keeps its permissions and its directory gets no other file`() {
        if (!posix) return
        val file = source("Api.kt")
        val permissions = PosixFilePermissions.fromString("rw-r-----")
        Files.setPosixFilePermissions(file, permissions)
        reportOpenClass(file)

        runFixer()

        assertEquals(EXEMPTED_API, Files.readString(file))
        assertEquals(permissions, Files.getPosixFilePermissions(file))
        assertEquals(listOf(file), Files.list(file.parent).use { it.iterator().asSequence().toList() })
    }

    @Test
    fun `a symbolic link to a source file stays a link`() {
        if (!posix) return
        val target = source("Target.kt")
        val link = Files.createSymbolicLink(target.resolveSibling("Api.kt"), target.fileName)
        reportOpenClass(link)

        runFixer()

        assertTrue(Files.isSymbolicLink(link))
        assertTrue(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
        assertEquals(EXEMPTED_API, Files.readString(target))
    }

    @Test
    fun `a file in a read-only directory is fixed`() {
        if (!posix) return
        val file = source("Api.kt")
        reportOpenClass(file)
        assertTrue(file.parent.toFile().setWritable(false))

        val response = runFixer()

        assertEquals(EXEMPTED_API, Files.readString(file))
        assertEquals(listOf(file.toString()), response.modifiedFiles)
    }

    @Test
    fun `a file in the build directory is skipped`() {
        val generated = source("Generated.kt", directory = "build/generated")
        val written = source("Api.kt", directory = "build-logic")
        reportOpenClass(generated)
        reportOpenClass(written)

        val response = runFixer()

        assertEquals(API, Files.readString(generated))
        assertEquals(EXEMPTED_API, Files.readString(written))
        assertEquals(listOf(written.toString()), response.modifiedFiles)
        val skipped = response.skipped.single()
        assertEquals(generated.toString(), skipped.filePath)
        assertEquals(3, skipped.line)
        assertContains(skipped.reason, "build directory")
    }

    @Test
    fun `a file under a symbolic link into the build directory is skipped`() {
        if (!posix) return
        val generated = source("Generated.kt", directory = "build/generated")
        val link = Files.createSymbolicLink(Files.createDirectories(tempDir.resolve("src")).resolve("generated"), generated.parent)
        reportOpenClass(link.resolve("Generated.kt"))

        val response = runFixer()

        assertEquals(API, Files.readString(generated))
        assertContains(response.skipped.single().reason, "build directory")
    }

    @Test
    fun `a file in a build directory that is a symbolic link is skipped`() {
        if (!posix) return
        val generated = source("Generated.kt", directory = "elsewhere/build/generated")
        Files.createSymbolicLink(tempDir.resolve("build"), generated.parent.parent)
        reportOpenClass(generated)

        val response = runFixer()

        assertEquals(API, Files.readString(generated))
        assertContains(response.skipped.single().reason, "build directory")
    }

    private companion object {
        val BYTE_ORDER_MARK = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        val REPLACEMENT_CHARACTER = Char(0xFFFD)

        const val API = "package p\n\npublic open class Api\n"

        const val EXEMPTED_API = "package p\n\n" +
                "import kotlinx.library.watchdog.ExemptionReason\n" +
                "import kotlinx.library.watchdog.IntentionallyOpen\n\n" +
                "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                "public open class Api\n"

        const val CONFLICTING = "package p\n\n@SubclassOptInRequired\npublic open class Api\n"
    }
}
