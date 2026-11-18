package kotlinx.library.watchdog

import java.io.File
import java.io.Serializable
import java.util.concurrent.TimeUnit
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * The IDE project that the **Open in IDE** buttons of the HTML report open.
 *
 * @property name the IDE project name
 * @property rootDirectory the absolute path of the build root directory, with `/` separators
 */
@InternalWatchdogApi
public data class ApiWatchdogIdeProject(public val name: String, public val rootDirectory: String) : Serializable

/** What the reader does before the OS opens the `jetbrains://` links of the **Open in IDE** buttons. */
@InternalWatchdogApi
public enum class ApiWatchdogIdeLinkSetup {
    INSTALL_TOOLBOX,

    /** JetBrains Toolbox is installed on Linux, and no application handles the links. */
    REGISTER_TOOLBOX,
}

/** The [ApiWatchdogIdeLinkSetup] of this machine, or no value when the OS has a handler or the handler is unknown. */
@InternalWatchdogApi
public abstract class ApiWatchdogIdeLinkSetupSource : ValueSource<ApiWatchdogIdeLinkSetup, ValueSourceParameters.None> {
    override fun obtain(): ApiWatchdogIdeLinkSetup? =
        JetBrainsLinkHandler.setup(RunningIdes.Os.current, File(System.getProperty("user.home")))
}

/**
 * How the **Open in IDE** buttons open a source file.
 *
 * @property toolbox whether the buttons open `jetbrains://` links through JetBrains Toolbox. Otherwise they open
 *   `idea://open` links. Every IntelliJ IDEA and Android Studio on macOS opens them.
 * @property tags the Toolbox tags of the IDEs that the button can open. With two or more, the report shows a selector.
 * @property setup the step that the report shows above the entries, or null
 */
@InternalWatchdogApi
public data class ApiWatchdogIdeLinks(
    public val project: ApiWatchdogIdeProject,
    public val toolbox: Boolean,
    public val tags: List<String>,
    public val setup: ApiWatchdogIdeLinkSetup? = null,
) {
    @InternalWatchdogApi
    public companion object {
        /** The Toolbox tags and IDE names that the report selector lists. */
        public val SUPPORTED_TAGS: Map<String, String> = mapOf("idea" to "IntelliJ IDEA", "studio" to "Android Studio")

        /**
         * The links for the IDEs that run on this machine now. The IDEs with [project] open come first, then all
         * running IDEs. Without exactly one IDE tag, the reader selects the IDE in the report.
         */
        public fun detect(project: ApiWatchdogIdeProject, setup: ApiWatchdogIdeLinkSetup?): ApiWatchdogIdeLinks {
            val os = RunningIdes.Os.current
            val userHome = File(System.getProperty("user.home"))
            if (os == RunningIdes.Os.MAC && !RunningIdes.toolboxInstalled(userHome)) {
                return ApiWatchdogIdeLinks(project, toolbox = false, tags = emptyList())
            }
            return of(project, RunningIdes.find(os, userHome)).copy(setup = setup)
        }

        internal fun of(project: ApiWatchdogIdeProject, running: List<RunningIdes.Ide>): ApiWatchdogIdeLinks {
            val root = File(project.rootDirectory)
            val withProject = running.filter { ide -> ide.openProjects.any { root.startsWith(it) } }
            val tags = withProject.ifEmpty { running }.map { it.tag }.distinct()
            return ApiWatchdogIdeLinks(project, toolbox = true, tags = tags.singleOrNull()?.let(::listOf) ?: SUPPORTED_TAGS.keys.toList())
        }
    }
}

/**
 * Finds whether the OS has a handler for `jetbrains://` links. Only JetBrains Toolbox registers one. An IDE registers
 * a URL scheme on macOS only.
 */
internal object JetBrainsLinkHandler {
    const val MIME_TYPE = "x-scheme-handler/jetbrains"
    const val TOOLBOX_DESKTOP_ENTRY = "jetbrains-toolbox.desktop"
    private const val TIMEOUT_SECONDS = 5L

    class Result(val exitCode: Int, val output: String)

