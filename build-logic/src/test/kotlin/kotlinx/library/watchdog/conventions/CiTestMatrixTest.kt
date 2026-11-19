package kotlinx.library.watchdog.conventions

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CiTestMatrixTest {
    @Test
    fun `full matrix runs every test on every version`() {
        val matrix = fullFunctionalTestMatrix(versions("2.3.0", "2.2.21", "2.2.20"))

        assertEquals("versions=[\"2.2.20\",\"2.2.21\",\"2.3.0\"]\nsmoke-versions=", matrix.toGitHubOutput())
    }

    @Test
    fun `reduced matrix has one full version for each release line`() {
        val matrix = reducedFunctionalTestMatrix(
            versions(
                "2.2.20", "2.2.21", "2.3.0", "2.3.10", "2.3.20", "2.3.21", "2.4.0", "2.4.10", "2.4.20", "2.4.21",
                "2.5.0-Beta1", "2.5.0-Beta2", "2.5.0-RC",
            )
        )

        assertEquals(
            "versions=[\"2.2.20\",\"2.3.0\",\"2.3.20\",\"2.4.0\",\"2.4.21\",\"2.5.0-RC\"]\n" +
                "smoke-versions=2.2.21,2.3.10,2.3.21,2.4.10,2.4.20,2.5.0-Beta1,2.5.0-Beta2",
            matrix.toGitHubOutput(),
        )
    }

    @Test
    fun `line of the newest stable version has its newest version`() {
        val matrix = reducedFunctionalTestMatrix(versions("2.4.20", "2.4.21", "2.5.0", "2.5.10"))

        assertEquals(versions("2.4.20", "2.5.10"), matrix.full)
        assertEquals(versions("2.4.21", "2.5.0"), matrix.smoke)
    }

    @Test
    fun `lines without a stable version have their newest version`() {
        val matrix = reducedFunctionalTestMatrix(versions("2.5.0-Beta1", "2.5.0-RC", "2.5.20-Beta1", "2.5.20-Beta2"))

        assertEquals(versions("2.5.0-RC", "2.5.20-Beta2"), matrix.full)
    }

    @Test
    fun `shards have every item once`() {
        val items = (1..10).toList()

        assertEquals(listOf(1, 5, 9), items.shard("1/4"))
        assertEquals(items, (1..4).flatMap { items.shard("$it/4") }.sorted())
    }

    @Test
    fun `shard outside its count is an error`() {
        for (shard in listOf("0/4", "5/4", "1", "a/4", "1/4/2")) {
            assertFailsWith<IllegalArgumentException>(shard) { listOf(1).shard(shard) }
        }
    }

    private fun versions(vararg versions: String): List<KotlinToolingVersion> = versions.map(::KotlinToolingVersion)
}
