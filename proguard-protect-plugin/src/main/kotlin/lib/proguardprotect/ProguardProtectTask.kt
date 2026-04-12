package lib.proguardprotect

import lib.proguardprotect.analyzers.*
import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceScanner
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

@CacheableTask
abstract class ProguardProtectTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirs: ConfigurableFileCollection

    @get:Internal
    abstract val proguardFiles: ConfigurableFileCollection

    @get:Input
    abstract val failOnError: Property<Boolean>

    @get:Input
    abstract val targetPackages: ListProperty<String>

    @get:Input
    abstract val variantName: Property<String>

    @get:OutputDirectory
    abstract val reportDir: DirectoryProperty

    init {
        group = "verification"
        description = "Analyzes source code for ProGuard/R8 vulnerabilities"
    }

    @TaskAction
    fun analyze() {
        val variant = variantName.get()
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logger.lifecycle("  ProguardProtect Analysis — variant: $variant")
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Parse all ProGuard rules files
        val rulesParser = ProguardRulesParser()
        val allRules = proguardFiles.files
            .filter { it.exists() }
            .flatMap { file ->
                logger.lifecycle("  Parsing rules: ${file.name}")
                rulesParser.parse(file)
            }
        logger.lifecycle("  Found ${allRules.size} keep rules")

        // Scan source files
        val scanner = SourceScanner()
        val allSources = sourceDirs.files
            .filter { it.exists() }
            .flatMap { dir ->
                logger.lifecycle("  Scanning sources: ${dir.path}")
                scanner.scanDirectory(dir)
            }
        logger.lifecycle("  Found ${allSources.size} source files")

        // Filter by target packages if specified
        val packages = targetPackages.get()
        val filteredSources = if (packages.isEmpty()) {
            allSources
        } else {
            allSources.filter { source ->
                packages.any { pkg -> source.packageName.startsWith(pkg) }
            }
        }

        // Also detect @Keep annotations in source files as implicit rules
        val keepAnnotatedClasses = findKeepAnnotatedClasses(filteredSources)
        val effectiveRules = allRules.toMutableList()
        keepAnnotatedClasses.forEach { className ->
            effectiveRules.add(
                lib.proguardprotect.utils.KeepRule(
                    directive = "-keep",
                    classPattern = className,
                    memberPatterns = listOf("*"),
                    rawText = "@Keep annotation on $className"
                )
            )
        }

        // Run all analyzers
        val analyzers = listOf(
            ReflectionClassAnalyzer(),
            ReflectionMethodAnalyzer(),
            SerializationAnalyzer(),
            EnumAnalyzer(),
            CallbackAnalyzer(),
            DevirtualizationAnalyzer()
        )

        val allIssues = mutableListOf<ProguardIssue>()
        for (analyzer in analyzers) {
            val issues = analyzer.analyze(filteredSources, effectiveRules)
            if (issues.isNotEmpty()) {
                logger.lifecycle("\n  ▸ ${analyzer.issueType}: ${issues.size} issue(s)")
            }
            allIssues.addAll(issues)
        }

        // Write report
        val reportFile = reportDir.file("proguard-protect-report-$variant.txt").get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(buildReport(variant, allIssues))

        // Summary
        val errors = allIssues.count { it.severity == ProguardIssue.Severity.ERROR }
        val warnings = allIssues.count { it.severity == ProguardIssue.Severity.WARNING }

        logger.lifecycle("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        if (allIssues.isEmpty()) {
            logger.lifecycle("  ✅ No ProGuard issues detected!")
        } else {
            logger.lifecycle("  ⚠️  Found $errors error(s), $warnings warning(s)")
            allIssues.forEach { issue ->
                logger.lifecycle("")
                logger.lifecycle("  ${issue}")
            }
        }
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logger.lifecycle("  Report: ${reportFile.absolutePath}")
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        if (errors > 0 && failOnError.get()) {
            throw GradleException(
                "ProguardProtect found $errors error(s) that will cause runtime crashes. " +
                    "See report: ${reportFile.absolutePath}"
            )
        }
    }

    private fun findKeepAnnotatedClasses(sources: List<lib.proguardprotect.utils.SourceFile>): List<String> {
        val keepPattern = Regex("""@Keep""")
        val classPattern = Regex("""(?:class|object|interface)\s+(\w+)""")
        val result = mutableListOf<String>()

        for (source in sources) {
            var hasKeep = false
            source.lines.forEach { line ->
                if (keepPattern.containsMatchIn(line)) {
                    hasKeep = true
                } else if (hasKeep && classPattern.find(line) != null) {
                    val className = classPattern.find(line)!!.groupValues[1]
                    result.add("${source.packageName}.$className")
                    hasKeep = false
                } else if (line.isNotBlank() && !line.trim().startsWith("//") && !line.trim().startsWith("@")) {
                    hasKeep = false
                }
            }
        }

        return result
    }

    private fun buildReport(variant: String, issues: List<ProguardIssue>): String {
        val sb = StringBuilder()
        sb.appendLine("ProguardProtect Analysis Report")
        sb.appendLine("Variant: $variant")
        sb.appendLine("=" .repeat(60))
        sb.appendLine()

        if (issues.isEmpty()) {
            sb.appendLine("No issues found.")
        } else {
            sb.appendLine("Found ${issues.size} issue(s):")
            sb.appendLine()
            issues.forEachIndexed { i, issue ->
                sb.appendLine("${i + 1}. $issue")
                sb.appendLine()
            }
        }

        return sb.toString()
    }
}