    /** @param run the result of a command, or null when the command can't start or doesn't end in time */
    fun setup(
        os: RunningIdes.Os,
        userHome: File,
        run: (List<String>) -> Result? = ::run,
        environment: (String) -> String? = System::getenv,
    ): ApiWatchdogIdeLinkSetup? = when (os) {
        // `detect` writes `idea://open` links without JetBrains Toolbox.
        RunningIdes.Os.MAC -> null
        RunningIdes.Os.LINUX -> {
            val handler = run(listOf("xdg-mime", "query", "default", MIME_TYPE))
            when {
                handler == null || handler.output.isNotBlank() -> null
                toolboxDesktopEntryExists(userHome, environment) -> ApiWatchdogIdeLinkSetup.REGISTER_TOOLBOX
                else -> ApiWatchdogIdeLinkSetup.INSTALL_TOOLBOX
            }
        }
        // `reg query` exits with 1 when the key is missing.
        RunningIdes.Os.WINDOWS -> when (run(listOf("reg", "query", "HKCR\\jetbrains"))?.exitCode) {
            1 -> ApiWatchdogIdeLinkSetup.INSTALL_TOOLBOX
            else -> null
        }
    }

    /** The directories of the [XDG Base Directory Specification](https://specifications.freedesktop.org/basedir-spec/latest/). */
    private fun toolboxDesktopEntryExists(userHome: File, environment: (String) -> String?): Boolean {
        fun variable(name: String) = environment(name)?.takeIf { it.isNotEmpty() }
        val dataHome = variable("XDG_DATA_HOME")?.let(::File) ?: File(userHome, ".local/share")
        val dataDirectories = (variable("XDG_DATA_DIRS") ?: "/usr/local/share:/usr/share").split(':').filter { it.isNotEmpty() }.map(::File)
        return (listOf(dataHome) + dataDirectories).any { File(it, "applications/$TOOLBOX_DESKTOP_ENTRY").isFile }
    }

    private fun run(command: List<String>): Result? = runCatching {
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        process.outputStream.close()
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        Result(process.exitValue(), process.inputStream.bufferedReader().use { it.readText() })
    }.getOrNull()
}

/**
 * Finds running IntelliJ IDEA and Android Studio instances through their processes. Every installation has a
 * `product-info.json` with the launcher path and the product code
 * ([the format](https://github.com/JetBrains/intellij-community/blob/master/platform/build-scripts/src/org/jetbrains/intellij/build/ProductInfoData.kt)).
 * A process is an IDE when its executable is that launcher, or the JVM of the installation with the IDE main class.
 */
internal object RunningIdes {
    /** An IDE with its JetBrains Toolbox tag and the directories of its open projects. */
    class Ide(val tag: String, val openProjects: List<File>)

    enum class Os(val productInfoName: String) {
        MAC("macOS"), LINUX("Linux"), WINDOWS("Windows");

