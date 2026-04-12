package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.SourceFile

/**
 * Detects `android:onClick="methodName"` XML layout attributes whose handler methods
 * will be renamed by R8, causing [IllegalStateException] at runtime.
 *
 * ## Root Cause
 * Android resolves `android:onClick` handlers at runtime by reflection:
 * ```java
 * method = context.getClass().getMethod(handlerName, View.class)
 * ```
 * Methods only referenced from XML (never called from Kotlin/Java code) have no direct
 * call sites visible to R8. R8 renames them, and the runtime method lookup fails:
 * ```
 * IllegalStateException: Could not find method handlePaymentSubmit(View)
 *   in a parent or ancestor Context
 * ```
 *
 * ## Detection Strategy
 * 1. Scan layout XML files for `android:onClick="methodName"` attributes
 * 2. Search Kotlin/Java source files for a `fun methodName(` or `void methodName(` method
 * 3. Report the issue with the host class FQN and method name for mapping confirmation
 *
 * ## Post-Build Confirmation (in [lib.proguardprotect.PostBuildAnalysisTask])
 * The method name is checked against mapping.txt. If the host class was renamed, or the
 * method itself was renamed (member entry shows `handlePaymentSubmit -> a`), confirmed.
 *
 * References:
 * - https://stackoverflow.com/questions/20665616/proguard-illegalstateexception-could-not-find-method
 * - https://issuetracker.google.com/issues/37129458
 * - https://developer.android.com/guide/topics/ui/declaring-layout#onClick
 */
class AndroidOnClickAnalyzer : BaseAnalyzer() {

    override val issueType = IssueType.ANDROID_ONCLICK_METHOD_RENAMED

    /** Matches `android:onClick="methodName"` in XML (any whitespace variant). */
    private val onClickXmlPattern = Regex(
        """android:onClick\s*=\s*"(\w+)""""
    )

    /** Matches a Kotlin `fun methodName(` or Java `void methodName(` declaration. */
    private fun handlerMethodPattern(methodName: String) = Regex(
        """(?:fun|void)\s+${Regex.escape(methodName)}\s*\("""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()

        // Step 1: Collect all android:onClick handler names from XML layout files
        val xmlHandlers = mutableMapOf<String, String>() // methodName → xml file path
        for (source in sourceFiles) {
            if (source.file.extension != "xml") continue
            source.lines.forEach { line ->
                val match = onClickXmlPattern.find(line) ?: return@forEach
                val methodName = match.groupValues[1]
                if (methodName.isNotBlank()) {
                    xmlHandlers[methodName] = source.file.path
                }
            }
        }

        if (xmlHandlers.isEmpty()) return emptyList()

        // Step 2: Search source files for handler method declarations
        val sourceOnlyFiles = sourceFiles.filter { it.file.extension != "xml" }
        for (methodName in xmlHandlers.keys) {
            val xmlFile = xmlHandlers[methodName] ?: continue
            val pattern = handlerMethodPattern(methodName)

            for (source in sourceOnlyFiles) {
                source.lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                    if (!pattern.containsMatchIn(line)) return@forEachIndexed

                    val fqn = "${source.packageName}.${source.file.nameWithoutExtension}"

                    issues.add(ProguardIssue(
                        type = IssueType.ANDROID_ONCLICK_METHOD_RENAMED,
                        severity = Severity.ERROR,
                        filePath = source.file.path,
                        lineNumber = index + 1,
                        message = "Method '$methodName(View)' is referenced from XML layout " +
                            "(android:onClick in ${xmlFile.substringAfterLast("/")}) but has no " +
                            "direct Kotlin call sites. R8 will rename it → " +
                            "IllegalStateException: Could not find method $methodName(View) at runtime.",
                        suggestion = "-keepclassmembers class $fqn {\n" +
                            "    public void $methodName(android.view.View);\n}",
                        className = fqn,
                        memberName = methodName
                    ))
                }
            }
        }
        return issues
    }
}
