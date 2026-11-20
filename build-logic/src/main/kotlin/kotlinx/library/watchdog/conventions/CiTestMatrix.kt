package kotlinx.library.watchdog.conventions

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

internal fun oneVersionOfEachReleaseLine(versions: Collection<KotlinToolingVersion>): List<KotlinToolingVersion> {
    val sorted = versions.sorted()
    val newestStable = sorted.lastOrNull { it.maturity == KotlinToolingVersion.Maturity.STABLE }
    return sorted
        // `2.4.0` and `2.4.10` are one release line. `2.4.20` and `2.4.21` are the next one.
        .groupBy { Triple(it.major, it.minor, it.patch / 20) }
        .values
        .map { line -> if (newestStable != null && line.last() < newestStable) line.first() else line.last() }
}

internal class FunctionalTestMatrix(val full: List<KotlinToolingVersion>, val smoke: List<KotlinToolingVersion>) {
    fun toGitHubOutput(): String = listOf(
        "versions=" + full.joinToString(prefix = "[", separator = ",", postfix = "]") { "\"$it\"" },
        "smoke-versions=" + smoke.joinToString(","),
    ).joinToString("\n")
}

internal fun fullFunctionalTestMatrix(versions: Collection<KotlinToolingVersion>): FunctionalTestMatrix =
    FunctionalTestMatrix(versions.sorted(), emptyList())

internal fun reducedFunctionalTestMatrix(versions: Collection<KotlinToolingVersion>): FunctionalTestMatrix {
    val full = oneVersionOfEachReleaseLine(versions)
    return FunctionalTestMatrix(full, versions.sorted() - full.toSet())
}

internal const val SHARD_PROPERTY = "watchdog.compilerTest.shard"

internal fun <T> List<T>.shard(shard: String): List<T> {
    val parts = shard.split('/').map(String::toIntOrNull)
    val index = parts.getOrNull(0)
    val count = parts.getOrNull(1)
    require(parts.size == 2 && index != null && count != null && index in 1..count) {
        "The Gradle property '$SHARD_PROPERTY' is '$shard'. Set it to '<index>/<count>', for example '1/4'."
    }
    return filterIndexed { position, _ -> position % count == index - 1 }
}
