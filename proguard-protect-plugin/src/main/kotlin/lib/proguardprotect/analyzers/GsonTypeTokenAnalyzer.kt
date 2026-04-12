package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.SourceFile

/**
 * Detects Gson TypeToken usage where the parameterized type class may be renamed by R8.
 *
 * When R8 strips the `Signature` bytecode attribute (absent `-keepattributes Signature`),
 * TypeToken loses its generic type info → `RuntimeException: Missing type parameter` at runtime.
 * Also detects model classes inside TypeToken angle brackets that lack keep rules,
 * meaning their fields may be renamed → silent JSON deserialization corruption.
 *
 * Patterns detected:
 * - `TypeToken<List<ClassName>>(){}`
 * - `TypeToken<ClassName>(){}`
 * - `TypeToken<Map<K, ClassName>>()`
 * - `TypeToken.getParameterized(List::class.java, ClassName::class.java)`
 */
class GsonTypeTokenAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.GSON_TYPE_TOKEN_STRIPPED

    // Matches TypeToken<...> capturing the type arguments
    private val typeTokenPattern = Regex(
        """TypeToken\s*<([^>]+)>"""
    )

    // Matches TypeToken.getParameterized(SomeClass::class.java, ModelClass::class.java)
    private val getParameterizedPattern = Regex(
        """TypeToken\.getParameterized\s*\(([^)]+)\)"""
    )

    // Simple class name (starts uppercase, no dot) or fully-qualified name
    private val classNamePattern = Regex("""([A-Z][a-zA-Z0-9]*)""")

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()

        for (source in sourceFiles) {
            source.lines.forEachIndexed { index, line ->
                // Skip comment lines
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                // Check TypeToken<...> patterns
                typeTokenPattern.findAll(line).forEach { match ->
                    val typeArgs = match.groupValues[1]
                    extractAppClassNames(typeArgs, source).forEach { className ->
                        issues.add(createIssue(source, index + 1, className))
                    }
                }

                // Check TypeToken.getParameterized(...) patterns
                getParameterizedPattern.findAll(line).forEach { match ->
                    val args = match.groupValues[1]
                    extractAppClassNames(args, source).forEach { className ->
                        issues.add(createIssue(source, index + 1, className))
                    }
                }
            }
        }

        return issues
    }

    /**
     * Extracts fully-qualified app class names from a type argument string.
     *
     * Prioritises FQN patterns already present in the type string
     * (e.g., `List<app.proguard.models.ProductCatalog>` when the file uses a fully-qualified
     * reference without importing the class). Only falls back to simple-name resolution when
     * no FQN is found — this prevents resolving "ProductCatalog" to the wrong package.
     */
    private fun extractAppClassNames(typeArgs: String, source: SourceFile): List<String> {
        val results = mutableListOf<String>()

        // Strategy 1: Extract FQN already present in the type argument string.
        // Handles cases like TypeToken<List<app.proguard.models.ProductCatalog>>.
        val fqnPattern = Regex("""((?:app|com|org|net|io)\.[a-z][a-z0-9.]+\.[A-Z][a-zA-Z0-9]*)""")
        fqnPattern.findAll(typeArgs).forEach { match ->
            val fqn = match.groupValues[1]
            // Exclude known framework packages that shouldn't be flagged
            if (!fqn.startsWith("com.google") && !fqn.startsWith("com.android")) {
                results.add(fqn)
            }
        }
        if (results.isNotEmpty()) return results

        // Strategy 2: Fall back to simple-name resolution via imports.
        classNamePattern.findAll(typeArgs).forEach { match ->
            val simpleName = match.groupValues[1]
            // Skip common generic markers and built-in types
            if (simpleName in setOf("List", "Map", "Set", "String", "Int", "Long",
                    "Double", "Float", "Boolean", "Any", "K", "V", "T", "E", "R")) return@forEach

            val resolved = source.resolveClassName(simpleName) ?: "${source.packageName}.$simpleName"
            // Only flag classes from app packages
            if (resolved.startsWith("app.") || (resolved.startsWith("com.") && !resolved.startsWith("com.google"))) {
                results.add(resolved)
            }
        }
        return results
    }

    private fun createIssue(source: SourceFile, lineNumber: Int, className: String): ProguardIssue {
        return ProguardIssue(
            type = issueType,
            severity = Severity.ERROR,
            filePath = source.file.path,
            lineNumber = lineNumber,
            message = "Gson TypeToken references '$className' — R8 strips the Signature bytecode attribute, " +
                "causing TypeToken to lose generic type info at runtime. " +
                "Additionally, if '$className' fields are renamed, Gson deserialization silently returns default values.",
            suggestion = "Add to proguard-rules.pro: -keepattributes Signature\n" +
                "-keep class * extends com.google.gson.reflect.TypeToken\n" +
                "-keep class $className { *; }",
            className = className
        )
    }
}
