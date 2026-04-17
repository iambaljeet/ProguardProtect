package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects Kotlin `@JvmOverloads` functions that are vulnerable to R8 stripping generated overloads.
 *
 * `@JvmOverloads` generates synthetic Java overloads for every combination of default parameters.
 * R8 may remove overloads that are not directly called from Kotlin code (only the full-argument
 * version is usually called from Kotlin). Java interop code or reflection that calls the shorter
 * overloads will receive `NoSuchMethodError` at runtime.
 *
 * Detection strategy:
 * 1. Find functions annotated with `@JvmOverloads` that also have default parameter values
 * 2. Extract the class FQCN and method name
 * 3. Post-build: confirmed if the class was renamed in mapping (overload names change with class)
 *    OR if the method itself was renamed (meaning the overloads are also renamed)
 *
 * Ref: https://kotlinlang.org/docs/java-to-kotlin-interop.html#overloads-generation
 */
class JvmOverloadsAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.JVMOVERLOADS_OVERLOAD_STRIPPED

    // Match @JvmOverloads on its own line or inline before fun
    private val jvmOverloadsPattern = Regex("""@JvmOverloads""")

    // Match function declaration (possibly on the line after @JvmOverloads)
    private val funDeclPattern = Regex("""fun\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*\(""")

    // Match default parameter values (the presence of = after a parameter type)
    private val defaultParamPattern = Regex(""":\s*\w[\w<>?]*\s*=\s*""")

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        for (source in sourceFiles) {
            val pkg = source.packageName
            val className = inferClassName(source) ?: continue
            val fullClassName = "$pkg.$className"

            val lines = source.lines
            var i = 0
            while (i < lines.size) {
                val trimmed = lines[i].trim()

                // Skip comment lines
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    i++
                    continue
                }

                if (jvmOverloadsPattern.containsMatchIn(trimmed)) {
                    // Look ahead up to 3 lines for the fun declaration
                    val lookahead = (i until minOf(i + 4, lines.size))
                    val funLine = lookahead.firstOrNull { idx ->
                        val l = lines[idx].trim()
                        !l.startsWith("//") && !l.startsWith("*") && funDeclPattern.containsMatchIn(l)
                    }

                    if (funLine != null) {
                        val funMatch = funDeclPattern.find(lines[funLine].trim())
                        val methodName = funMatch?.groupValues?.get(1) ?: run { i++; continue }

                        // Collect the full function signature (may span multiple lines) to check for defaults
                        val sigLines = collectSignatureLines(lines, funLine)
                        val hasDefaults = sigLines.any { defaultParamPattern.containsMatchIn(it) }

                        if (hasDefaults && !parser.isClassKept(fullClassName, keepRules)) {
                            issues.add(
                                ProguardIssue(
                                    type = issueType,
                                    severity = Severity.ERROR,
                                    filePath = source.file.path,
                                    lineNumber = funLine + 1,
                                    message = "@JvmOverloads function '$methodName' in '$fullClassName' generates synthetic overloads. " +
                                        "R8 may strip overloads with fewer arguments than the full signature since they are not " +
                                        "called from Kotlin. Java interop or reflection calling a shorter overload will get " +
                                        "NoSuchMethodError at runtime.",
                                    suggestion = "Add to proguard-rules.pro:\n" +
                                        "-keepclassmembers class $fullClassName {\n    public *** $methodName(...);\n}",
                                    className = fullClassName,
                                    memberName = methodName
                                )
                            )
                        }
                    }
                }
                i++
            }
        }

        return issues
    }

    /** Collects lines from the opening parenthesis of the function signature until the closing parenthesis. */
    private fun collectSignatureLines(lines: List<String>, startLine: Int): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        for (i in startLine until minOf(startLine + 15, lines.size)) {
            val line = lines[i]
            result.add(line)
            depth += line.count { it == '(' } - line.count { it == ')' }
            if (depth <= 0 && i > startLine) break
        }
        return result
    }

    private fun inferClassName(source: SourceFile): String? {
        val classPattern = Regex("""(?:^|\s)(?:class|object|data\s+class|open\s+class|abstract\s+class)\s+([A-Z][a-zA-Z0-9]+)""")
        return source.lines.firstNotNullOfOrNull { classPattern.find(it) }?.groupValues?.get(1)
    }
}
