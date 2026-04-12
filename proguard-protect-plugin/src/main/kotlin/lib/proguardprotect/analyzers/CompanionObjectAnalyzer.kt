package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.SourceFile

/**
 * Detects Kotlin companion object access patterns that are vulnerable to R8 renaming.
 *
 * Kotlin companion objects compile to a nested class `EnclosingClass$Companion` in bytecode.
 * R8 can rename this nested class, breaking dynamic reflection that uses the original name.
 *
 * Patterns detected:
 * 1. `Class.forName(...)` with a string containing `$Companion` (dynamic loading)
 * 2. `companionObjectInstance` Kotlin reflection property access
 * 3. String construction that builds `ClassName$Companion` from parts
 * 4. `getDeclaredField("Companion")` on a class (accessing companion instance field)
 */
class CompanionObjectAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.COMPANION_OBJECT_STRIPPED

    // Matches: Class.forName("pkg.ClassName$Companion") — direct literal
    private val directCompanionForNamePattern = Regex(
        """Class\.forName\s*\(\s*"([^"]+\${'$'}Companion[^"]*)"\s*\)"""
    )

    // Matches: companionObjectInstance or companionObject property access
    private val companionObjectInstancePattern = Regex(
        """(\w+)::class\.companionObject(?:Instance)?"""
    )

    // Matches the $Companion suffix string (escaped or literal) in source code
    private val companionSuffixPattern = Regex(
        """["\\]\${'$'}Companion"""
    )

    // Matches: getDeclaredField("Companion") — accessing the companion instance field
    private val getCompanionFieldPattern = Regex(
        """getDeclaredField\s*\(\s*"Companion"\s*\)"""
    )

    // Package string pattern for resolving companion class context
    private val packageStringPattern = Regex(
        """"(app\.[a-z][a-z0-9.]+)""""
    )

    // Class name after package string pattern
    private val classNameAfterPackagePattern = Regex(
        """val\s+\w+\s*=\s*"([A-Z][a-zA-Z0-9]+)""""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()

        for (source in sourceFiles) {
            val fullContent = source.lines.joinToString("\n")

            // Check if file uses companion suffix string (dynamic companion class construction)
            val hasCompanionSuffix = companionSuffixPattern.containsMatchIn(fullContent)
            val hasGetCompanionField = getCompanionFieldPattern.containsMatchIn(fullContent)

            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                // Pattern 1: Direct Class.forName with $Companion literal
                directCompanionForNamePattern.findAll(line).forEach { match ->
                    val companionClassName = match.groupValues[1]
                    val baseClass = companionClassName.substringBefore("\$Companion")
                    issues.add(createIssue(source, index + 1, "$baseClass\$Companion"))
                }

                // Pattern 2: companionObjectInstance / companionObject reflection
                companionObjectInstancePattern.findAll(line).forEach { match ->
                    val className = match.groupValues[1]
                    val resolved = source.resolveClassName(className) ?: "${source.packageName}.$className"
                    if (resolved.startsWith("app.")) {
                        issues.add(createIssue(source, index + 1, "$resolved\$Companion"))
                    }
                }
            }

            // Pattern 3: Dynamic companion class construction via string parts
            // e.g., val companionSuffix = "$Companion"; val companionClassName = "$pkg.$className$companionSuffix"
            if (hasCompanionSuffix || hasGetCompanionField) {
                val companionLine = source.lines.indexOfFirst {
                    companionSuffixPattern.containsMatchIn(it) || getCompanionFieldPattern.containsMatchIn(it)
                }
                if (companionLine >= 0) {
                    // Try to find the base class name from package + class construction in context.
                    // Build a set of all known FQNs from source files to validate candidates —
                    // this prevents false positives from TAG/log constants like val TAG = "ClassName".
                    val allSourceFqns = sourceFiles.flatMapTo(mutableSetOf()) { src ->
                        val pkg = src.packageName
                        val classPattern = Regex("""(?:class|object|data\s+class|enum\s+class|interface)\s+([A-Z][a-zA-Z0-9]+)""")
                        src.lines.mapNotNull { line -> classPattern.find(line)?.groupValues?.get(1)?.let { "$pkg.$it" } }
                    }

                    val packages = packageStringPattern.findAll(fullContent).map { it.groupValues[1] }.toList()
                    val classNames = classNameAfterPackagePattern.findAll(fullContent).map { it.groupValues[1] }.toList()

                    if (packages.isNotEmpty() && classNames.isNotEmpty()) {
                        for (pkg in packages) {
                            for (cls in classNames) {
                                val baseClass = "$pkg.$cls"
                                // Only create issue if the class actually exists in the project source.
                                // This filters out TAG constants (e.g., val TAG = "EnclosingClassName")
                                // where the string value happens to look like a class name.
                                if (baseClass in allSourceFqns) {
                                    val companionClass = "$baseClass\$Companion"
                                    issues.add(createIssue(source, companionLine + 1, companionClass))
                                }
                            }
                        }
                    } else if (hasGetCompanionField) {
                        // Can't resolve exact class, report generic companion access
                        val forNameLine = source.lines.indexOfFirst {
                            Regex("""Class\.forName\s*\(""").containsMatchIn(it)
                        }
                        if (forNameLine >= 0) {
                            // Already covered by reflection class analyzer — skip
                        }
                    }
                }
            }
        }

        return issues
    }

    private fun createIssue(source: SourceFile, lineNumber: Int, companionClassName: String): ProguardIssue {
        val enclosingClass = companionClassName.substringBefore("\$Companion")
        return ProguardIssue(
            type = issueType,
            severity = Severity.ERROR,
            filePath = source.file.path,
            lineNumber = lineNumber,
            message = "Kotlin companion object '$companionClassName' accessed via reflection. " +
                "R8 renames companion class from original name → ClassNotFoundException at runtime.",
            suggestion = "Add to proguard-rules.pro:\n" +
                "-keep class $companionClassName { *; }\n" +
                "-keep class $enclosingClass { *; }",
            className = companionClassName
        )
    }
}
