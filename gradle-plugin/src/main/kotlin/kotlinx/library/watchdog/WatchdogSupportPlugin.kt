package kotlinx.library.watchdog

import java.io.File
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitSupportPlugin
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.plugin.FilesOptionKind
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetsContainer
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

/**
 * The `org.jetbrains.kotlin.library.api-watchdog` plugin.
 *
 * See [Configuration](https://kotlin.github.io/Library-API-Watchdog/configuration) for more details.
 */
@Suppress("unused")
public class WatchdogSupportPlugin : DevKitSupportPlugin(PluginInfo.PLUGIN_INFO) {
    private lateinit var collectDiagnosticsForExempts: Property<Boolean>
    private lateinit var enabled: Property<Boolean>
    private lateinit var exemptsTaskPaths: List<String>

    /** The compile tasks that have checks when the project turns explicit API mode on. */
    private val checkedCompileTaskPaths = mutableSetOf<String>()

    private val feedsReportByCompileTaskPath = mutableMapOf<String, Lazy<Boolean>>()

    @Volatile
    private var tasksInGraph: Set<String>? = null

    /**
     * KGP calls it after `afterEvaluate`, so `apiWatchdog.enabled` is final here. A raw
     * `-Xexplicit-api` flag turns explicit API mode on for test compilations too, so the
     * exclusion is here and not in the compiler plugin.
     */
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        enabled.get() && !kotlinCompilation.isTestCompilation()

