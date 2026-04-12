package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects Kotlin data classes whose auto-generated `copy()` and `componentN()` methods
 * are accessed via Kotlin reflection but may be removed by R8 in full mode.
 *
 * R8 full mode (default since AGP 8.0) can remove `copy()`, `component1()`, etc.
 * if they are never directly called in non-reflective compiled code. Frameworks that
 * use `KClass.memberFunctions.find { it.name == "copy" }` at runtime will encounter
 * NoSuchMethodException when R8 has stripped these methods.
 *
 * Also covers the related issue of KClass-based destructuring via componentN()
 * being used only in reflection code (e.g., state management, data mappers).
 *
 * References:
 * - https://github.com/Kotlin/kotlinx.serialization/issues/2312
 * - https://stackoverflow.com/questions/65748959/r8-removes-kotlin-data-class-copy-method
 * - https://issuetracker.google.com/issues/172677414
 */
class DataClassMemberAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.DATA_CLASS_MEMBER_STRIPPED

    /** Matches `data class Foo(...)` declarations. */
    private val dataClassPattern = Regex(
        """^\s*(?:internal\s+|private\s+|public\s+)?data\s+class\s+([A-Z][a-zA-Z0-9]*)"""
    )

    /** Matches reflective access to copy(), component1(), etc. via memberFunctions. */
    private val reflectiveCopyPattern = Regex(
        """memberFunctions\.find\s*\{[^}]*"copy"|memberFunctions\.find\s*\{[^}]*"component\d"""
    )

    /** Matches KFunction.call() on what might be a data class method. */
    private val kfunctionCallPattern = Regex(
        """\.call\s*\([^)]*copy|"copy"\s*\).*\.call|"component\d+"\s*\).*\.call"""
    )

    /** Matches direct memberFunctions access (broader pattern). */
    private val memberFunctionsPattern = Regex(
        """::class\.memberFunctions|\.memberFunctions\b"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        // First pass: collect data class declarations
        val dataClasses = mutableMapOf<String, Pair<SourceFile, Int>>() // simpleName → (file, line)
        for (source in sourceFiles) {
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                val match = dataClassPattern.find(line) ?: return@forEachIndexed
                dataClasses[match.groupValues[1]] = source to (index + 1)
            }
        }

        if (dataClasses.isEmpty()) return issues

        // Second pass: find files that use reflective copy()/componentN() on data classes
        for (source in sourceFiles) {
            val fullContent = source.lines.joinToString("\n")
            val hasReflectiveCopyUsage = reflectiveCopyPattern.containsMatchIn(fullContent) ||
                kfunctionCallPattern.containsMatchIn(fullContent) ||
                (memberFunctionsPattern.containsMatchIn(fullContent) &&
                    (fullContent.contains("\"copy\"") || fullContent.contains("\"component")))

            if (!hasReflectiveCopyUsage) continue

            // Find which data classes are referenced in this file
            for ((simpleName, entry) in dataClasses) {
                val (declFile, declLine) = entry
                if (!fullContent.contains(simpleName)) continue
                val fqn = "${declFile.packageName}.$simpleName"
                if (!parser.isClassKept(fqn, keepRules)) {
                    issues.add(
                        ProguardIssue(
                            type = IssueType.DATA_CLASS_MEMBER_STRIPPED,
                            severity = Severity.WARNING,
                            filePath = declFile.file.path,
                            lineNumber = declLine,
                            message = "Data class '$fqn' has copy()/componentN() methods accessed via " +
                                "Kotlin reflection (memberFunctions). R8 full mode removes unused generated " +
                                "methods → NoSuchMethodException at runtime.",
                            suggestion = "-keepclassmembers class $fqn { *; }",
                            className = fqn,
                            memberName = "copy"
                        )
                    )
                }
            }
        }
        return issues
    }
}
