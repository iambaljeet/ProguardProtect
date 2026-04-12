package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.SourceFile

/**
 * Detects sealed class `sealedSubclasses` reflection usage vulnerable to R8 subtype removal.
 *
 * Kotlin's `sealedSubclasses` property relies on Kotlin metadata to enumerate subtypes.
 * R8 removes sealed subtypes that aren't directly referenced in non-reflective code paths,
 * causing `sealedSubclasses` to return a partial or empty list at runtime.
 *
 * For each detected sealed class with sealedSubclasses access:
 * - Finds all subtypes declared in the source files
 * - Creates one issue per sealed class listing all expected subtypes
 * - Post-build cross-reference checks if subtypes are missing from DEX
 *
 * Common in: state machine registries, UI state dispatchers, serializers
 * that enumerate sealed subtypes dynamically.
 */
class SealedClassAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.SEALED_SUBCLASS_STRIPPED

    // Matches: SomeClass::class.sealedSubclasses
    private val sealedSubclassesPattern = Regex(
        """(\w+(?:\.\w+)*)::class\.sealedSubclasses"""
    )

    // Matches sealed class declaration: sealed class ClassName
    private val sealedClassDeclPattern = Regex(
        """^\s*sealed\s+class\s+([A-Z][a-zA-Z0-9]+)"""
    )

    // Matches subtype declaration: object/data class/class SubType : SealedClass()
    private val subtypePattern = Regex(
        """^\s*(?:object|data\s+class|class)\s+([A-Z][a-zA-Z0-9]+)\s*(?:\([^)]*\))?\s*:\s*(\w+)"""
    )

    // Also matches inner object/class inside sealed class body
    private val innerSubtypePattern = Regex(
        """^\s*(?:object|data\s+class|class)\s+([A-Z][a-zA-Z0-9]+)"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()

        // First pass: collect all sealed class declarations and their subtypes
        val sealedClasses = mutableMapOf<String, String>() // simpleName → FQN
        val sealedSubtypes = mutableMapOf<String, MutableList<String>>() // sealedFQN → [subtypeFQNs]

        for (source in sourceFiles) {
            val pkg = source.packageName
            var insideSealedClass: String? = null
            var braceDepth = 0

            source.lines.forEachIndexed { _, line ->
                val sealedMatch = sealedClassDeclPattern.find(line)
                if (sealedMatch != null) {
                    val simpleName = sealedMatch.groupValues[1]
                    val fqn = "$pkg.$simpleName"
                    sealedClasses[simpleName] = fqn
                    sealedSubtypes.getOrPut(fqn) { mutableListOf() }
                    insideSealedClass = fqn
                    braceDepth = 0
                }

                if (insideSealedClass != null) {
                    braceDepth += line.count { it == '{' } - line.count { it == '}' }
                    if (braceDepth <= 0 && line.contains('}')) {
                        insideSealedClass = null
                        braceDepth = 0
                    }
                }
            }

        }

        // Find subtypes that explicitly extend a sealed class (in same or different file).
        // NOTE: This loop must be OUTSIDE the outer source-collection loop above to avoid
        // running N times per outer iteration (O(N²) duplicates).
        //
        // IMPORTANT: Skip subtypes defined in the SAME file as their sealed parent class.
        // Subtypes nested inside the sealed class body (e.g., `object Empty : CheckoutState()`)
        // compile to `SealedClass$SubtypeName` in bytecode — they are handled by the inner-class
        // collector pass below. Adding them here as `pkg.SubtypeName` (top-level format) would
        // create duplicates with a wrong FQN since no such top-level class exists in the DEX.
        for (source2 in sourceFiles) {
            val pkg2 = source2.packageName
            // Check if this file ALSO contains the sealed class declaration (i.e., subtypes are inner)
            val hasSealedDecl = source2.lines.any { sealedClassDeclPattern.containsMatchIn(it) }

            source2.lines.forEach { line ->
                val match = subtypePattern.find(line) ?: return@forEach
                val subtypeName = match.groupValues[1]
                val parentName = match.groupValues[2]
                val sealedFqn = sealedClasses[parentName] ?: return@forEach

                // If the subtype is in the same file as the sealed parent, skip it here —
                // the inner-class collector pass will add it with the correct `$`-nested FQN.
                if (hasSealedDecl && sealedFqn.startsWith(pkg2)) return@forEach

                val subtypeFqn = "$pkg2.$subtypeName"
                val list = sealedSubtypes[sealedFqn]
                if (list != null && !list.contains(subtypeFqn)) {
                    list.add(subtypeFqn)
                }
            }
        }

        // Also collect subtypes defined inside sealed class files using indented declarations
        for (source in sourceFiles) {
            val pkg = source.packageName
            var insideSealedFqn: String? = null
            var depth = 0

            source.lines.forEachIndexed { _, line ->
                val sealedMatch = sealedClassDeclPattern.find(line)
                if (sealedMatch != null) {
                    val simpleName = sealedMatch.groupValues[1]
                    insideSealedFqn = "$pkg.$simpleName"
                    depth = 1
                    return@forEachIndexed
                }

                if (insideSealedFqn != null) {
                    depth += line.count { it == '{' } - line.count { it == '}' }

                    if (depth == 1) {
                        // We're one level inside the sealed class body
                        val innerMatch = innerSubtypePattern.find(line)
                        if (innerMatch != null) {
                            val subtypeName = innerMatch.groupValues[1]
                            if (subtypeName != insideSealedFqn!!.substringAfterLast(".")) {
                                val subtypeFqn = "$insideSealedFqn\$$subtypeName"
                                val list = sealedSubtypes[insideSealedFqn!!]
                                if (list != null && !list.contains(subtypeFqn)) {
                                    list.add(subtypeFqn)
                                }
                            }
                        }
                    }

                    if (depth <= 0) {
                        insideSealedFqn = null
                        depth = 0
                    }
                }
            }
        }

        // Second pass: find sealedSubclasses usage and report issues
        for (source in sourceFiles) {
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                sealedSubclassesPattern.findAll(line).forEach { match ->
                    val classRef = match.groupValues[1]
                    // Resolve to FQN
                    val resolvedFqn = if (classRef.contains(".")) classRef
                        else source.resolveClassName(classRef)
                        ?: sealedClasses[classRef]
                        ?: "${source.packageName}.$classRef"

                    val subtypes = sealedSubtypes[resolvedFqn]
                    val subtypeList = subtypes?.joinToString(",") ?: ""

                    issues.add(
                        ProguardIssue(
                            type = issueType,
                            severity = Severity.ERROR,
                            filePath = source.file.path,
                            lineNumber = index + 1,
                            message = "Sealed class '$resolvedFqn' uses sealedSubclasses reflection. " +
                                "R8 removes sealed subtypes that aren't directly referenced in code — " +
                                "sealedSubclasses may return an empty or incomplete list at runtime.",
                            suggestion = "Add to proguard-rules.pro:\n" +
                                "-keep class $resolvedFqn { *; }\n" +
                                "-keep class $resolvedFqn\$* { *; }",
                            className = resolvedFqn,
                            memberName = subtypeList.ifEmpty { null }
                        )
                    )
                }
            }
        }

        return issues
    }
}