    override fun apply(target: Project) {
        requireMinimumGradleVersion()
        target.configurations.register(EXEMPT_NON_TRANSITIVE_CONFIGURATION_NAME) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = false
            configuration.description = "Dependencies whose types the public API can expose " +
                    "although they aren't published transitively"
        }
        val extension = target.extensions.create(
            "apiWatchdog",
            WatchdogGradleExtension::class.java,
        )
        enabled = extension.enabled
        extension.reports.html.convention(true)
        extension.reports.htmlFile.convention(target.layout.buildDirectory.file(REPORT_FILE))
        extension.reports.sarif.convention(true)
        extension.reports.sarifFile.convention(target.layout.buildDirectory.file(SARIF_REPORT_FILE))
        val collectDiagnostics = target.objects.property(Boolean::class.java).convention(false)
        collectDiagnosticsForExempts = collectDiagnostics
        target.registerExemptionTasks(extension, collectDiagnostics)
        target.afterEvaluate { project ->
            if (!extension.enabled.get()) {
                project.addExemptionAnnotationsWithoutChecks()
                return@afterEvaluate
            }
            project.requireExplicitApiMode(extension.explicitApiRequirement.get())
            if (extension.suggestAbiValidation.get() && !project.hasAbiValidation()) {
                project.logger.warn(
                    abiValidationSuggestion(project.path, project.kotlinPluginVersion()),
                )
            }
        }
    }

    private fun Project.requireExplicitApiMode(requirement: ExplicitApiRequirement) {
        if (requirement == ExplicitApiRequirement.SKIP || hasExplicitApiMode()) return
        when (requirement) {
            ExplicitApiRequirement.WARN -> logger.warn(missingExplicitApiMessage(path, requirement))
            ExplicitApiRequirement.FAIL -> gradle.taskGraph.whenReady { graph ->
                // The exemption tasks turn explicit API mode on themselves.
                val checksSkipped = checkedCompileTaskPaths.any(graph::hasTask) && exemptsTaskPaths.none(graph::hasTask)
                if (checksSkipped) throw GradleException(missingExplicitApiMessage(path, requirement))
            }
            ExplicitApiRequirement.SKIP -> Unit
        }
    }

    /** KGP adds the annotations library only through [applyToCompilation], which a disabled watchdog never reaches. */
    private fun Project.addExemptionAnnotationsWithoutChecks() {
        fun NamedDomainObjectContainer<out KotlinCompilation<*>>.addAnnotations() = all { compilation ->
            if (compilation.isTestCompilation()) return@all
            compilation.defaultSourceSet.dependencies {
                info.companionLibraryCoordinates.forEach(::implementation)
            }
        }
        when (val kotlin = extensions.findByName("kotlin")) {
            is KotlinJvmExtension -> kotlin.target.compilations.addAnnotations()
            is KotlinAndroidExtension -> kotlin.target.compilations.addAnnotations()
            is KotlinTargetsContainer -> kotlin.targets.all { it.compilations.addAnnotations() }
        }
    }

    private fun Project.registerExemptionTasks(
        extension: WatchdogGradleExtension,
        collectDiagnostics: Property<Boolean>,
    ) {
        val enabled = extension.enabled
        val dependencyHandler = dependencies
        val kotlinVersion = provider { kotlinPluginVersion() }
        @Suppress("UnstableApiUsage")
        val rootDirectory = isolated.rootProject.projectDirectory.asFile
        val projectDirectoryFromRoot = layout.projectDirectory.asFile
            .relativeToOrSelf(rootDirectory).invariantSeparatorsPath
        @Suppress("UnstableApiUsage")
        val ideProject = ApiWatchdogHtmlReport.ideProject(providers, isolated.rootProject.projectDirectory)
        val diagnosticSeverities = extension.diagnosticSeverities.entries.fold(
            objects.mapProperty(String::class.java, WatchdogSeverity::class.java),
        ) { map, (diagnostic, severity) -> map.apply { put(diagnostic.name, severity) } }
            .apply { put(WatchdogChecks.REDUNDANT_EXEMPTION.name, WatchdogSeverity.WARNING) }
        val fixerDependency = dependencyHandler.create(
            "${info.artifact.groupId}:$FIXER_ARTIFACT_ID:${info.artifact.version}",
        )
        val kotlinCompilerDependency = kotlinVersion.map {
            dependencyHandler.create("org.jetbrains.kotlin:kotlin-compiler:$it")
        }
        val fixerArtifact = configurations.register(
            FIXER_ARTIFACT_CONFIGURATION_NAME,
            Action { configuration ->
                configuration.isCanBeConsumed = false
                configuration.isCanBeResolved = true
                configuration.isTransitive = false
                configuration.description = "The multi-version artifact of the watchdog exemption fixer"
                configuration.dependencies.add(fixerDependency)
            },
        )
        val fixerClasspath = configurations.register(
            FIXER_CLASSPATH_CONFIGURATION_NAME,
            Action { configuration ->
                configuration.isCanBeConsumed = false
                configuration.isCanBeResolved = true
                configuration.description = "The Kotlin PSI runtime for the watchdog exemption fixer"
                configuration.dependencies.addLater(kotlinCompilerDependency)
            },
        )

        fun <T : ApiWatchdogReportTask> registerReportTask(
            name: String,
            type: Class<T>,
            description: String,
        ): TaskProvider<T> = tasks.register(name, type) { task ->
            task.group = "api watchdog"
            task.onlyIf(DISABLED_TASK_REASON) { enabled.get() }
            task.description = description
            task.compilationNames.convention(emptyList())
            task.projectDirectory.set(layout.projectDirectory)
            task.buildDirectory.set(layout.buildDirectory)
            @Suppress("UnstableApiUsage")
            task.projectIdentityPath.set(isolated.buildTreePath)
            task.projectDirectoryFromRoot.set(projectDirectoryFromRoot)
            task.ideProject.set(ideProject)
            task.diagnosticSeverities.set(diagnosticSeverities)
            task.kotlinVersion.set(kotlinVersion)
            task.fixerArtifact.from(fixerArtifact)
            task.fixerClasspath.from(fixerClasspath)
            task.reports.html.convention(extension.reports.html)
            task.ideLinkSetup.set(ApiWatchdogHtmlReport.ideLinkSetup(providers, task.reports.html))
            task.reports.htmlFile.convention(extension.reports.htmlFile)
            task.reports.sarif.convention(extension.reports.sarif)
            task.reports.sarifFile.convention(extension.reports.sarifFile)
            task.reportDataFile.convention(layout.buildDirectory.file(REPORT_DATA_FILE))
        }
        val exemptTask = registerReportTask(
            EXEMPT_EXISTING_API_TASK_NAME,
            ExemptExistingApiTask::class.java,
            "Exempts every declaration with a watchdog error in every compilation except tests, with a " +
                    "FOR_BACKWARDS_COMPATIBILITY exemption or a placeholder KDoc. Writes the API Watchdog reports",
        )
        val reportTask = registerReportTask(
            GENERATE_REPORT_TASK_NAME,
            GenerateApiWatchdogReportTask::class.java,
            "Writes the API Watchdog reports: the applied exemptions and the diagnostics without one. " +
                    "Doesn't change any source",
        )
        // IDE sync realizes every task, so task realization can't be the signal.
        val exemptTaskPath = taskPath(EXEMPT_EXISTING_API_TASK_NAME)
        val exemptsTaskPaths = listOf(exemptTaskPath, taskPath(GENERATE_REPORT_TASK_NAME))
        this@WatchdogSupportPlugin.exemptsTaskPaths = exemptsTaskPaths
        val exemptInGraph = objects.property(Boolean::class.java).convention(false)
        gradle.taskGraph.whenReady { graph ->
            exemptInGraph.set(graph.hasTask(exemptTaskPath))
            val collecting = exemptsTaskPaths.any(graph::hasTask)
            collectDiagnostics.set(collecting)
            if (!collecting) return@whenReady
            for ((path, feedsReport) in feedsReportByCompileTaskPath) {
                if (!graph.hasTask(path) || !feedsReport.value) continue
                tasks.named(path.substringAfterLast(':'), KotlinCompilationTask::class.java).configure { task ->
                    task.compilerOptions.allWarningsAsErrors.set(false)
                }
            }
            // `graph.allTasks` fails under project isolation.
            val kotlin = extensions.findByName("kotlin") as? KotlinTargetsContainer
            tasksInGraph = kotlin?.targets.orEmpty().asSequence()
                .flatMap { it.compilations }
                .filter { graph.hasTask(taskPath(it.compileKotlinTaskName)) && runsCompileTask(it) }
                .mapTo(hashSetOf()) { taskPath(it.compileKotlinTaskName) }
        }
        // Both tasks write the same report files. The diagnostics of the compilation don't match the sources
        // that `exemptExistingApi` rewrote, so the report of that task stays.
        reportTask.configure { task ->
            task.mustRunAfter(exemptTask)
            task.onlyIf("exemptExistingApi isn't in the task graph") { !exemptInGraph.get() }
        }
        configurations.register(API_WATCHDOG_REPORT_ELEMENTS_CONFIGURATION) { configuration ->
            configuration.isCanBeConsumed = true
            configuration.isCanBeResolved = false
            configuration.description =
                "The API Watchdog report of this project"
            configuration.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                objects.named(Usage::class.java, API_WATCHDOG_REPORT_USAGE),
            )
            configuration.attributes.attribute(
                Category.CATEGORY_ATTRIBUTE,
                objects.named(Category::class.java, Category.DOCUMENTATION),
            )
            configuration.attributes.attribute(
                API_WATCHDOG_REPORT_ROOT_DIRECTORY_ATTRIBUTE,
                rootDirectory.absolutePath,
            )
        }
        // A disabled watchdog doesn't have a report file for aggregation.
        afterEvaluate {
            if (!enabled.get()) return@afterEvaluate
            configurations.named(API_WATCHDOG_REPORT_ELEMENTS_CONFIGURATION).configure { configuration ->
                configuration.outgoing.artifact(reportTask.flatMap { it.reportDataFile }) { artifact ->
                    artifact.builtBy(reportTask)
                }
            }
        }
    }

    override fun Project.applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val extension = extensions.getByType(WatchdogGradleExtension::class.java)
        val feedsReportWithoutTaskFlag = feedsReport(kotlinCompilation)
        val feedsReport = lazy {
            feedsReportWithoutTaskFlag ||
                    hasRawExplicitApiFlag(kotlinCompilation.compileTaskProvider.get().compilerOptions)
        }
        feedsReportByCompileTaskPath[taskPath(kotlinCompilation.compileKotlinTaskName)] = feedsReport
        val collect = collectDiagnosticsForExempts.map { it && feedsReport.value }
        // KGP already sets `-Xexplicit-api`. A different value causes a warning about repeated flags.
        // `feedsReport` reads the compiler arguments, so this value can't use it.
        val forceExplicitApi = collectDiagnosticsForExempts.map {
            it && feedsReportWithoutTaskFlag && !hasExplicitApiMode()
        }
        val compileDependencies = configurations.named(compileDependenciesName(kotlinCompilation))
        val transitiveDependencies = transitiveDependenciesFor(kotlinCompilation, compileDependencies)
        val exemptNonTransitiveDependencies = exemptNonTransitiveDependenciesFor(kotlinCompilation, compileDependencies)
        val reportFile = layout.buildDirectory.file(
            "reports/api-watchdog/diagnostics/${kotlinCompilation.compileKotlinTaskName}.tsv"
        )
        // `whenReady` sets `tasksInGraph`.
        val roles = lazy { if (collect.get()) sourceSetRoles(kotlinCompilation) else SourceSetRoles.NONE }

        if (feedsReportWithoutTaskFlag) checkedCompileTaskPaths += taskPath(kotlinCompilation.compileKotlinTaskName)
        val exemptTask = tasks.named(
            EXEMPT_EXISTING_API_TASK_NAME,
            ExemptExistingApiTask::class.java,
        )
        val reportTask = tasks.named(
            GENERATE_REPORT_TASK_NAME,
            GenerateApiWatchdogReportTask::class.java,
        )
        fun ApiWatchdogReportTask.addCompilation() {
            compilationNames.add(kotlinCompilation.compileKotlinTaskName)
            sourceFiles.from(kotlinCompilation.allKotlinSourceSets.map { it.kotlin })
            // The built-in Kotlin of AGP 9 doesn't register a Kotlin source set on the compilation.
            sourceFiles.from(kotlinCompilation.compileTaskProvider.map { task ->
                (task as? KotlinCompileTool)?.sources ?: objects.fileCollection()
            })
            diagnosticReports.from(reportFile)
            dependsOn(kotlinCompilation.compileTaskProvider)
        }
        exemptTask.configure { task -> if (feedsReport.value) task.addCompilation() }
        reportTask.configure { task -> if (feedsReport.value) task.addCompilation() }

        kotlinCompilation.compileTaskProvider.configure { task ->
            task.inputs.property("apiWatchdog.collectDiagnosticsForExempts", collect)
            // The report isn't a declared output because JVM compile tasks prepare every
            // extra output as a directory. So collection mode always runs and skips the
            // build cache.
            task.outputs.upToDateWhen { !collect.get() }
            task.outputs.doNotCacheIf("watchdog diagnostic collection is enabled") {
                collect.get()
            }
            task.compilerOptions.freeCompilerArgs.addAll(forceExplicitApi.map {
                if (it) listOf("-Xexplicit-api=warning") else emptyList()
            })
            task.doFirst {
                if (collect.get()) {
                    reportFile.get().asFile.apply {
                        parentFile.mkdirs()
                        writeText("")
                    }
                }
            }
        }

        // Native compile tasks read the option list at configuration time, before the task graph
        // is ready. So the set of options is fixed and each value that depends on collection is
        // lazy. The compiler plugin ignores a blank value.
        val checkDependencyExposure = extension.publicTypesMustBeTransitiveDependencies.map { it && !collect.get() }
        val compilesForAndroid = compileDependencies.map { kotlinCompilation.compilesForAndroid(it) }
        // The compiler plugin doesn't have an option for exempt dependencies, so they go into the
        // transitive paths.
        val transitiveFiles = files(providers.provider {
            if (!checkDependencyExposure.get()) return@provider emptyList<FileCollection>()
            val android = compilesForAndroid.get()
            listOf(
                dependencyFiles(transitiveDependencies.get(), android),
                dependencyFiles(exemptNonTransitiveDependencies.get(), android),
            )
        })
        fun artifactIds(configuration: NamedDomainObjectProvider<Configuration>): Provider<List<String>> =
            checkDependencyExposure.flatMap { check ->
                if (!check) return@flatMap providers.provider { emptyList<String>() }
                dependencyArtifacts(configuration.get(), compilesForAndroid.get()).resolvedArtifacts
                    .map { artifacts -> artifacts.map { it.id.displayName } }
            }
        // A path option isn't a task input, because a path is different in each checkout directory. The
        // compile classpath is an input of the task already, so the transitive dependencies are inputs by name.
        kotlinCompilation.compileTaskProvider.configure { task ->
            task.inputs.property("apiWatchdog.checkDependencyExposure", checkDependencyExposure)
            task.dependsOn(transitiveFiles)
            task.inputs.property("apiWatchdog.transitiveDependencies", artifactIds(transitiveDependencies))
            task.inputs.property(
                "apiWatchdog.exemptNonTransitiveDependencies",
                artifactIds(exemptNonTransitiveDependencies),
            )
        }
        return providers.provider {
            buildList {
                extension.diagnosticSeverities.forEach { (diagnostic, severity) ->
                    add(
                        SubpluginOption(
                            "diagnosticSeverity",
                            lazy {
                                val configured = severity.get()
                                val effective = if (collect.get() && configured != WatchdogSeverity.NONE) {
                                    WatchdogSeverity.WARNING
                                } else {
                                    configured
                                }
                                "${diagnostic.name}:${effective.name.lowercase()}"
                            },
                        ),
                    )
                }
                extension.annotationIgnoreRules().get().forEach { rule ->
                    add(SubpluginOption("ignoreWhenAnnotated", rule))
                }
                val internalApiAnnotations = extension.internalApiAnnotations.get()
                internalApiAnnotations.forEach { annotationName ->
                    require(ApiFilterRules.isUsableRule(annotationName)) {
                        "An API Watchdog internal API annotation must be a fully qualified name or a name " +
                                "pattern without whitespace and ':': '$annotationName'"
                    }
                }
                // KGP reads this provider after the build script of the project ran. A filter that an
                // `afterEvaluate` block adds later can be absent for a Native compilation.
                val filterRules =
                    if (extension.useAbiValidationFilters.get()) abiValidationFilterRules() else ApiFilterRules()
                (internalApiAnnotations + filterRules.excludedAnnotations).forEach {
                    add(SubpluginOption("internalApiAnnotation", it))
                }
                filterRules.excludedNames.forEach { add(SubpluginOption("excludedName", it)) }
                filterRules.includedNames.forEach { add(SubpluginOption("includedName", it)) }
                filterRules.includedAnnotations.forEach { add(SubpluginOption("includedAnnotation", it)) }
                add(
                    SubpluginOption(
                        "diagnosticsOutputFile",
                        lazy { if (collect.get()) escapeCommas(reportFile.get().asFile.path) else "" },
                    ),
                )
                add(SubpluginOption("exemptingExistingApi", lazy { collect.get().toString() }))
                add(
                    SubpluginOption(
                        "requireExplicitApi",
                        lazy { (extension.explicitApiRequirement.get() == ExplicitApiRequirement.FAIL).toString() },
                    ),
                )
                add(SubpluginOption("publicTypeWithInternalApi", lazy { extension.publicTypeWithInternalApi.get().toString() }))
                add(
                    SubpluginOption(
                        "reportUndocumentedClassProperties",
                        lazy { extension.reportUndocumentedClassProperties.get().toString() },
                    ),
                )
                add(SubpluginOption("ignoreDeprecatedApi", lazy { extension.ignoreDeprecatedApi.get().toString() }))
                add(
                    pathsOption("compileDependencyPaths") {
                        if (!checkDependencyExposure.get()) return@pathsOption ""
                        dependencyFiles(compileDependencies.get(), compilesForAndroid.get()).asPath
                    },
                )
                add(
                    pathsOption("transitiveDependencyPaths") {
                        transitiveFiles.files.joinToString(File.pathSeparator, transform = File::getPath)
                    },
                )
                add(
                    pathsOption("dependencyDeclarations") {
                        if (!checkDependencyExposure.get()) return@pathsOption ""
                        dependencyDeclarations(compileDependencies.get(), compilesForAndroid.get())
                    },
                )
                add(
                    SubpluginOption(
                        "sourceSetsCheckedElsewhere",
                        lazy { roles.value.checkedElsewhere.joinToString(":") },
                    ),
                )
                add(
                    SubpluginOption(
                        "sourceSetsWithPlatformChecks",
                        lazy { roles.value.platformChecks.joinToString(":") },
                    ),
                )
            }
        }
    }

    /**
     * The build script declaration of each artifact of [configuration], as tab-separated
     * `path`, `configuration`, `notation` triples. The declaration is the direct dependency with
     * the shortest resolution path to the component of the artifact.
     */
    private fun dependencyDeclarations(configuration: Configuration, android: Boolean): String {
        val declarations = HashMap<ComponentIdentifier, String>()
        val queue = ArrayDeque<Pair<ResolvedComponentResult, String>>()
        for (dependency in configuration.incoming.resolutionResult.root.dependencies) {
            if (dependency !is ResolvedDependencyResult) continue
            val declaration = declarationOf(dependency.requested, configuration) ?: continue
            queue.add(dependency.selected to declaration)
        }
        while (queue.isNotEmpty()) {
            val (component, declaration) = queue.removeFirst()
            if (declarations.putIfAbsent(component.id, declaration) != null) continue
            for (dependency in component.dependencies) {
                if (dependency is ResolvedDependencyResult) queue.add(dependency.selected to declaration)
            }
        }
        return dependencyArtifacts(configuration, android).artifacts.asSequence()
            .mapNotNull { artifact ->
                declarations[artifact.id.componentIdentifier]?.let { "${artifact.file.path}\t$it" }
            }
            .joinToString("\t")
    }

    /** The declaration of [requested] as a tab-separated configuration name and notation. */
    private fun declarationOf(requested: ComponentSelector, configuration: Configuration): String? {
        val notation: String
        val isDeclaredIn: (Dependency) -> Boolean
        when (requested) {
            is ProjectComponentSelector -> {
                notation = "project(\"${requested.projectPath}\")"
                isDeclaredIn = { it is ProjectDependency && it.path == requested.projectPath }
            }
            is ModuleComponentSelector -> {
                val version = requested.version.takeIf(String::isNotBlank)?.let { ":$it" }.orEmpty()
                notation = "\"${requested.group}:${requested.module}$version\""
                isDeclaredIn = {
                    it is ModuleDependency && it.group == requested.group && it.name == requested.module
                }
            }
            else -> return null
        }
        val declaringConfiguration = configuration.hierarchy
            .firstOrNull { it.dependencies.any(isDeclaredIn) }
            ?.name
            ?: "implementation"
        return "$declaringConfiguration\t$notation"
    }

    private fun Project.sourceSetRoles(kotlinCompilation: KotlinCompilation<*>): SourceSetRoles {
        val parents = kotlinCompilation.allKotlinSourceSets.map { it.name } - kotlinCompilation.defaultSourceSet.name
        val graph = tasksInGraph
        val kotlin = extensions.findByName("kotlin") as? KotlinTargetsContainer
        if (parents.isEmpty() || graph == null || kotlin == null ||
            taskPath(kotlinCompilation.compileKotlinTaskName) !in graph
        ) {
            return SourceSetRoles.NONE
        }
        val running = collectingCompilations(kotlin, graph)
        val order = ownerOrder()
        val checkedElsewhere = mutableListOf<String>()
        val platformChecks = mutableListOf<String>()
        for (sourceSet in parents) {
            // A metadata compilation gets its parent source sets as klibs (`-Xrefines-paths`), not as sources.
            val candidates = running.filter { compilation ->
                if (compilation.platformType == KotlinPlatformType.common) {
                    compilation.defaultSourceSet.name == sourceSet
                } else {
                    compilation.allKotlinSourceSets.any { it.name == sourceSet }
                }
            }
            val owner = candidates.minWithOrNull(order) ?: kotlinCompilation
            if (owner == kotlinCompilation) continue
            checkedElsewhere += sourceSet
            val platformOwner = candidates.filter { it.platformType != KotlinPlatformType.common }.minWithOrNull(order)
            if (platformOwner == kotlinCompilation) platformChecks += sourceSet
        }
        return SourceSetRoles(checkedElsewhere, platformChecks)
    }

    private fun Project.runsCompileTask(compilation: KotlinCompilation<*>): Boolean =
        tasks.findByName(compilation.compileKotlinTaskName)?.enabled == true &&
                // When the host can't compile a native target, KGP skips the compile task with `onlyIf`,
                // and `enabled` stays true.
                (compilation.platformType != KotlinPlatformType.native || compilation.target.publishable)

    private fun Project.collectingCompilations(
        kotlin: KotlinTargetsContainer,
        graph: Set<String>,
    ): List<KotlinCompilation<*>> =
        kotlin.targets.flatMap { it.compilations }.filter { compilation ->
            val compileTaskPath = taskPath(compilation.compileKotlinTaskName)
            // KGP skips the `main` metadata compilation (`onlyIf { false }`).
            val skippedMetadata = compilation.platformType == KotlinPlatformType.common &&
                    compilation.name == KotlinCompilation.MAIN_COMPILATION_NAME
            // The task graph check comes first, because a `feedsReport` value can realize the compile task.
            compileTaskPath in graph && isApplicable(compilation) && !skippedMetadata &&
                    feedsReportByCompileTaskPath[compileTaskPath]?.value == true
        }

    /**
     * Whether the compiler plugin checks [compilation], without the `-Xexplicit-api` flag of its compile task. The
     * function doesn't read the task, so the task stays unrealized.
     */
    private fun Project.feedsReport(compilation: KotlinCompilation<*>): Boolean {
        if (compilation.isTestCompilation()) return false
        val requirement = extensions.getByType(WatchdogGradleExtension::class.java).explicitApiRequirement.get()
        return requirement != ExplicitApiRequirement.FAIL || compilation.hasExplicitApiFromExtension() ||
                hasRawExplicitApiFlag() || hasRawExplicitApiFlag(compilation.target)
    }

    private fun Project.ownerOrder(): Comparator<KotlinCompilation<*>> = compareBy(
        { it.platformType != KotlinPlatformType.common },
        { it.platformType != KotlinPlatformType.jvm && it.platformType != KotlinPlatformType.androidJvm },
        { it.compilesForAndroid(configurations.getByName(it.compileDependencyConfigurationName)) },
        { it.target.name },
        { it.name != KotlinCompilation.MAIN_COMPILATION_NAME },
        { it.name },
    )

    /** The `com.android.kotlin.multiplatform.library` target of AGP 8 reports platform type `jvm`. */
    private fun KotlinCompilation<*>.compilesForAndroid(compileDependencies: Configuration): Boolean =
        platformType == KotlinPlatformType.androidJvm ||
                compileDependencies.attributes.getAttribute(TARGET_JVM_ENVIRONMENT_ATTRIBUTE)?.name ==
                TargetJvmEnvironment.ANDROID

    /**
     * The files of [configuration] as the compiler sees them. Without the `android-classes-jar`
     * view, a project AAR doesn't have an artifact. The view leaves a plain jar as it is.
     */
    private fun dependencyFiles(configuration: Configuration, android: Boolean): FileCollection =
        if (android) dependencyArtifacts(configuration, android).artifactFiles else configuration

    private fun dependencyArtifacts(configuration: Configuration, android: Boolean): ArtifactCollection =
        if (android) {
            configuration.incoming.artifactView { view ->
                view.attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ANDROID_CLASSES_JAR)
            }.artifacts
        } else {
            configuration.incoming.artifacts
        }

    private fun Project.transitiveDependenciesFor(
        kotlinCompilation: KotlinCompilation<*>,
        compileDependencies: Provider<Configuration>,
    ): NamedDomainObjectProvider<Configuration> {
        val publishedApi = configurations.named(publishedApiElementsName(kotlinCompilation))
        return registerCompilationClasspathView(
            kotlinCompilation,
            compileDependencies,
            suffix = "TransitiveDependencies",
            description = "Dependencies exposed transitively by ${kotlinCompilation.target.name} API elements",
        ) { configuration ->
            configuration.extendsFrom(publishedApi.get())
        }
    }

    private fun Project.exemptNonTransitiveDependenciesFor(
        kotlinCompilation: KotlinCompilation<*>,
        compileDependencies: Provider<Configuration>,
    ): NamedDomainObjectProvider<Configuration> {
        val exempt = configurations.named(EXEMPT_NON_TRANSITIVE_CONFIGURATION_NAME)
        return registerCompilationClasspathView(
            kotlinCompilation,
            compileDependencies,
            suffix = "ExemptNonTransitiveDependencies",
            description = "Dependencies whose types the public API of ${kotlinCompilation.target.name} can expose " +
                    "although they aren't published transitively",
        ) { configuration ->
            configuration.extendsFrom(exempt.get())
        }
    }

    /**
     * The compiler plugin matches dependency files by path, so a conflict-upgraded version on the
     * compile classpath must resolve to the same jar here.
     */
    private fun Project.registerCompilationClasspathView(
        kotlinCompilation: KotlinCompilation<*>,
        compileDependencies: Provider<Configuration>,
        suffix: String,
        description: String,
        configure: (Configuration) -> Unit,
    ): NamedDomainObjectProvider<Configuration> {
        val classifier = kotlinCompilation.target.disambiguationClassifier.orEmpty()
        val compilationName = (classifier + kotlinCompilation.name.replaceFirstChar(Char::uppercaseChar))
            .replaceFirstChar(Char::uppercaseChar)
        return configurations.register("apiWatchdog$compilationName$suffix") { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.description = description
            configure(configuration)
            val compileClasspath = compileDependencies.get()
            configuration.attributes.copyFrom(compileClasspath.attributes)
            @Suppress("UnstableApiUsage")
            configuration.shouldResolveConsistentlyWith(compileClasspath)
        }
    }

    private fun Project.compileDependenciesName(kotlinCompilation: KotlinCompilation<*>): String {
        // Before Kotlin 2.3.20, the configuration of an `androidTarget()` compilation lacks the
        // build-type and flavor dependencies. The classpath of the Android variant has them.
        val androidCompileClasspathName = "${kotlinCompilation.name}CompileClasspath"
        return if (
            kotlinCompilation.platformType == KotlinPlatformType.androidJvm &&
            androidCompileClasspathName in configurations.names
        ) {
            androidCompileClasspathName
        } else {
            kotlinCompilation.compileDependencyConfigurationName
        }
    }

    private fun Project.publishedApiElementsName(kotlinCompilation: KotlinCompilation<*>): String {
        // The target-level configuration of Android lacks the build-type and flavor `api`
        // dependencies. A non-library Android variant doesn't have API elements of its own.
        // In a Kotlin/JVM project, a Java feature such as the test fixtures has its own API elements.
        val ownApiElementsName = "${kotlinCompilation.name}ApiElements"
        val canHaveOwnApiElements = kotlinCompilation.platformType == KotlinPlatformType.androidJvm ||
                extensions.findByName("kotlin") is KotlinJvmExtension
        return if (canHaveOwnApiElements && ownApiElementsName in configurations.names) {
            ownApiElementsName
        } else {
            kotlinCompilation.target.apiElementsConfigurationName
        }
    }

    private fun Project.taskPath(taskName: String): String = if (path == ":") ":$taskName" else "$path:$taskName"

    private class SourceSetRoles(val checkedElsewhere: List<String>, val platformChecks: List<String>) {
        companion object {
            val NONE = SourceSetRoles(emptyList(), emptyList())
        }
    }

    private fun AttributeContainer.copyFrom(source: AttributeContainer) {
        for (key in source.keySet()) {
            @Suppress("UNCHECKED_CAST")
            key as Attribute<Any>
            source.getAttribute(key)?.let { attribute(key, it) }
        }
    }

    internal companion object {
        private const val EXEMPT_EXISTING_API_TASK_NAME = "exemptExistingApi"
        private const val GENERATE_REPORT_TASK_NAME = "generateApiWatchdogReport"
        private const val REPORT_FILE = "reports/api-watchdog/report.html"
        private const val SARIF_REPORT_FILE = "reports/api-watchdog/report.sarif"
        private const val REPORT_DATA_FILE = "reports/api-watchdog/report.data"
        private const val ANDROID_CLASSES_JAR = "android-classes-jar"
        internal const val EXEMPT_NON_TRANSITIVE_CONFIGURATION_NAME = "apiWatchdogExemptNonTransitive"

        private const val TEST_COMPILATION_NAME_SUFFIX = "Test"

        /**
         * Android names a test compilation after its variant, so only the suffix is common. Test
         * fixtures are published with the library, so they aren't test compilations.
         */
        private fun KotlinCompilation<*>.isTestCompilation(): Boolean =
            name == KotlinCompilation.TEST_COMPILATION_NAME || name.endsWith(TEST_COMPILATION_NAME_SUFFIX)

        private const val FIXER_ARTIFACT_ID = "library-api-watchdog-exempts-fixer"
        private const val FIXER_CLASSPATH_CONFIGURATION_NAME = "apiWatchdogExemptsFixerClasspath"
        private const val FIXER_ARTIFACT_CONFIGURATION_NAME = "apiWatchdogExemptsFixerArtifact"
        private const val BCV_PLUGIN_ID = "org.jetbrains.kotlinx.binary-compatibility-validator"

        private const val DISABLED_TASK_REASON = "apiWatchdog.enabled is true"

        /** The first version with `ProjectDependency.getPath`. */
        private val MINIMUM_GRADLE_VERSION = GradleVersion.version("8.11")

        private fun requireMinimumGradleVersion() {
            val current = GradleVersion.current()
            if (current.baseVersion >= MINIMUM_GRADLE_VERSION) return
            throw GradleException(
                "library-api-watchdog needs Gradle ${MINIMUM_GRADLE_VERSION.version} or later, and this build " +
                        "runs on Gradle ${current.version}. Update the Gradle version of the build.",
            )
        }

        /** A comma splits a `-P` compiler argument. The compiler plugin restores the value. */
        private fun escapeCommas(value: String): String = value.replace("%", "%25").replace(",", "%2C")

        /** KGP doesn't add a [FilesSubpluginOption] to the task inputs. */
        private fun pathsOption(key: String, paths: () -> String): SubpluginOption =
            FilesSubpluginOption(key, emptyList(), FilesOptionKind.INTERNAL, lazy { escapeCommas(paths()) })

        private fun Project.kotlinPluginVersion(): String =
            plugins.withType(KotlinBasePlugin::class.java)
                .firstOrNull()?.pluginVersion
                ?: error("Apply the Kotlin Gradle plugin together with library-api-watchdog")

        /** Reads the extension, not the compile tasks, so the tasks stay unrealized. */
        private fun Project.hasExplicitApiMode(): Boolean {
            val kotlin = extensions.findByName("kotlin") as? KotlinBaseExtension ?: return false
            val mode = kotlin.explicitApi
            if (mode != null && mode != ExplicitApiMode.Disabled) return true
            return hasRawExplicitApiFlag()
        }

        private fun Project.hasRawExplicitApiFlag(): Boolean = hasRawExplicitApiFlag(extensions.findByName("kotlin"))

        private fun hasRawExplicitApiFlag(extensionOrTarget: Any?): Boolean =
            hasRawExplicitApiFlag((extensionOrTarget as? HasConfigurableKotlinCompilerOptions<*>)?.compilerOptions)

        private fun hasRawExplicitApiFlag(options: KotlinCommonCompilerOptions?): Boolean =
            options?.freeCompilerArgs?.orNull.orEmpty().any {
                it.startsWith("-Xexplicit-api=") && it != "-Xexplicit-api=disable"
            }

        /**
         * The rule of `AbstractKotlinCompileConfig.explicitApiMode()` in KGP. Every other
         * compilation, such as `testFixtures`, gets `ExplicitApiMode.Disabled`.
         */
        private fun KotlinCompilation<*>.hasExplicitApiFromExtension(): Boolean =
            name == KotlinCompilation.MAIN_COMPILATION_NAME ||
                    target.platformType == KotlinPlatformType.common ||
                    target.platformType == KotlinPlatformType.androidJvm

        private fun Project.hasAbiValidation(): Boolean =
            pluginManager.hasPlugin(BCV_PLUGIN_ID) || activeBinaryCompatibilityValidator() != null ||
                    activeKotlinAbiValidation() != null

        private fun missingExplicitApiMessage(projectPath: String, requirement: ExplicitApiRequirement): String {
            val hint = if (requirement == ExplicitApiRequirement.FAIL) {
                "\n|\n|To run the checks without explicit API mode, set `explicitApiRequirement` in the `apiWatchdog`\n" +
                        "|block to `ExplicitApiRequirement.WARN` or `ExplicitApiRequirement.SKIP`."
            } else {
                ""
            }
            return """
                |Project '$projectPath' applies library-api-watchdog but doesn't enable explicit API mode.
                |Enable it in the build script of the module:
                |
                |    kotlin {
                |        explicitApi()
                |    }
                |
                |The `explicitApiWarning()` variant and the `-Xexplicit-api` compiler flag also count.$hint
            """.trimMargin()
        }

        private fun abiValidationSuggestion(projectPath: String, kotlinVersion: String): String {
            val setupSnippet = if (kotlinVersion.usesLegacyAbiValidationDsl()) {
                """
                    import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

                    kotlin {
                        @OptIn(ExperimentalAbiValidation::class)
                        abiValidation {
                            enabled.set(true)
                        }
                    }
                """.trimIndent()
            } else {
                """
                    import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

                    kotlin {
                        @OptIn(ExperimentalAbiValidation::class)
                        abiValidation()
                    }
                """.trimIndent()
            }.prependIndent("    ")
            return """
            |Project '$projectPath' applies library-api-watchdog but doesn't enable binary compatibility validation.
            |The watchdog checks the shape of new API. Binary compatibility validation compares each build with
            |a committed dump of the released API and catches a breaking change. Enable the ABI validation of
            |the Kotlin Gradle plugin in the build script of the module:
            |
            |$setupSnippet
            |
            |Configuration reference: https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html
            |On an older Kotlin version, apply the Binary Compatibility Validator plugin instead:
            |https://github.com/Kotlin/binary-compatibility-validator
            |
            |Disable this suggestion with `apiWatchdog { suggestAbiValidation = false }`.
            """.trimMargin()
        }

        private fun String.usesLegacyAbiValidationDsl(): Boolean {
            val components = substringBefore('-').split('.')
            val major = components.getOrNull(0)?.toIntOrNull() ?: return false
            val minor = components.getOrNull(1)?.toIntOrNull() ?: return false
            return major == 2 && minor in 2..3
        }
    }
}
