package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects classes implementing [java.io.Serializable] that are vulnerable to
 * R8 obfuscation breaking cross-build deserialization.
 *
 * **The Problem**: Java serialization stores the fully-qualified class name (FQCN)
 * AND an implicit `serialVersionUID` (computed from the class structure) in the
 * serialized byte stream. R8 affects this in two critical ways:
 *
 * 1. **Class Rename**: R8 renames `app.myapp.UserSession` → `a.b`. Any serialized
 *    data written by an older build (or a differently-obfuscated build) references
 *    the original class name. At deserialization time, `ObjectInputStream` cannot
 *    find the class → [ClassNotFoundException].
 *
 * 2. **Implicit UID Change**: Without an explicit `serialVersionUID`, Java computes
 *    a hash from the class name, its fields, and their types. When R8 renames fields
 *    (e.g., `authToken` → `a`), the computed hash changes → [java.io.InvalidClassException]:
 *    "local class incompatible: stream classdesc serialVersionUID = X, local = Y"
 *
 * **When it happens in practice**:
 * - SharedPreferences or file-based session storage (encoded as base64 serialized object)
 * - SDK clients passing serialized objects between app processes
 * - Remote push notification payloads that include serialized Java objects
 * - Apps that persist serialized model objects across app updates
 *
 * **Important**: In-build round-trips (serialize and deserialize within the SAME build)
 * work fine because both sides use the same obfuscated names. The crash occurs ACROSS
 * builds where the serialized form was produced by a build with different (or no) obfuscation.
 *
 * **References**:
 * - https://stackoverflow.com/questions/285793/what-is-a-serialversionuid-and-why-should-i-use-it
 * - https://issuetracker.google.com/issues/177393019
 * - https://github.com/square/retrofit/issues/3490
 * - https://stackoverflow.com/questions/10378855/java-io-invalidclassexception-local-class-incompatible
 *
 * **Fix**:
 * - Add `companion object { private const val serialVersionUID = 1L }` to the class
 * - AND add a keep rule: `-keep class com.myapp.UserSession { *; }`
 */
class SerializableAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.SERIALIZABLE_CLASS_RENAMED

    /**
     * Detects data/regular class declarations that implement Serializable.
     * Matches: `class X : Serializable`, `data class X(...) : Serializable`,
     *          `class X(...) : SomeBase(), Serializable`
     */
    private val serializableClassPattern = Regex(
        """(?:data\s+)?class\s+(\w+)\s*(?:\([^)]*\))?\s*[:{][^{]*\bSerializable\b"""
    )

    /** Matches the closing `)` of a multi-line constructor followed by `: Serializable`. */
    private val multiLineSerializablePattern = Regex(
        """^\s*\)\s*:\s*[^{]*\bSerializable\b"""
    )

    /**
     * Detects explicit `serialVersionUID` declaration — classes WITH this are safe
     * (the UID won't change even if R8 renames fields, as long as the class is kept).
     */
    private val serialVersionUidPattern = Regex(
        """serialVersionUID\s*=\s*[0-9-]+L?"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        for (source in sourceFiles) {
            // Pre-scan: does this file have an explicit serialVersionUID?
            val hasExplicitUid = source.lines.any { serialVersionUidPattern.containsMatchIn(it) }

            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                // Try single-line match first: class Foo(...) : Serializable
                val classMatch = serializableClassPattern.find(line)
                if (classMatch != null) {
                    reportSerializable(classMatch.groupValues[1], source, index, hasExplicitUid, keepRules, parser, issues)
                    return@forEachIndexed
                }

                // Handle multi-line data class declarations where ): Serializable is on its own line
                // e.g.:  data class Foo(       <- line N
                //            val x: Int        <- line N+1
                //        ) : Serializable      <- line N+2 (current line)
                if (multiLineSerializablePattern.containsMatchIn(line)) {
                    // Trace back to find the class name (look for 'class X(' within last 20 lines)
                    val classNamePattern = Regex("""(?:data\s+)?class\s+(\w+)\s*\(""")
                    val lookbackStart = maxOf(0, index - 20)
                    for (prevIdx in index - 1 downTo lookbackStart) {
                        val prevMatch = classNamePattern.find(source.lines[prevIdx])
                        if (prevMatch != null) {
                            reportSerializable(prevMatch.groupValues[1], source, prevIdx, hasExplicitUid, keepRules, parser, issues)
                            break
                        }
                    }
                }
            }
        }
        return issues
    }

    private fun reportSerializable(
        className: String,
        source: SourceFile,
        lineIndex: Int,
        hasExplicitUid: Boolean,
        keepRules: List<KeepRule>,
        parser: ProguardRulesParser,
        issues: MutableList<ProguardIssue>
    ) {
        val fqcn = if (source.packageName.isNotEmpty()) "${source.packageName}.$className" else className
        val isKept = parser.isClassKept(fqcn, keepRules)
        if (isKept && hasExplicitUid) return // Fully protected

        val problems = buildList {
            if (!isKept) add("no ProGuard keep rule (class will be renamed)")
            if (!hasExplicitUid) add("no explicit serialVersionUID (implicit UID will change when class structure changes)")
        }

        issues.add(
            ProguardIssue(
                type = IssueType.SERIALIZABLE_CLASS_RENAMED,
                severity = Severity.WARNING,
                filePath = source.file.path,
                lineNumber = lineIndex + 1,
                message = "Class '$className' implements Serializable with: ${problems.joinToString(", ")}. " +
                    "R8 renames the class and its fields → cross-build deserialization fails with " +
                    "ClassNotFoundException or InvalidClassException.",
                suggestion = buildString {
                    if (!isKept) append("-keep class $fqcn { *; }  ")
                    if (!hasExplicitUid) append("Add: companion object { private const val serialVersionUID = 1L }")
                },
                className = fqcn,
                memberName = null
            )
        )
    }
}
