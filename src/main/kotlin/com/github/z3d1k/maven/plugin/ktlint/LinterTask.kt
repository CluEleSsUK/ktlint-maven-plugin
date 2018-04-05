package com.github.z3d1k.maven.plugin.ktlint

import com.github.shyiko.ktlint.core.KtLint
import com.github.shyiko.ktlint.core.LintError
import com.github.z3d1k.maven.plugin.ktlint.reports.ReporterParameters
import com.github.z3d1k.maven.plugin.ktlint.reports.ReportsGenerator
import com.github.z3d1k.maven.plugin.ktlint.rules.resolveRuleSets
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.utils.io.FileUtils
import org.jetbrains.kotlin.backend.common.push
import java.io.File

@Mojo(name = "lint", defaultPhase = LifecyclePhase.VALIDATE)
class LinterTask : AbstractMojo() {
    @Parameter(defaultValue = "\${project}", readonly = true)
    private lateinit var mavenProject: MavenProject

    @Parameter
    private var includes: String = "src\\/**\\/*.kt"

    @Parameter
    private var excludes: String? = null

    @Parameter
    private var outputToConsole: Boolean = true

    @Parameter
    private var color: Boolean = false

    @Parameter
    private var groupByFile: Boolean = false

    @Parameter
    private var verbose: Boolean = false

    @Parameter
    private var parameters: Map<String, String> = emptyMap()

    @Parameter
    private var failOnError: Boolean = true

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun execute() {
        var consoleReporter: ReporterParameters? = null
        if (outputToConsole) {
            consoleReporter = ReporterParameters(
                "plain",
                System.out,
                mapOf(
                    "pad" to "true",
                    "verbose" to verbose.toString(),
                    "group_by_file" to groupByFile.toString(),
                    "color" to color.toString()
                )
            )
        }

        val reporterParameters = ReporterParameters.fromParametersMap(parameters)
        val reporterGenerator = ReportsGenerator(reporterParameters + listOfNotNull(consoleReporter))

        log.info("Ktlint lint task started")
        val lintResults = lint(mavenProject.basedir, includes, excludes)
        reporterGenerator.generateReports(lintResults)
        log.info("Ktlint lint task finished: ${lintResults.size} files was checked")

        reporterParameters.forEach { it.output.close() }

        val filesWithErrorsCount: Int = lintResults.filter { it.value.isNotEmpty() }.size
        val errorCount: Int = lintResults.entries.fold(0, { count, entry -> entry.value.count() + count })

        if (errorCount != 0) {
            log.error("Found $errorCount errors in $filesWithErrorsCount files")
            if (failOnError) {
                throw MojoExecutionException("Failed during ktlint execution: found $errorCount errors in $filesWithErrorsCount files")
            }
        }
    }

    private fun lint(
        baseDirectory: File,
        includes: String,
        excludes: String?,
        userProperties: Map<String, String> = emptyMap()
    ): Map<String, List<LintError>> {
        return baseDirectory
                .let { FileUtils.getFiles(it, includes, excludes, true) }
                .map { file ->
                    val eventList = mutableListOf<LintError>()
                    KtLint.lint(file.readText(), resolveRuleSets(), userProperties, { eventList.push(it) })
                    file.path to eventList
                }.toMap()
    }
}