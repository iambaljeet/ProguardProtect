package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects Class.forName() calls where the referenced class is not protected by a ProGuard keep rule.
 *
 * Handles three patterns:
 * 1. Direct string literal: Class.forName("com.example.MyClass")
 * 2. Variable reference: Class.forName(className) where className is traceable to a string
 * 3. Package + class construction: strings like "com.example.models" + ".MyClass" or joinToString(".")
 *
 * For pattern 2 & 3, we scan the file for class name construction and cross-reference with
 * project source files to identify which classes could be targets.
 */
/**
 * Detects `Class.forName()` calls with dynamically constructed class name strings.
 *
 * Uses 4 strategies to find class names that R8 can't automatically adapt:
 * 1. String concatenation/interpolation in Class.forName()
 * 2. Variable-based Class.forName() where the variable is constructed earlier
 * 3. joinToString() patterns building class names from parts
 * 4. arrayOf() + joinToString() for complex multi-segment construction
 *
 * R8 auto-adapts literal strings in `Class.forName("literal")`, so only dynamic
 * constructions are flagged as vulnerable.
 */
class ReflectionClassAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.REFLECTION_CLASS_FOR_NAME

    // Pattern 1: Direct literal string in Class.forName("...")
    private val directForNamePattern = Regex(
        """Class\.forName\s*\(\s*"([^"]+)"\s*\)"""
    )

    // Pattern 2: Class.forName(variable) — variable reference, not a literal string
    private val variableForNamePattern = Regex(
        """Class\.forName\s*\(\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\)"""
    )

    // Pattern to find package-like string literals in a file (e.g., "app.proguard.models")
    private val packageStringPattern = Regex(
        """"([a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*){2,})""""
    )

    // Pattern to detect class name appended to package: "$pkg.ClassName" or pkg + ".ClassName"
    private val classAppendPattern = Regex(
        """["$]\{?\w+\}?\.([A-Z][a-zA-Z0-9]+)"""
    )

    // Pattern to detect string building like listOf("app", "proguard", "models").joinToString(".")
    // or arrayOf("app", "proguard", "models").joinToString(".")
    private val joinToStringPattern = Regex(
        """(?:listOf|arrayOf)\s*\(([^)]+)\)\s*(?:\.\s*(?:take|drop)\s*\(\d+\)\s*)?\.?\s*joinToString\s*\(\s*"\."""
    )

    // Pattern for array/list of strings (broader - for multi-step string building)
    private val stringArrayPattern = Regex(
        """(?:listOf|arrayOf)\s*\(([^)]+)\)"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        // Build a set of all known project class names for cross-referencing
        val allClassNames = sourceFiles.mapNotNull { inferFullClassName(it) }.toSet()

        for (source in sourceFiles) {
            val fullContent = source.lines.joinToString("\n")

            // Check Pattern 1: Direct string literal
            source.lines.forEachIndexed { index, line ->
                // Skip comment lines — KDoc/JavaDoc often contains Class.forName() examples
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                directForNamePattern.findAll(line).forEach { match ->
                    val className = match.groupValues[1]
                    if (!parser.isClassKept(className, keepRules)) {
                        issues.add(createIssue(source, index + 1, className))
                    }
                }
            }

            // Check Pattern 2 & 3: Variable-based Class.forName with dynamic class name construction
            val hasVariableForName = variableForNamePattern.containsMatchIn(fullContent)
            if (hasVariableForName) {
                val resolvedClassNames = resolveClassNames(fullContent, allClassNames)
                if (resolvedClassNames.isNotEmpty()) {
                    val forNameLine = source.lines.indexOfFirst {
                        variableForNamePattern.containsMatchIn(it)
                    }
                    for (className in resolvedClassNames) {
                        if (!parser.isClassKept(className, keepRules)) {
                            issues.add(createIssue(source, forNameLine + 1, className,
                                "(dynamically constructed) "))
                        }
                    }
                }
            }
        }

        return issues
    }

    private fun resolveClassNames(content: String, allClassNames: Set<String>): Set<String> {
        val resolved = mutableSetOf<String>()

        // Strategy 1: Find package string + class name append pattern
        // e.g., val pkg = "app.proguard.models" then "$pkg.UserProfile"
        val packageStrings = packageStringPattern.findAll(content).map { it.groupValues[1] }.toList()
        val appendedClasses = classAppendPattern.findAll(content).map { it.groupValues[1] }.toList()

        for (pkg in packageStrings) {
            for (cls in appendedClasses) {
                val fullName = "$pkg.$cls"
                if (fullName in allClassNames) {
                    resolved.add(fullName)
                }
            }
        }

        // Strategy 2: joinToString pattern like listOf("app", "proguard", "models").joinToString(".")
        joinToStringPattern.findAll(content).forEach { match ->
            val parts = match.groupValues[1]
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
            val basePkg = parts.joinToString(".")

            // Look for class name appended after
            for (cls in appendedClasses) {
                val fullName = "$basePkg.$cls"
                if (fullName in allClassNames) {
                    resolved.add(fullName)
                }
            }
        }

        // Strategy 3 (REMOVED): Previously this fell back to matching ALL classes in the detected
        // package prefix when no specific class name was found. This caused false positives for
        // files like CompanionReflectionCrash.kt that build class names from two variables
        // (e.g., "$pkg.$className"), since the package string was found but the appended class
        // (lowercase variable) wasn't matched by classAppendPattern. The broad fallback generated
        // an issue for every class in the package instead of just the intended one.
        // Solution: callers should use explicit class name patterns (Strategies 1, 2, 4) only.

        // Strategy 4: For complex patterns like arrayOf("app", "proguard", "models", "Plugin", "Registry")
        // where parts are combined with take/drop/joinToString in various ways
        if (resolved.isEmpty()) {
            stringArrayPattern.findAll(content).forEach { match ->
                val parts = match.groupValues[1]
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotEmpty() && !it.contains("(") }

                // Try all possible splits: package = parts[0..i].join("."), class = parts[i+1..].join("")
                for (splitAt in 1 until parts.size) {
                    val pkg = parts.take(splitAt).joinToString(".")
                    val cls = parts.drop(splitAt).joinToString("")
                    val fullName = "$pkg.$cls"
                    if (fullName in allClassNames) {
                        resolved.add(fullName)
                    }
                }
            }
        }

        return resolved
    }

    private fun inferFullClassName(source: SourceFile): String? {
        val packageLine = source.lines.firstOrNull { it.trimStart().startsWith("package ") } ?: return null
        val pkg = packageLine.trim().removePrefix("package ").removeSuffix(";").trim()
        // Extract class/object name from file content
        val classPattern = Regex("""(?:class|object|data\s+class|enum\s+class)\s+([A-Z][a-zA-Z0-9]+)""")
        val match = source.lines.firstNotNullOfOrNull { classPattern.find(it) } ?: return null
        return "$pkg.${match.groupValues[1]}"
    }

    private fun createIssue(
        source: SourceFile,
        lineNumber: Int,
        className: String,
        prefix: String = ""
    ): ProguardIssue {
        return ProguardIssue(
            type = issueType,
            severity = Severity.ERROR,
            filePath = source.file.path,
            lineNumber = lineNumber,
            message = "Class.forName() ${prefix}references \"$className\" - class is not protected by ProGuard keep rules. " +
                "R8 will rename or remove this class, causing ClassNotFoundException at runtime.",
            suggestion = "Add to proguard-rules.pro: -keep class $className { *; }",
            className = className
        )
    }
}