        /** The directory with the configuration directories of the IDEs of [vendor], one for each `dataDirectoryName`. */
        fun configRoot(userHome: File, vendor: String): File = when (this) {
            MAC -> File(userHome, "Library/Application Support/$vendor")
            LINUX -> File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotEmpty() } ?: File(userHome, ".config").path, vendor)
            WINDOWS -> File(System.getenv("APPDATA") ?: File(userHome, "AppData/Roaming").path, vendor)
        }

        companion object {
            val current: Os = System.getProperty("os.name", "").lowercase().let {
                when {
                    it.startsWith("mac") -> MAC
                    it.startsWith("windows") -> WINDOWS
                    else -> LINUX
                }
            }
        }
    }

    private val TAGS = mapOf("IU" to "idea", "IC" to "idea", "IE" to "idea", "AI" to "studio")

    /** How far above its executable an installation's `product-info.json` can be: `Contents/jbr/Contents/Home/bin/java`. */
    private const val MAX_LEVELS = 5

    fun toolboxInstalled(userHome: File): Boolean =
        listOf(File("/Applications"), File(userHome, "Applications")).any { File(it, "JetBrains Toolbox.app").isDirectory }

    fun find(os: Os, userHome: File): List<Ide> =
        ProcessHandle.allProcesses().toList().mapNotNull { process ->
            val info = runCatching { process.info() }.getOrNull() ?: return@mapNotNull null
            val command = info.command().orElse(null) ?: return@mapNotNull null
            runCatching { ideOf(File(command), info.arguments().orElse(null)?.asList(), os, userHome) }.getOrNull()
        }

    /** The IDE that runs [command] with [arguments] as its main process, or null. [arguments] is null when the OS hides them. */
    fun ideOf(command: File, arguments: List<String>?, os: Os, userHome: File): Ide? {
        val infoFile = productInfoAbove(command) ?: return null
        val info = Json.parse(infoFile.readText()) as? Map<*, *> ?: return null
        val tag = TAGS[info["productCode"]] ?: return null
        val launch = (info["launch"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.firstOrNull { it["os"] == os.productInfoName } ?: return null
        fun path(key: String) = (launch[key] as? String)?.let { File(infoFile.parentFile, it) }
        val launcher = path("launcherPath")
        val java = path("javaExecutablePath")
        val mainClass = launch["mainClass"] as? String
        val isLauncher = launcher != null && sameFile(command, launcher)
        val isIdeJvm = java != null && mainClass != null && sameFile(command, java) && arguments.orEmpty().contains(mainClass)
        if (!isLauncher && !isIdeJvm) return null
        val vendor = (info["productVendor"] as? String)?.takeIf { it.isNotEmpty() } ?: "JetBrains"
        val projects = (info["dataDirectoryName"] as? String)
            ?.let { openProjects(File(os.configRoot(userHome, vendor), "$it/options/recentProjects.xml"), userHome) }
            .orEmpty()
        return Ide(tag, projects)
    }

    private fun productInfoAbove(command: File): File? {
        var directory: File? = command.absoluteFile.parentFile
        repeat(MAX_LEVELS) {
            val current = directory ?: return null
            for (candidate in listOf(File(current, "product-info.json"), File(current, "Resources/product-info.json"))) {
                if (candidate.isFile) return candidate
            }
            directory = current.parentFile
        }
        return null
    }

    private fun sameFile(a: File, b: File): Boolean =
        a.absoluteFile.normalize() == b.absoluteFile.normalize() ||
            runCatching { a.canonicalFile == b.canonicalFile }.getOrDefault(false)

    private val ENTRY = Regex("""<entry key="([^"]*)">(.*?)</entry>""", RegexOption.DOT_MATCHES_ALL)

    /** The projects that `recentProjects.xml` marks `opened="true"`. The IDE marks every project closed when it exits. */
    fun openProjects(recentProjects: File, userHome: File): List<File> {
        val text = runCatching { recentProjects.readText() }.getOrNull() ?: return emptyList()
        return ENTRY.findAll(text)
            .filter { """opened="true"""" in it.groupValues[2] }
            .map { File(unescapeXml(it.groupValues[1]).replace("\$USER_HOME\$", userHome.path)) }
            .toList()
    }

    private fun unescapeXml(value: String): String =
        value.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}

/** A JSON reader for `product-info.json`. Objects become maps, arrays lists, and numbers doubles. */
internal object Json {
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipWhitespace()
        require(reader.index == text.length) { "Unexpected text at ${reader.index}" }
        return value
    }

    private class Reader(val text: String) {
        var index = 0

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun value(): Any? {
            skipWhitespace()
            require(index < text.length) { "Unexpected end of JSON" }
            return when (val character = text[index]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> stringValue()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> {
                    require(character == '-' || character.isDigit()) { "Unexpected '$character' at $index" }
                    val start = index
                    while (index < text.length && text[index] in "+-.eE0123456789") index++
                    text.substring(start, index).toDouble()
                }
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            require(text.startsWith(word, index)) { "Unexpected text at $index" }
            index += word.length
            return value
        }

        private fun expect(character: Char) {
            skipWhitespace()
            require(index < text.length && text[index] == character) { "Expected '$character' at $index" }
            index++
        }

        private fun objectValue(): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            expect('{')
            skipWhitespace()
            if (text.getOrNull(index) == '}') return result.also { index++ }
            while (true) {
                skipWhitespace()
                val key = stringValue()
                expect(':')
                result[key] = value()
                skipWhitespace()
                if (text.getOrNull(index) == ',') index++ else break
            }
            expect('}')
            return result
        }

        private fun arrayValue(): List<Any?> {
            val result = ArrayList<Any?>()
            expect('[')
            skipWhitespace()
            if (text.getOrNull(index) == ']') return result.also { index++ }
            while (true) {
                result += value()
                skipWhitespace()
                if (text.getOrNull(index) == ',') index++ else break
            }
            expect(']')
            return result
        }

        private fun stringValue(): String {
            expect('"')
            val result = StringBuilder()
            while (true) {
                require(index < text.length) { "Unterminated string" }
                val character = text[index++]
                when (character) {
                    '"' -> return result.toString()
                    '\\' -> {
                        when (val escaped = text[index++]) {
                            'n' -> result.append('\n')
                            't' -> result.append('\t')
                            'r' -> result.append('\r')
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000C')
                            'u' -> {
                                result.append(text.substring(index, index + 4).toInt(16).toChar())
                                index += 4
                            }
                            else -> result.append(escaped)
                        }
                    }
                    else -> result.append(character)
                }
            }
        }
    }
}
