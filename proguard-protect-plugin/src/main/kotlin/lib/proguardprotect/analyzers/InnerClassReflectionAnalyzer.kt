package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.SourceFile

/**
 * Detects [Class.forName] calls that use the `$`-separated Java nested class notation,
 * which breaks when R8 renames the outer class.
 *
 * ## Root Cause
 * Java/Kotlin inner classes are stored in bytecode as `OuterClass$InnerClass`.
 * When code calls `Class.forName("com.example.Outer$Inner")`, R8 renames `Outer` to `a5`,
 * making the nested class `a5$Inner`. The hardcoded string no longer resolves → [ClassNotFoundException].
 *
 * ## Detection Strategy
 * 1. Find `Class.forName("X$Y")` string literals containing a `$` separator
 * 2. Extract the full nested class FQCN (including all nesting levels)
 * 3. Extract the outermost class FQCN (before the first `$`)
 * 4. Report for post-build mapping confirmation
 *
 * ## Post-Build Confirmation (in [lib.proguardprotect.PostBuildAnalysisTask])
 * - Check if the exact nested class FQCN appears in mapping.txt (was renamed)
 * - OR check if the outer class was renamed (which breaks all nested class references)
 * - OR check if the nested class is absent from DEX entirely
 *
 * References:
 * - https://issuetracker.google.com/issues/369813108
 * - https://stackoverflow.com/questions/5816914/proguard-and-inner-classes
 * - https://r8.googlesource.com/r8/+/refs/heads/master/compatibility-faq.md
 */
class InnerClassReflectionAnalyzer : BaseAnalyzer() {

    override val issueType = IssueType.INNER_CLASS_REFLECTION_RENAMED

    /**
     * Matches `Class.forName("pkg.Outer$Inner")` with literal `$` or escaped `\$` in Kotlin.
     * Captures the full nested class FQCN.
     */
    private val classForNameDollarPattern = Regex(
        """Class\.forName\s*\(\s*"([^"]*(?:\$|\\[$])[^"]*)"\s*\)"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()

        for (source in sourceFiles) {
            if (source.file.extension == "xml") continue
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed

                val match = classForNameDollarPattern.find(line) ?: return@forEachIndexed
                val rawFqcn = match.groupValues[1]

                // Normalize: Kotlin `\$` → `$`
                val fqcn = rawFqcn.replace("\\$", "$")
                if (!fqcn.contains("$")) return@forEachIndexed

                // Extract outer class FQCN (before the first `$`)
                val outerFqcn = fqcn.substringBefore("$")
                if (outerFqcn.isBlank()) return@forEachIndexed

                // Extract a readable nested class path for messages (e.g., "TaskDispatcher.TaskResult")
                val displayName = fqcn.replace("$", ".")

                issues.add(ProguardIssue(
                    type = IssueType.INNER_CLASS_REFLECTION_RENAMED,
                    severity = Severity.ERROR,
                    filePath = source.file.path,
                    lineNumber = index + 1,
                    message = "Class.forName(\"$fqcn\") uses nested class notation. " +
                        "R8 will rename outer class '$outerFqcn' → the nested class path " +
                        "'$displayName' no longer exists in the DEX → ClassNotFoundException at runtime.",
                    suggestion = "-keep class ${outerFqcn.trim()} { *; }\n" +
                        "-keep class ${fqcn.trim()} { *; }",
                    className = fqcn
                ))
            }
        }
        return issues
    }
}
