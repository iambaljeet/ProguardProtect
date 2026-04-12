package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects classes that rely on the JVM `Signature` bytecode attribute for generic
 * type introspection at runtime, without a corresponding `-keepattributes Signature` rule.
 *
 * R8 strips the `Signature` attribute by default (unless explicitly kept). Code that does:
 * ```kotlin
 * val superclass = javaClass.genericSuperclass as ParameterizedType
 * val typeArg = superclass.actualTypeArguments[0] as Class<T>
 * ```
 * will throw `ClassCastException: Class cannot be cast to ParameterizedType` because
 * `genericSuperclass` returns a raw `Class` without the Signature attribute.
 *
 * Common victims: Gson TypeAdapters, Retrofit callbacks, custom generic base classes.
 *
 * References:
 * - https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#gson-and-generic-type
 * - https://issuetracker.google.com/issues/112386012
 * - https://stackoverflow.com/questions/6034233/class-cast-exception-on-getting-generic-type-information
 * - https://medium.com/@ferrosward/r8-is-stripping-type-generic-signature-from-my-classes-3c3e7ed29bb2
 */
class GenericSignatureAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.GENERIC_SIGNATURE_STRIPPED

    /** Matches `class Foo : SomeGenericBase<T>()` — concrete class extending a generic. */
    private val genericSubclassPattern = Regex(
        """^\s*(?:internal\s+|private\s+|public\s+|open\s+|abstract\s+)*class\s+(\w+)\s*(?:<[^>]+>)?\s*:\s*\w+\s*<"""
    )

    /** Matches `.genericSuperclass` or `.genericInterfaces` usage in code. */
    private val genericSuperclassPattern = Regex(
        """\.(genericSuperclass|genericInterfaces)\b"""
    )

    /** Matches `as ParameterizedType` cast. */
    private val parameterizedTypeCastPattern = Regex(
        """\bas\s+ParameterizedType\b|\(ParameterizedType\)"""
    )

    /** Matches `.actualTypeArguments` access. */
    private val actualTypeArgsPattern = Regex(
        """\.actualTypeArguments\b"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        // Find files that use genericSuperclass/genericInterfaces + ParameterizedType/actualTypeArguments
        for (source in sourceFiles) {
            val fullContent = source.lines.joinToString("\n")
            val hasGenericSuperclassUsage = genericSuperclassPattern.containsMatchIn(fullContent) ||
                parameterizedTypeCastPattern.containsMatchIn(fullContent) ||
                actualTypeArgsPattern.containsMatchIn(fullContent)

            if (!hasGenericSuperclassUsage) continue

            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                val classMatch = genericSubclassPattern.find(line) ?: return@forEachIndexed
                val simpleName = classMatch.groupValues[1]
                val fqn = "${source.packageName}.$simpleName"

                // Always report — keepattributes Signature is a global directive that the
                // ProguardRulesParser doesn't parse. Confirmation is done in PostBuildAnalysisTask
                // by checking the raw rule files for '-keepattributes ... Signature'.
                issues.add(
                        ProguardIssue(
                            type = IssueType.GENERIC_SIGNATURE_STRIPPED,
                            severity = Severity.WARNING,
                            filePath = source.file.path,
                            lineNumber = index + 1,
                            message = "Class '$fqn' extends a generic type and uses genericSuperclass/ParameterizedType " +
                                "for runtime type introspection. R8 strips the Signature bytecode attribute without " +
                                "'-keepattributes Signature' → ClassCastException: Class cannot be cast to ParameterizedType.",
                            suggestion = "-keepattributes Signature, InnerClasses, EnclosingMethod",
                            className = fqn,
                            memberName = "Signature"
                        )
                    )
            }
        }
        return issues
    }
}
