package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects custom View classes that will cause InflateException when renamed by R8.
 *
 * Android's LayoutInflater instantiates custom views from XML by class name:
 *   Class.forName(className).getConstructor(Context, AttributeSet).newInstance(...)
 * When R8 renames the class, LayoutInflater can't find it → InflateException.
 *
 * Only flags classes with AttributeSet constructors (XML-inflatable).
 *
 * References:
 * - https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html
 * - https://krossovochkin.com/posts/2021_01_18_debugging_proguard_configuration_issues/
 */
class CustomViewAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.CUSTOM_VIEW_STRIPPED

    // Match class declarations that inherit from View subclasses (on a non-comment line)
    private val viewInheritPattern = Regex(
        """(?:class|object)\s+\w+[^{]*:\s*[^{(\n]*?(?:android\.view\.|android\.widget\.|androidx\.[a-z.]*\.)?""" +
        """(View|ViewGroup|FrameLayout|LinearLayout|RelativeLayout|ConstraintLayout|RecyclerView|TextView|ImageView|EditText|Button|CheckBox|CardView)\b"""
    )
    // Must have an AttributeSet constructor to be XML-inflatable
    private val attributeSetPattern = Regex("""AttributeSet""")

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        for (source in sourceFiles) {
            // Only check non-comment, non-string lines for patterns
            val codeLines = source.lines.filter { line ->
                val trimmed = line.trim()
                !trimmed.startsWith("//") && !trimmed.startsWith("*") && !trimmed.startsWith("/*")
            }
            val codeContent = codeLines.joinToString("\n")

            if (!viewInheritPattern.containsMatchIn(codeContent)) continue
            // Only XML-inflatable views (those with AttributeSet in their constructor signature)
            // Check in code lines only to avoid matching AttributeSet in comments
            if (codeLines.none { attributeSetPattern.containsMatchIn(it) && !it.trim().startsWith("//") }) continue

            var classLine = 1
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("//") && !trimmed.startsWith("*") && viewInheritPattern.containsMatchIn(line)) {
                    classLine = index + 1
                    return@forEachIndexed
                }
            }

            val fqn = "${source.packageName}.${source.file.nameWithoutExtension}"
            if (!parser.isClassKept(fqn, keepRules)) {
                issues.add(ProguardIssue(
                    type = IssueType.CUSTOM_VIEW_STRIPPED,
                    severity = Severity.WARNING,
                    filePath = source.file.path,
                    lineNumber = classLine,
                    message = "Custom View class '$fqn' extends an Android View class and has XML-inflatable constructors. " +
                        "R8 will rename this class → LayoutInflater fails → InflateException: ClassNotFoundException.",
                    suggestion = "-keep class $fqn { public <init>(android.content.Context, android.util.AttributeSet); }",
                    className = fqn
                ))
            }
        }
        return issues
    }
}
