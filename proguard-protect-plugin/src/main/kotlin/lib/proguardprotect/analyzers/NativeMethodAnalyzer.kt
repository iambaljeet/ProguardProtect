package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects native (JNI) method declarations whose class will be renamed by R8.
 *
 * JNI function signatures encode the Java class name:
 *   Java_com_example_MyClass_myNativeMethod(JNIEnv*, jobject, ...)
 * When R8 renames the class, JNI lookup fails → UnsatisfiedLinkError.
 *
 * References:
 * - https://stackoverflow.com/questions/19004114/jni-proguard-obfuscation
 * - https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html
 */
class NativeMethodAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.NATIVE_METHOD_RENAMED

    // Kotlin external fun declaration
    private val externalFunPattern = Regex("""^\s*(?:private\s+|protected\s+|internal\s+|public\s+)?external\s+fun\s+""")
    // Java native method declaration
    private val javaNativePattern = Regex("""^\s*(?:private\s+|protected\s+|public\s+)?(?:static\s+)?native\s+\w""")

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        for (source in sourceFiles) {
            var hasNativeMethods = false
            var firstNativeLine = 1

            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                if (externalFunPattern.containsMatchIn(line) || javaNativePattern.containsMatchIn(line)) {
                    if (!hasNativeMethods) {
                        hasNativeMethods = true
                        firstNativeLine = index + 1
                    }
                }
            }

            if (!hasNativeMethods) continue

            val fqn = "${source.packageName}.${source.file.nameWithoutExtension}"
            if (!parser.isClassKept(fqn, keepRules)) {
                issues.add(ProguardIssue(
                    type = IssueType.NATIVE_METHOD_RENAMED,
                    severity = Severity.WARNING,
                    filePath = source.file.path,
                    lineNumber = firstNativeLine,
                    message = "Class '$fqn' declares native/external JNI methods. " +
                        "R8 will rename this class → native library's JNI signatures will not match → UnsatisfiedLinkError at runtime.",
                    suggestion = "-keepclasseswithmembernames class $fqn { native <methods>; }",
                    className = fqn,
                    memberName = "native methods"
                ))
            }
        }
        return issues
    }
}
