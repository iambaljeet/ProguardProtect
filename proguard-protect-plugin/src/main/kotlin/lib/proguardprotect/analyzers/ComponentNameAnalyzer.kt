package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects hardcoded class names used in [android.content.ComponentName],
 * [android.content.Intent.setClassName], or similar Android navigation APIs.
 *
 * R8 renames Activity/Service/BroadcastReceiver classes that lack keep rules.
 * When Android's ActivityManager tries to resolve a component by its original
 * class name (stored as a string), it fails with ActivityNotFoundException.
 *
 * Common sources of this issue:
 * - Deep-link routing with hardcoded FQCN strings
 * - Plugin architectures with dynamically loaded Activity classes
 * - Remote config driven navigation maps
 * - Inter-process communication with explicit class-name Intents
 *
 * References:
 * - https://stackoverflow.com/questions/36829329/proguard-activitynotfoundexception
 * - https://issuetracker.google.com/issues/173432204
 * - https://stackoverflow.com/questions/22680117/proguard-and-using-class-names-as-strings
 */
class ComponentNameAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.COMPONENT_CLASS_NOT_FOUND

    /**
     * Matches ComponentName(pkg, "fully.qualified.ClassName") — literal string class name.
     * Captures the class name string (group 1).
     */
    private val componentNamePattern = Regex(
        """ComponentName\s*\([^,]+,\s*["']([a-zA-Z][a-zA-Z0-9_.]+(?:Activity|Service|Receiver|Provider)[a-zA-Z0-9_]*)["']"""
    )

    /**
     * Matches intent.setClassName(pkg, "fully.qualified.ClassName").
     */
    private val setClassNamePattern = Regex(
        """setClassName\s*\([^,]+,\s*["']([a-zA-Z][a-zA-Z0-9_.]+(?:Activity|Service|Receiver|Provider)[a-zA-Z0-9_]*)["']"""
    )

    /**
     * Matches Intent(context, ClassName::class.java) — NOT a risk (direct ref), but a string
     * literal fallback: Intent("fully.qualified.ClassName") style intents.
     */
    private val intentClassStringPattern = Regex(
        """ComponentName\s*\([^)]*["']([a-zA-Z][a-zA-Z0-9_.]{5,})["'][^)]*\)"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()
        val seen = mutableSetOf<String>()

        for (source in sourceFiles) {
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                // Try ComponentName pattern first
                val compMatch = componentNamePattern.find(line) ?: setClassNamePattern.find(line)
                if (compMatch != null) {
                    val className = compMatch.groupValues[1]
                    val key = "$className:${source.file.path}"
                    if (key in seen) return@forEachIndexed
                    seen.add(key)

                    if (!parser.isClassKept(className, keepRules)) {
                        issues.add(
                            ProguardIssue(
                                type = IssueType.COMPONENT_CLASS_NOT_FOUND,
                                severity = Severity.WARNING,
                                filePath = source.file.path,
                                lineNumber = index + 1,
                                message = "Android component '$className' is referenced by string in ComponentName/setClassName. " +
                                    "R8 will rename it without proper keep rules → ActivityNotFoundException at runtime. " +
                                    "The Android framework resolves components by their original FQCN.",
                                suggestion = "-keep class $className { *; }",
                                className = className,
                                memberName = null
                            )
                        )
                    }
                }
            }
        }
        return issues
    }
}
