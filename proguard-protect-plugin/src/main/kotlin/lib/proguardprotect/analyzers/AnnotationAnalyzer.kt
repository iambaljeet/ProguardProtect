package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.SourceFile

/**
 * Detects custom `@Retention(AnnotationRetention.RUNTIME)` annotation usage vulnerable to R8 stripping.
 *
 * R8 can strip custom annotation classes when `-keepattributes *Annotation*` is absent.
 * Code that reads annotations at runtime via `getAnnotation()` will receive null,
 * causing NullPointerException when annotation properties are accessed.
 *
 * Patterns detected:
 * 1. `getAnnotation(SomeAnnotation::class.java)` calls — direct annotation reading
 * 2. `annotations.filterIsInstance<SomeAnnotation>()` — annotation enumeration
 * 3. Annotation classes defined with `@Retention(AnnotationRetention.RUNTIME)` that are
 *    referenced in class-level usages (e.g., `@SomeAnnotation(...)` on a data class)
 */
class AnnotationAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.ANNOTATION_STRIPPED

    // Matches: getAnnotation(SomeAnnotation::class.java)
    private val getAnnotationPattern = Regex(
        """getAnnotation\s*\(\s*([A-Z][a-zA-Z0-9]+)::class\.java\s*\)"""
    )

    // Matches: annotations.filterIsInstance<SomeAnnotation>() or filterIsInstance<SomeAnnotation>()
    private val filterIsInstancePattern = Regex(
        """filterIsInstance\s*<\s*([A-Z][a-zA-Z0-9]+)\s*>"""
    )

    // Matches: @Retention(AnnotationRetention.RUNTIME) annotation — marks annotation class declarations
    private val runtimeRetentionPattern = Regex(
        """@Retention\s*\(\s*AnnotationRetention\.RUNTIME\s*\)"""
    )

    // Matches: annotation class ClassName
    private val annotationClassPattern = Regex(
        """^\s*annotation\s+class\s+([A-Z][a-zA-Z0-9]+)"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()

        // First pass: collect all runtime-retained annotation class definitions
        val runtimeAnnotations = mutableMapOf<String, String>() // simpleName → FQN

        for (source in sourceFiles) {
            val lines = source.lines
            lines.forEachIndexed { index, line ->
                if (runtimeRetentionPattern.containsMatchIn(line)) {
                    // Next non-blank line should be the annotation class declaration
                    val nextLines = lines.drop(index + 1).take(3)
                    for (nextLine in nextLines) {
                        val annotMatch = annotationClassPattern.find(nextLine)
                        if (annotMatch != null) {
                            val simpleName = annotMatch.groupValues[1]
                            val fqn = "${source.packageName}.$simpleName"
                            runtimeAnnotations[simpleName] = fqn
                            break
                        }
                    }
                }
            }
        }

        // Second pass: find getAnnotation() / filterIsInstance() usage referencing runtime annotations
        for (source in sourceFiles) {
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                // Check getAnnotation(SomeAnnotation::class.java)
                getAnnotationPattern.findAll(line).forEach { match ->
                    val simpleName = match.groupValues[1]
                    val resolved = source.resolveClassName(simpleName)
                        ?: runtimeAnnotations[simpleName]
                        ?: "${source.packageName}.$simpleName"
                    // Only flag if we know it's a runtime-retained annotation or starts with app.
                    if (runtimeAnnotations.containsKey(simpleName) || runtimeAnnotations.containsValue(resolved)) {
                        issues.add(createIssue(source, index + 1, resolved))
                    } else if (resolved.startsWith("app.")) {
                        // May be a runtime annotation we couldn't fully resolve
                        issues.add(createIssue(source, index + 1, resolved))
                    }
                }

                // Check filterIsInstance<SomeAnnotation>()
                filterIsInstancePattern.findAll(line).forEach { match ->
                    val simpleName = match.groupValues[1]
                    val resolved = source.resolveClassName(simpleName)
                        ?: runtimeAnnotations[simpleName]
                        ?: "${source.packageName}.$simpleName"
                    if (runtimeAnnotations.containsKey(simpleName) || resolved.startsWith("app.")) {
                        issues.add(createIssue(source, index + 1, resolved))
                    }
                }
            }
        }

        return issues
    }

    private fun createIssue(source: SourceFile, lineNumber: Int, annotationClass: String): ProguardIssue {
        return ProguardIssue(
            type = issueType,
            severity = Severity.ERROR,
            filePath = source.file.path,
            lineNumber = lineNumber,
            message = "Custom runtime annotation '$annotationClass' read via reflection. " +
                "R8 strips annotation classes without -keepattributes *Annotation*, " +
                "causing getAnnotation() to return null → NullPointerException at runtime.",
            suggestion = "Add to proguard-rules.pro:\n" +
                "-keepattributes *Annotation*\n" +
                "-keep @interface $annotationClass { *; }\n" +
                "-keepclassmembers class ** { @$annotationClass *; }",
            className = annotationClass
        )
    }
}
