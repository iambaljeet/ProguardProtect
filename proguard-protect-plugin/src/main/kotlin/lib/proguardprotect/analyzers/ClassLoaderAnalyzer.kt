package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects `ClassLoader.loadClass()` calls with string class name arguments.
 *
 * This pattern has the same runtime failure mode as `Class.forName()` but is
 * not caught by tools that only scan for `forName`. When R8 renames or removes
 * the target class, `loadClass()` throws `ClassNotFoundException` at runtime.
 *
 * Detected patterns:
 * - `.loadClass("fully.qualified.ClassName")`
 * - `contextClassLoader?.loadClass(`
 * - `Thread.currentThread().contextClassLoader.loadClass(`
 * - Any `.loadClass("app.proguard.` prefix
 *
 * Ref: https://r8.googlesource.com/r8/+/refs/heads/master/compatibility-faq.md
 */
class ClassLoaderAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.CLASSLOADER_LOADCLASS

    // Match .loadClass("fully.qualified.ClassName")
    private val loadClassLiteralPattern = Regex(
        """\.loadClass\s*\(\s*"([^"]+)"\s*\)"""
    )

    // Match .loadClass(variable) — variable reference, not a literal
    private val loadClassVariablePattern = Regex(
        """\.loadClass\s*\(\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\)"""
    )

    // Package-like string literal that might be a class name argument
    private val packageStringPattern = Regex(
        """"([a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*){2,})""""
    )

    // Class name appended to a variable: "$pkg.ClassName" or pkg + ".ClassName"
    private val classAppendPattern = Regex(
        """["$]\{?\w+\}?\.([A-Z][a-zA-Z0-9]+)"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()
        val allClassNames = sourceFiles.mapNotNull { inferFullClassName(it) }.toSet()

        for (source in sourceFiles) {
            val fullContent = source.lines.joinToString("\n")

            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                // Pattern 1: literal string argument
                loadClassLiteralPattern.findAll(line).forEach { match ->
                    val className = match.groupValues[1]
                    if (!parser.isClassKept(className, keepRules)) {
                        issues.add(createIssue(source, index + 1, className))
                    }
                }
            }

            // Pattern 2: variable argument — resolve dynamically constructed class names
            if (loadClassVariablePattern.containsMatchIn(fullContent)) {
                val resolved = resolveClassNames(fullContent, allClassNames)
                if (resolved.isNotEmpty()) {
                    val line = source.lines.indexOfFirst { loadClassVariablePattern.containsMatchIn(it) }
                    for (className in resolved) {
                        if (!parser.isClassKept(className, keepRules)) {
                            issues.add(createIssue(source, line + 1, className, "(dynamically constructed) "))
                        }
                    }
                }
            }
        }

        return issues
    }

    private fun resolveClassNames(content: String, allClassNames: Set<String>): Set<String> {
        val resolved = mutableSetOf<String>()
        val packageStrings = packageStringPattern.findAll(content).map { it.groupValues[1] }.toList()
        val appendedClasses = classAppendPattern.findAll(content).map { it.groupValues[1] }.toList()

        for (pkg in packageStrings) {
            for (cls in appendedClasses) {
                val fullName = "$pkg.$cls"
                if (fullName in allClassNames) resolved.add(fullName)
            }
        }
        return resolved
    }

    private fun inferFullClassName(source: SourceFile): String? {
        val pkg = source.lines.firstOrNull { it.trimStart().startsWith("package ") }
            ?.trim()?.removePrefix("package ")?.removeSuffix(";")?.trim() ?: return null
        val classPattern = Regex("""(?:class|object|data\s+class|enum\s+class)\s+([A-Z][a-zA-Z0-9]+)""")
        val match = source.lines.firstNotNullOfOrNull { classPattern.find(it) } ?: return null
        return "$pkg.${match.groupValues[1]}"
    }

    private fun createIssue(
        source: SourceFile,
        lineNumber: Int,
        className: String,
        prefix: String = ""
    ): ProguardIssue = ProguardIssue(
        type = issueType,
        severity = Severity.ERROR,
        filePath = source.file.path,
        lineNumber = lineNumber,
        message = "ClassLoader.loadClass() ${prefix}references \"$className\" - class is not protected by keep rules. " +
            "R8 will rename or remove this class, causing ClassNotFoundException at runtime. " +
            "This pattern is equivalent to Class.forName() but bypasses Class.forName detectors.",
        suggestion = "Add to proguard-rules.pro: -keep class $className { *; }",
        className = className
    )
}
