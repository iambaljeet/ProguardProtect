package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects WebView JavascriptInterface methods that will be renamed by R8.
 *
 * Android WebView bridges work by matching method names at runtime:
 * JavaScript calls `window.bridge.methodName()` → Android finds the method by its
 * exact Java name. When R8 renames @JavascriptInterface methods, the bridge breaks.
 *
 * References:
 * - https://codingtechroom.com/question/how-to-resolve-javascript-interface-issues-with-android-proguard
 * - https://stackoverflow.com/questions/17629507/how-to-configure-proguard-for-javascript-interface
 */
class JavascriptInterfaceAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.JAVASCRIPT_INTERFACE_STRIPPED

    // Match @JavascriptInterface as a standalone annotation (not inside strings/comments)
    private val jsiAnnotationPattern = Regex("""^\s*@JavascriptInterface\s*$""")
    private val addJsiPattern = Regex("""addJavascriptInterface\s*\(""")

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        // Check if addJavascriptInterface is used anywhere in the project
        val hasAddJsiUsage = sourceFiles.any { s ->
            s.lines.any { addJsiPattern.containsMatchIn(it) }
        }

        for (source in sourceFiles) {
            var hasJsi = false
            var firstJsiLine = 1
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                if (jsiAnnotationPattern.containsMatchIn(line)) {
                    if (!hasJsi) {
                        hasJsi = true
                        firstJsiLine = index + 1
                    }
                }
            }

            if (!hasJsi) continue

            val fqn = "${source.packageName}.${source.file.nameWithoutExtension}"
            if (!parser.isClassKept(fqn, keepRules)) {
                issues.add(ProguardIssue(
                    type = IssueType.JAVASCRIPT_INTERFACE_STRIPPED,
                    severity = Severity.WARNING,
                    filePath = source.file.path,
                    lineNumber = firstJsiLine,
                    message = "Class '$fqn' has @JavascriptInterface methods used as WebView bridge" +
                        (if (hasAddJsiUsage) " via addJavascriptInterface()" else "") +
                        ". R8 will rename these methods → JavaScript calls fail at runtime.",
                    suggestion = "-keepclassmembers class $fqn { @android.webkit.JavascriptInterface <methods>; }",
                    className = fqn
                ))
            }
        }
        return issues
    }
}
