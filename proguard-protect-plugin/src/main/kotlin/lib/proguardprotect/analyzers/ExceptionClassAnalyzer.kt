package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects custom exception classes whose qualified names are stored as string constants
 * and later loaded via `Class.forName()` or similar dynamic lookup.
 *
 * Error reporting and retry frameworks commonly store exception class names as strings
 * (e.g., in SharedPreferences, databases, or remote configs). After R8 renames those
 * exception classes, the stored names no longer resolve — `ClassNotFoundException` is thrown.
 *
 * Detection strategy:
 * 1. Find classes extending `Exception`, `RuntimeException`, or `Throwable`
 * 2. Check if the class's fully-qualified name appears as a string constant anywhere in
 *    the project source (e.g., `private const val EXCEPTION_CLASS = "app.proguard.models.NetworkException"`)
 * 3. Post-build: confirmed if the exception class was renamed in mapping.txt
 *
 * Ref: https://issuetracker.google.com/issues/353143650
 */
class ExceptionClassAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.EXCEPTION_CLASS_RENAMED

    // Match the start of an exception class declaration (class name extraction)
    private val classDeclStartPattern = Regex(
        """(?:^|\s)class\s+([A-Z][a-zA-Z0-9]+)"""
    )

    // Match a supertype that is a known exception base class (can appear on a later line)
    private val exceptionSuperPattern = Regex(
        """:\s*(?:RuntimeException|Exception|Throwable|IllegalStateException|IllegalArgumentException|IOException)"""
    )

    // Match string constant containing a fully-qualified class name
    private val fqcnStringPattern = Regex(
        """"((?:[a-z][a-z0-9]*\.){2,}[A-Z][a-zA-Z0-9]*)""""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        // First pass: collect all exception classes (simpleName → fqcn).
        // Handles multi-line class declarations by checking the next 5 lines after
        // the class header for a supertype that is an exception base class.
        val exceptionClasses = mutableMapOf<String, String>()
        for (source in sourceFiles) {
            val pkg = source.packageName
            val lines = source.lines
            lines.forEachIndexed { i, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                val classMatch = classDeclStartPattern.find(line) ?: return@forEachIndexed
                val simpleName = classMatch.groupValues[1]

                // Scan this line and the next few lines for a known exception supertype
                val lookaheadEnd = minOf(i + 6, lines.size)
                val snippet = lines.subList(i, lookaheadEnd).joinToString(" ")
                if (exceptionSuperPattern.containsMatchIn(snippet)) {
                    exceptionClasses[simpleName] = "$pkg.$simpleName"
                }
            }
        }

        if (exceptionClasses.isEmpty()) return issues

        // Build set of all exception FQCNs for string-matching
        val exceptionFqcns = exceptionClasses.values.toSet()

        // Second pass: find exception FQCNs stored as string literals in any source file
        for (source in sourceFiles) {
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                fqcnStringPattern.findAll(line).forEach { match ->
                    val referencedFqcn = match.groupValues[1]
                    if (referencedFqcn in exceptionFqcns && !parser.isClassKept(referencedFqcn, keepRules)) {
                        issues.add(
                            ProguardIssue(
                                type = issueType,
                                severity = Severity.ERROR,
                                filePath = source.file.path,
                                lineNumber = index + 1,
                                message = "Exception class name \"$referencedFqcn\" is stored as a string constant. " +
                                    "R8 renames this class (it extends Exception/RuntimeException/Throwable), so the " +
                                    "string-based lookup will fail with ClassNotFoundException at runtime. " +
                                    "Common in error reporting frameworks that reconstruct exceptions by class name.",
                                suggestion = "Add to proguard-rules.pro: -keep class $referencedFqcn { *; }",
                                className = referencedFqcn
                            )
                        )
                    }
                }
            }
        }

        return issues
    }
}
