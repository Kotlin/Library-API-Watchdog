@file:JvmName("CorpusAudit")
@file:Suppress("KotlinPrintToLogpoint")

package kotlinx.library.watchdog.benchmark

import java.nio.file.Files
import kotlin.io.path.readLines
import kotlinx.library.watchdog.fir.WatchdogDiagnostics
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services

/**
 * Compiles the corpus with every configurable diagnostic at `warning` and prints the count of each diagnostic.
 * Run it with the Gradle `benchmarkCorpusAudit` task after a change to the corpus templates.
 */
fun main(args: Array<String>) {
    val fileCount = args.firstOrNull()?.toInt() ?: 200
    require(fileCount >= BenchmarkCorpus.templateCount) {
        "The corpus audit needs at least ${BenchmarkCorpus.templateCount} files, one for each template. It has $fileCount."
    }
    setIdeaIoUseFallback()

    val sourceRoot = Files.createTempDirectory("watchdog-audit-src")
    val outputDir = Files.createTempDirectory("watchdog-audit-out")
    val report = Files.createTempFile("watchdog-audit", ".tsv")
    try {
        BenchmarkCorpus.generate(sourceRoot, fileCount)
        val collector = ErrorSummaryMessageCollector()
        val arguments = corpusCompilerArguments(sourceRoot, outputDir).apply {
            pluginClasspaths = arrayOf(BenchmarkEnv.pluginJar)
            val everyDiagnostic = WatchdogDiagnostics.allDiagnostics.mapTo(hashSetOf()) { it.name }
            pluginOptions = (
                corpusPluginOptions(everyDiagnostic, withDependencyPaths = true) +
                    pluginOption("diagnosticsOutputFile", report.toAbsolutePath().toString())
                ).toTypedArray()
        }

        val exitCode = K2JVMCompiler().exec(collector, Services.EMPTY, arguments)
        check(exitCode == ExitCode.OK) {
            "Corpus audit compilation failed ($exitCode):\n${collector.summary()}"
        }

        val counts = report.readLines()
            .filter { it.isNotBlank() }
            .groupingBy { it.substringBefore('\t') }
            .eachCount()
            .toSortedMap()

        println("Corpus: $fileCount files. Diagnostics reported:")
        val width = counts.keys.maxOfOrNull { it.length } ?: 0
        for ((name, count) in counts) {
            println("  ${name.padEnd(width)}  $count")
        }

        val silent = (WatchdogDiagnostics.allDiagnostics.map { it.name } + WatchdogDiagnostics.REDUNDANT_EXEMPTION.name)
            .filter { it !in counts }
        check(silent.isEmpty()) {
            "The corpus doesn't trigger ${silent.joinToString()}. " +
                "Add a shape to `BenchmarkCorpus` that triggers each diagnostic."
        }
    } finally {
        deleteRecursively(sourceRoot)
        deleteRecursively(outputDir)
        Files.deleteIfExists(report)
    }
}
