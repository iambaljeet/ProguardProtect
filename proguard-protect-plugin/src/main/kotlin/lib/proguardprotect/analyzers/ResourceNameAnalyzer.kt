package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.SourceFile

/**
 * Detects resources accessed only via [android.content.Context.getResources] +
 * [android.content.res.Resources.getIdentifier] string lookup that may be removed
 * by aapt2 when `shrinkResources = true` with strict mode is enabled.
 *
 * In safe mode (default), aapt2 keeps resources referenced by literal strings in
 * `getIdentifier()`. In strict mode (`tools:shrinkMode="strict"` in keep.xml),
 * ONLY resources with a direct R.type.name reference OR explicit `tools:keep` are kept.
 *
 * This analyzer finds `getIdentifier("resourceName", ...)` calls with string literals
 * and flags them as potential resource shrinking victims.
 *
 * References:
 * - https://developer.android.com/build/shrink-code#keep-resources
 * - https://stackoverflow.com/questions/38069175/getidentifier-returns-0-after-minification
 */
class ResourceNameAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.RESOURCE_SHRUNK_BY_NAME

    /**
     * Matches getIdentifier("literalName", "type", ...) — resource referenced only by string.
     * Captures the resource name (group 1) and resource type (group 2).
     */
    private val getIdentifierPattern = Regex(
        """getIdentifier\s*\(\s*["']([a-zA-Z0-9_]+)["']\s*,\s*["']([a-zA-Z0-9_]+)["']"""
    )

    /**
     * Detects resource name concatenation/construction that aapt2 can't statically trace.
     * e.g. `getIdentifier(buildString { ... }, "drawable", pkg)`
     */
    private val dynamicGetIdentifierPattern = Regex(
        """getIdentifier\s*\(\s*(?!["'])"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()

        for (source in sourceFiles) {
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                // Check for getIdentifier with literal string name
                val match = getIdentifierPattern.find(line)
                if (match != null) {
                    val resourceName = match.groupValues[1]
                    val resourceType = match.groupValues[2]
                    issues.add(
                        ProguardIssue(
                            type = IssueType.RESOURCE_SHRUNK_BY_NAME,
                            severity = Severity.WARNING,
                            filePath = source.file.path,
                            lineNumber = index + 1,
                            message = "Resource '$resourceName' (type: $resourceType) is accessed via " +
                                "getIdentifier() string lookup. With shrinkResources + strict mode, aapt2 removes " +
                                "resources not directly referenced via R.type.name → Resources\$NotFoundException at runtime.",
                            suggestion = "Add <item name=\"$resourceName\" type=\"$resourceType\" " +
                                "tools:keep=\"@$resourceType/$resourceName\"/> in res/raw/keep.xml, " +
                                "OR add a direct R.$resourceType.$resourceName reference somewhere in code.",
                            className = source.packageName,
                            memberName = "$resourceType/$resourceName"
                        )
                    )
                    return@forEachIndexed
                }

                // Check for dynamic (non-literal) getIdentifier — higher risk
                if (dynamicGetIdentifierPattern.containsMatchIn(line)) {
                    issues.add(
                        ProguardIssue(
                            type = IssueType.RESOURCE_SHRUNK_BY_NAME,
                            severity = Severity.WARNING,
                            filePath = source.file.path,
                            lineNumber = index + 1,
                            message = "getIdentifier() is called with a dynamically constructed name. " +
                                "aapt2 CANNOT statically determine which resources to keep → any resource " +
                                "accessed this way may be removed by shrinkResources, causing Resources\$NotFoundException.",
                            suggestion = "Use direct R.type.name references where possible, or add explicit " +
                                "tools:keep entries in res/raw/keep.xml for all dynamically accessed resources.",
                            className = source.packageName,
                            memberName = "dynamic_getIdentifier"
                        )
                    )
                }
            }
        }
        return issues
    }
}
