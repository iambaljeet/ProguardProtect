package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.SourceFile

/**
 * Detects Parcelable implementations that may be renamed by R8, breaking Bundle/Intent deserialization.
 *
 * Android's Parcel/Bundle mechanism stores the Parcelable class name as a string when serializing.
 * On deserialization (e.g., after cross-process IPC or activity recreation), it calls
 * `Class.forName(storedClassName)` internally. If R8 renamed the class, this lookup fails →
 * `android.os.BadParcelableException` or `ClassNotFoundException`.
 *
 * Patterns detected:
 * - Classes implementing `Parcelable` (`: Parcelable` in Kotlin, `implements Parcelable` in Java)
 * - That are used in bundle/parcel operations: putParcelable, writeParcelable, readParcelable,
 *   getParcelable, putParcelableArray, putParcelableArrayList
 *
 * Only reports Parcelable classes that appear in bundle/parcel serialization calls
 * (not abstract base classes or interfaces themselves).
 */
class ParcelableAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.PARCELABLE_CLASS_RENAMED

    // Matches Kotlin class implementing Parcelable: data class Foo(...) : Parcelable
    private val parcelableKotlinPattern = Regex(
        """^\s*(?:data\s+)?class\s+([A-Z][a-zA-Z0-9]+)[^:]*:\s*[^{]*Parcelable"""
    )

    // Matches Java class implementing Parcelable
    private val parcelableJavaPattern = Regex(
        """class\s+([A-Z][a-zA-Z0-9]+)[^{]*implements[^{]*Parcelable"""
    )

    // Matches parcel/bundle write operations with a class reference
    private val parcelWritePattern = Regex(
        """(?:putParcelable|writeParcelable|putParcelableArray(?:List)?)\s*\("""
    )

    // Matches parcel/bundle read operations
    private val parcelReadPattern = Regex(
        """(?:readParcelable|getParcelable|getParcelableArray(?:List)?)\s*\("""
    )

    // Matches: ShippingAddress(...) — constructor call for known Parcelable classes
    private val constructorCallPattern = Regex(
        """([A-Z][a-zA-Z0-9]+)\s*\("""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()

        // First pass: collect all Parcelable implementation classes.
        // Handles both single-line (`data class Foo : Parcelable`) and multi-line constructors
        // (`data class Foo(\n  val x: T\n) : Parcelable`) by scanning forward from the class
        // declaration to find a `: Parcelable` on a subsequent closing-paren line.
        val parcelableClasses = mutableMapOf<String, Pair<String, Int>>() // simpleName → (FQN, lineNumber)

        // Also matches multi-line constructor closing: `) : Parcelable`
        val parcelableMultilineClosingPattern = Regex("""^\s*\)\s*:\s*[^{]*Parcelable""")

        for (source in sourceFiles) {
            var pendingClassName: String? = null  // class name whose constructor spans multiple lines

            source.lines.forEachIndexed { index, line ->
                val ktMatch = parcelableKotlinPattern.find(line)
                    ?: parcelableJavaPattern.find(line)
                if (ktMatch != null) {
                    val simpleName = ktMatch.groupValues[1]
                    val fqn = "${source.packageName}.$simpleName"
                    parcelableClasses[simpleName] = fqn to (index + 1)
                    pendingClassName = null  // resolved on this line
                } else {
                    // Check for multi-line constructor closing matching ): Parcelable
                    if (pendingClassName != null && parcelableMultilineClosingPattern.containsMatchIn(line)) {
                        val simpleName = pendingClassName!!
                        val fqn = "${source.packageName}.$simpleName"
                        parcelableClasses[simpleName] = fqn to (index + 1)
                        pendingClassName = null
                    } else {
                        // Check if this is a class declaration with a multi-line constructor
                        // (pattern: `class Foo(` without `:` yet)
                        val classOnlyPattern = Regex("""^\s*(?:data\s+)?class\s+([A-Z][a-zA-Z0-9]+)\s*\(""")
                        val classMatch = classOnlyPattern.find(line)
                        if (classMatch != null && !line.contains(": ")) {
                            pendingClassName = classMatch.groupValues[1]
                        } else if (!line.contains("class ")) {
                            // Non-class line resets pending only if it's a closing context
                            if (line.trim().startsWith("{") || line.trim().startsWith("}")) {
                                pendingClassName = null
                            }
                        }
                    }
                }
            }
        }

        // Second pass: find files that use bundle/parcel operations with these classes
        for (source in sourceFiles) {
            val fullContent = source.lines.joinToString("\n")

            // Skip files that don't use parcel operations at all
            val hasParcelOps = parcelWritePattern.containsMatchIn(fullContent) ||
                parcelReadPattern.containsMatchIn(fullContent)
            if (!hasParcelOps) continue

            // Find which Parcelable classes are used in this file
            val usedClasses = mutableSetOf<String>()
            source.lines.forEach { line ->
                constructorCallPattern.findAll(line).forEach { match ->
                    val simpleName = match.groupValues[1]
                    if (simpleName in parcelableClasses) {
                        usedClasses.add(simpleName)
                    }
                }
                // Also check direct imports
                source.imports.forEach { import ->
                    val simpleName = import.substringAfterLast(".")
                    if (simpleName in parcelableClasses) {
                        usedClasses.add(simpleName)
                    }
                }
            }

            // For each Parcelable class used with parcel operations → report issue
            for (simpleName in usedClasses) {
                val (fqn, _) = parcelableClasses[simpleName] ?: continue

                // Find the line where the parcel operation occurs
                val parcelOpLine = source.lines.indexOfFirst { line ->
                    parcelWritePattern.containsMatchIn(line) || parcelReadPattern.containsMatchIn(line)
                }

                val lineNum = if (parcelOpLine >= 0) parcelOpLine + 1 else 1
                issues.add(createIssue(source, lineNum, fqn))
            }
        }

        return issues
    }

    private fun createIssue(source: SourceFile, lineNumber: Int, className: String): ProguardIssue {
        return ProguardIssue(
            type = issueType,
            severity = Severity.ERROR,
            filePath = source.file.path,
            lineNumber = lineNumber,
            message = "Parcelable class '$className' is used in Bundle/Intent serialization. " +
                "R8 renames this class, but Android stores the original class name in the Parcel. " +
                "On deserialization, Class.forName(originalName) fails → BadParcelableException.",
            suggestion = "Add to proguard-rules.pro: -keep class $className { *; }",
            className = className
        )
    }
}
