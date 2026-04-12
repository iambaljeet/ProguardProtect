package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects data classes used with Gson serialization/deserialization
 * without @SerializedName annotations or ProGuard keep rules.
 * Without protection, R8 renames fields → JSON keys don't match → fields are null at runtime.
 */
/**
 * Detects Gson serialization/deserialization of classes without `@SerializedName` annotations.
 *
 * When R8 renames fields, Gson's default field-name-based JSON mapping breaks silently —
 * fields receive null/default values instead of their expected data, causing data corruption
 * or NullPointerException at runtime.
 */
class SerializationAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.SERIALIZATION_FIELD_RENAME

    // Matches: Gson().fromJson(json, SomeClass::class.java) or gson.fromJson(json, pkg.SomeClass::class.java)
    private val gsonFromJsonPattern = Regex(
        """(?:gson|Gson\(\))\.fromJson\s*\([^,]+,\s*([\w.]+)::class\.java\s*\)"""
    )

    // Matches: Gson().toJson(obj)  - indicates Gson is used
    private val gsonToJsonPattern = Regex(
        """(?:gson|Gson\(\))\.toJson\s*\("""
    )

    // Matches generic Gson type token: object : TypeToken<List<SomeClass>>
    private val typeTokenPattern = Regex(
        """TypeToken<[^>]*?(\w+)[>>\s]"""
    )

    // Detects @SerializedName annotation (only on actual code lines, not comments)
    private val serializedNamePattern = Regex(
        """^\s*@SerializedName"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        // First pass: find all classes used with Gson
        val gsonModelClasses = mutableSetOf<Pair<String, String>>() // (className, filePath:line)

        for (source in sourceFiles) {
            source.lines.forEachIndexed { index, line ->
                // Skip comment lines — prevents matching TypeToken references in KDoc/JavaDoc
                // (e.g., a model file documenting "TypeToken<List<ProductCatalog>>" in its comments)
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                gsonFromJsonPattern.findAll(line).forEach { match ->
                    val classRef = match.groupValues[1]
                    val fullName = if (classRef.contains(".")) classRef
                        else source.resolveClassName(classRef) ?: "${source.packageName}.$classRef"
                    gsonModelClasses.add(fullName to "${source.file.path}:${index + 1}")
                }

                typeTokenPattern.findAll(line).forEach { match ->
                    val simpleName = match.groupValues[1]
                    // Prefer FQN already present in the same line (e.g., TypeToken<List<app.pkg.Cls>>)
                    // before falling back to simple-name resolution, which may resolve to the wrong package.
                    val fqnInLine = Regex("""((?:app|com|org|net)\.[a-z][a-z0-9.]+\.[A-Z][a-zA-Z0-9]*)""")
                        .find(line)?.groupValues?.get(1)
                    val fullName = when {
                        fqnInLine != null && !fqnInLine.startsWith("com.google") && !fqnInLine.startsWith("com.android") ->
                            fqnInLine
                        simpleName.isNotEmpty() && simpleName[0].isUpperCase() ->
                            source.resolveClassName(simpleName) ?: "${source.packageName}.$simpleName"
                        else -> return@forEach
                    }
                    gsonModelClasses.add(fullName to "${source.file.path}:${index + 1}")
                }
            }
        }

        // Second pass: check if those model classes have @SerializedName or keep rules
        for ((className, location) in gsonModelClasses) {
            val simpleName = className.substringAfterLast(".")
            val modelSource = sourceFiles.find { source ->
                source.file.name == "$simpleName.kt" || source.file.name == "$simpleName.java"
            }

            val hasSerializedName = modelSource?.lines?.any { line ->
                val trimmed = line.trim()
                !trimmed.startsWith("//") && !trimmed.startsWith("*") && !trimmed.startsWith("/*") &&
                    serializedNamePattern.containsMatchIn(line)
            } ?: false

            val hasKeepRule = parser.isFieldKept(className, keepRules)

            if (!hasSerializedName && !hasKeepRule) {
                val (filePath, lineNum) = location.split(":")
                issues.add(
                    ProguardIssue(
                        type = issueType,
                        severity = Severity.ERROR,
                        filePath = filePath,
                        lineNumber = lineNum.toInt(),
                        message = "Class $className is used with Gson serialization but has no @SerializedName annotations " +
                            "and no ProGuard keep rules. R8 will rename fields → JSON deserialization will fail at runtime.",
                        suggestion = "Add to proguard-rules.pro: -keep class $className { *; } " +
                            "OR add @SerializedName(\"fieldName\") to each field.",
                        className = className
                    )
                )
            }
        }

        return issues
    }
}
