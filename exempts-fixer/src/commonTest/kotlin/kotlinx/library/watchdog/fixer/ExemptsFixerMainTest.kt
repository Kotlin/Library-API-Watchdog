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

    private companion object {
        val BYTE_ORDER_MARK = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        val REPLACEMENT_CHARACTER = Char(0xFFFD)

        const val API = "package p\n\npublic open class Api\n"

        const val EXEMPTED_API = "package p\n\n" +
                "import kotlinx.library.watchdog.ExemptionReason\n" +
                "import kotlinx.library.watchdog.IntentionallyOpen\n\n" +
                "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                "public open class Api\n"
    }
}
