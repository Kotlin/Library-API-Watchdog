package kotlinx.library.watchdog

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RunningIdesTest {
    private val home = Files.createTempDirectory("running-ides").toFile()

    @AfterTest
    fun deleteHome() {
        home.deleteRecursively()
    }

    @Test
    fun `the launcher and the IDE JVM with the main class are the IDE and other JVMs of the installation are not`() {
        val app = installation("IntelliJ IDEA.app", productCode = "IU", vendor = null, dataDirectory = "IntelliJIdea2026.3")
        recentProjects(
            "JetBrains/IntelliJIdea2026.3",
            """
                <entry key="${'$'}USER_HOME${'$'}/work/a&amp;b"><value><RecentProjectMetaInfo opened="true" /></value></entry>
                <entry key="/work/closed"><value><RecentProjectMetaInfo /></value></entry>
            """,
        )
        val launcher = assertNotNull(ide(File(app, "Contents/MacOS/idea")))
        assertEquals("idea", launcher.tag)
        assertEquals(listOf(File(home, "work/a&b")), launcher.openProjects)
        val java = File(app, "Contents/jbr/Contents/Home/bin/java")
        assertNotNull(ide(java, listOf("-Xmx2g", "com.intellij.idea.Main")))
        assertNull(ide(java, listOf("org.gradle.launcher.daemon.bootstrap.GradleDaemon")))
        assertNull(ide(java, null))
        assertNull(ide(File(home, "other/bin/java")))
    }

    @Test
    fun `Android Studio reads its open projects from the configuration directory of its vendor`() {
        val app = installation("Android Studio.app", productCode = "AI", vendor = "Google", dataDirectory = "AndroidStudio2026.2")
        recentProjects("Google/AndroidStudio2026.2", """<entry key="/work/app"><value><RecentProjectMetaInfo opened="true" /></value></entry>""")
        val studio = assertNotNull(ide(File(app, "Contents/MacOS/idea")))
        assertEquals("studio", studio.tag)
        assertEquals(listOf(File("/work/app")), studio.openProjects)
    }

    @Test
    fun `IDEs without a Toolbox tag and IDEs without a recent projects file`() {
        val pyCharm = installation("PyCharm.app", productCode = "PY", vendor = null, dataDirectory = "PyCharm2026.3")
        assertNull(ide(File(pyCharm, "Contents/MacOS/idea")))
        val idea = installation("IDEA.app", productCode = "IC", vendor = null, dataDirectory = "IdeaIC2026.3")
        assertEquals(emptyList(), assertNotNull(ide(File(idea, "Contents/MacOS/idea"))).openProjects)
    }

    @Test
    fun `Linux needs a setup step only without a handler of jetbrains links`() {
        val commands = mutableListOf<List<String>>()
        fun setup(handler: String?, dataDirectories: String? = null) = JetBrainsLinkHandler.setup(
            RunningIdes.Os.LINUX,
            home,
            run = { command -> commands += command; handler?.let { JetBrainsLinkHandler.Result(0, it) } },
            environment = { name -> dataDirectories.takeIf { name == "XDG_DATA_DIRS" } },
        )
        assertNull(setup("jetbrains-toolbox.desktop\n"))
        assertNull(setup(handler = null))
        assertEquals(ApiWatchdogIdeLinkSetup.INSTALL_TOOLBOX, setup("\n"))
        assertEquals(listOf("xdg-mime", "query", "default", "x-scheme-handler/jetbrains"), commands.distinct().single())

        val shared = File(home, "shared")
        File(shared, "applications").mkdirs()
        File(shared, "applications/jetbrains-toolbox.desktop").writeText("[Desktop Entry]")
        assertEquals(ApiWatchdogIdeLinkSetup.INSTALL_TOOLBOX, setup(""))
        assertEquals(ApiWatchdogIdeLinkSetup.REGISTER_TOOLBOX, setup("", dataDirectories = "/missing:${shared.path}"))
        File(home, ".local/share/applications").mkdirs()
        File(home, ".local/share/applications/jetbrains-toolbox.desktop").writeText("[Desktop Entry]")
        assertEquals(ApiWatchdogIdeLinkSetup.REGISTER_TOOLBOX, setup(""))
    }

    @Test
    fun `Windows needs JetBrains Toolbox without the registry key of jetbrains links and macOS needs no step`() {
        fun setup(os: RunningIdes.Os, exitCode: Int?) =
            JetBrainsLinkHandler.setup(os, home, run = { exitCode?.let { JetBrainsLinkHandler.Result(it, "") } }, environment = { null })
        assertNull(setup(RunningIdes.Os.WINDOWS, 0))
        assertEquals(ApiWatchdogIdeLinkSetup.INSTALL_TOOLBOX, setup(RunningIdes.Os.WINDOWS, 1))
        assertNull(setup(RunningIdes.Os.WINDOWS, null))
        assertNull(setup(RunningIdes.Os.MAC, 1))
    }

    @Test
    fun `the JSON reader reads product-info values`() {
        val value = Json.parse("""{"a": [1, -2.5e1, true, false, null], "b": "q\"\\\/é\n", "c": {}, "d": []}""")
        assertEquals(
            mapOf("a" to listOf(1.0, -25.0, true, false, null), "b" to "q\"\\/é\n", "c" to emptyMap<String, Any?>(), "d" to emptyList<Any?>()),
            value,
        )
        for (invalid in listOf("", "{", "[1,", "{\"a\" 1}", "tru", "\"a", "1 2")) {
            assertFailsWith<RuntimeException>(invalid) { Json.parse(invalid) }
        }
    }

    private fun ide(command: File, arguments: List<String>? = emptyList()) =
        RunningIdes.ideOf(command, arguments, RunningIdes.Os.MAC, home)

    private fun installation(name: String, productCode: String, vendor: String?, dataDirectory: String): File {
        val app = File(home, "Applications/$name")
        val vendorField = if (vendor == null) "" else "\"productVendor\": \"$vendor\","
        File(app, "Contents/Resources/product-info.json").apply { parentFile.mkdirs() }.writeText(
            """
                {
                  "name": "$name", "productCode": "$productCode", $vendorField "dataDirectoryName": "$dataDirectory",
                  "launch": [
                    {"os": "Linux", "launcherPath": "bin/idea"},
                    {
                      "os": "macOS", "launcherPath": "../MacOS/idea", "javaExecutablePath": "../jbr/Contents/Home/bin/java",
                      "mainClass": "com.intellij.idea.Main", "additionalJvmArguments": ["-Dx=\"y\""]
                    }
                  ]
                }
            """.trimIndent(),
        )
        return app
    }

    private fun recentProjects(configDirectory: String, entries: String) {
        File(home, "Library/Application Support/$configDirectory/options/recentProjects.xml").apply { parentFile.mkdirs() }
            .writeText("<application><component><option name=\"additionalInfo\"><map>$entries</map></option></component></application>")
    }
}
