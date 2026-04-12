package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.SourceFile

/**
 * Detects resource names embedded in JSON asset files that may be removed by
 * aapt2 strict resource shrinking.
 *
 * Apps with CMS-driven or config-driven UIs often store drawable/string resource names in
 * JSON config files under src/main/assets/. At runtime, code reads the JSON and calls
 * Resources.getIdentifier() to load the resource. However, aapt2's resource shrinker
 * scans ONLY compiled code for resource references -- strings inside JSON asset files
 * are invisible to it. With shrinkResources=true and tools:shrinkMode="strict" in keep.xml,
 * aapt2 removes ALL resources not directly referenced via R.type.name in compiled code,
 * including resources whose names appear only in JSON files.
 *
 * Result: getIdentifier("feature_banner", "drawable", pkg) returns 0 at runtime,
 * causing Resources.NotFoundException when loading the zero resource ID.
 *
 * Real-world examples:
 * - Remote config JSON files (assets/config.json) specifying screen images by name
 * - CMS content JSON bundled in assets that references drawable names for offline use
 * - A/B testing frameworks using asset-based config to specify variant images
 *
 * References:
 * - https://developer.android.com/build/shrink-code#keep-resources
 * - https://issuetracker.google.com/issues/37124708
 * - https://stackoverflow.com/questions/69862538/resources-removed-by-r8-when-loaded-from-json-config
 *
 * Fix: Add tools:keep for resources in res/raw/keep.xml:
 *   tools:keep="@drawable/feature_banner,@drawable/promo_overlay"
 */
class JsonAssetResourceAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.JSON_ASSET_RESOURCE_STRIPPED

    /** Matches assets.open("filename.json") calls. */
    private val assetOpenPattern = Regex(
        """assets\.open\s*\(\s*["']([^"']+\.json)["']"""
    )

    /** Matches getIdentifier() called with a variable (not a literal string). */
    private val dynamicGetIdentifierPattern = Regex(
        """getIdentifier\s*\(\s*(?!["'])"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()

        for (source in sourceFiles) {
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    return@forEachIndexed
                }

                // Detect code that opens a JSON asset file
                val assetMatch = assetOpenPattern.find(line)
                if (assetMatch != null) {
                    val jsonFileName = assetMatch.groupValues[1]
                    issues.add(
                        ProguardIssue(
                            type = IssueType.JSON_ASSET_RESOURCE_STRIPPED,
                            severity = Severity.WARNING,
                            filePath = source.file.path,
                            lineNumber = index + 1,
                            message = "Code opens asset file '$jsonFileName' which may contain resource names. " +
                                "aapt2's resource shrinker cannot see strings inside JSON asset files. " +
                                "With shrinkResources=true and strict mode, resources referenced ONLY via " +
                                "JSON config files will be removed, causing Resources.NotFoundException at runtime.",
                            suggestion = "Scan '$jsonFileName' for resource names and add them to res/raw/keep.xml " +
                                "with tools:keep=\"@drawable/name,@string/name,...\" to prevent aapt2 from removing them.",
                            className = source.packageName,
                            memberName = "json_asset:$jsonFileName"
                        )
                    )
                    return@forEachIndexed
                }

                // Also flag dynamic getIdentifier() calls near JSON/asset/config context
                if (dynamicGetIdentifierPattern.containsMatchIn(line)) {
                    val contextStart = maxOf(0, index - 5)
                    val contextEnd = minOf(source.lines.size, index + 5)
                    val contextLines = source.lines.subList(contextStart, contextEnd).joinToString(" ")
                    if (contextLines.contains("json", ignoreCase = true) ||
                        contextLines.contains("asset", ignoreCase = true) ||
                        contextLines.contains("config", ignoreCase = true)
                    ) {
                        issues.add(
                            ProguardIssue(
                                type = IssueType.JSON_ASSET_RESOURCE_STRIPPED,
                                severity = Severity.WARNING,
                                filePath = source.file.path,
                                lineNumber = index + 1,
                                message = "Dynamic getIdentifier() called near JSON/asset/config context. " +
                                    "Resource names loaded from JSON asset files are invisible to aapt2 " +
                                    "strict shrinking and will be removed, causing Resources.NotFoundException.",
                                suggestion = "Explicitly keep all resources referenced from JSON config files " +
                                    "using tools:keep in res/raw/keep.xml.",
                                className = source.packageName,
                                memberName = "json_dynamic_getIdentifier"
                            )
                        )
                    }
                }
            }
        }

        return issues
    }
}
