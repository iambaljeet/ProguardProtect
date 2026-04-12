package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects callback interface implementations that are instantiated dynamically
 * (e.g., via Class.forName or ServiceLoader) without ProGuard keep rules.
 * Without protection, R8 may strip the implementation → AbstractMethodError or
 * ClassNotFoundException at runtime.
 */
/**
 * Detects dynamically instantiated callback/interface implementations that R8 may remove.
 *
 * Pattern: `Class.forName("...impl").newInstance() as SomeInterface` — if the implementation
 * class has no static references, R8 strips it → ClassNotFoundException or AbstractMethodError.
 */
class CallbackAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.CALLBACK_INTERFACE_STRIPPED

    // Matches: Class.forName("...").newInstance() or getDeclaredConstructor
    private val dynamicInstantiationPattern = Regex(
        """Class\.forName\s*\(\s*"([^"]+)"\s*\)[\s\S]*?\.(?:newInstance|getDeclaredConstructor)"""
    )

    // Matches: ServiceLoader.load(SomeInterface::class.java)
    private val serviceLoaderPattern = Regex(
        """ServiceLoader\.load\s*\(\s*(\w+)::class\.java\s*\)"""
    )

    // Detects Class.forName() calls (dynamic or literal)
    private val classForNamePattern = Regex("""Class\.forName\s*\(""")

    // Detects newInstance or getDeclaredConstructor usage
    private val newInstancePattern = Regex("""\.(?:newInstance|getDeclaredConstructor)\s*\(""")

    // Detects interface cast: "as SomeInterface"
    private val interfaceCastPattern = Regex("""as\s+([\w.]+)""")

    // Detects class declarations implementing interfaces
    private val implementsPattern = Regex(
        """class\s+(\w+)(?:\s*<[^>]*>)?\s*(?:\([^)]*\))?\s*:\s*([^{]+)"""
    )

    // Detects interface declarations
    private val interfaceDeclPattern = Regex("""interface\s+(\w+)""")

    // Dynamic string construction: joinToString, string interpolation
    private val joinToStringPattern = Regex("""listOf\s*\(([^)]+)\)\.joinToString\s*\(\s*"([^"]*)"\s*\)""")

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        // Collect declared interfaces
        val declaredInterfaces = mutableSetOf<String>()
        // Collect classes that implement interfaces: simpleName -> (fullName, interfaceNames)
        val implementations = mutableMapOf<String, Pair<String, List<String>>>()

        for (source in sourceFiles) {
            source.lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEach

                interfaceDeclPattern.find(line)?.let { match ->
                    declaredInterfaces.add(match.groupValues[1])
                }

                implementsPattern.find(line)?.let { match ->
                    val className = match.groupValues[1]
                    val parents = match.groupValues[2].split(",").map { it.trim().split("(")[0].split("<")[0].trim() }
                    val fullClassName = "${source.packageName}.$className"
                    implementations[className] = fullClassName to parents.filter { it.isNotEmpty() && it[0].isUpperCase() }
                }
            }
        }

        for (source in sourceFiles) {
            val fileContent = source.lines.joinToString("\n")
            val reportedClasses = mutableSetOf<String>()

            // Check literal Class.forName("...").newInstance() patterns
            source.lines.forEachIndexed { index, line ->
                dynamicInstantiationPattern.findAll(line).forEach { match ->
                    val className = match.groupValues[1]
                    if (!parser.isClassKept(className, keepRules) && reportedClasses.add(className)) {
                        issues.add(
                            ProguardIssue(
                                type = issueType,
                                severity = Severity.ERROR,
                                filePath = source.file.path,
                                lineNumber = index + 1,
                                message = "Class $className is instantiated dynamically via Class.forName().newInstance() " +
                                    "without ProGuard keep rules. R8 may strip this class → ClassNotFoundException " +
                                    "or AbstractMethodError at runtime.",
                                suggestion = "Add to proguard-rules.pro: -keep class $className { *; }",
                                className = className
                            )
                        )
                    }
                }
            }

            // Check dynamic patterns: Class.forName(variable) + newInstance/getDeclaredConstructor
            if (classForNamePattern.containsMatchIn(fileContent) && newInstancePattern.containsMatchIn(fileContent)) {
                // Look for interface casts that indicate callback pattern
                val castedInterfaces = mutableSetOf<String>()
                source.lines.forEach { line ->
                    interfaceCastPattern.findAll(line).forEach { match ->
                        val castType = match.groupValues[1].split(".").last()
                        if (declaredInterfaces.contains(castType)) {
                            castedInterfaces.add(castType)
                        }
                    }
                }

                // Find implementation classes referenced in dynamic string construction
                for ((simpleName, pair) in implementations) {
                    val (fullName, parentInterfaces) = pair
                    if (reportedClasses.contains(fullName)) continue

                    // Check if this class name appears in dynamic strings
                    val nameAppears = fileContent.contains("\"$simpleName\"") ||
                        fileContent.contains(".$simpleName") ||
                        source.lines.any { line ->
                            line.contains("\"\$") && line.contains(simpleName) && !line.trim().startsWith("//")
                        }

                    // Check if any of its interfaces are cast-to in this file
                    val interfaceUsed = parentInterfaces.any { castedInterfaces.contains(it) }

                    if (nameAppears && interfaceUsed && !parser.isClassKept(fullName, keepRules)) {
                        reportedClasses.add(fullName)
                        val lineIndex = source.lines.indexOfFirst {
                            it.contains(simpleName) && !it.trim().startsWith("//")
                        }
                        issues.add(
                            ProguardIssue(
                                type = issueType,
                                severity = Severity.ERROR,
                                filePath = source.file.path,
                                lineNumber = if (lineIndex >= 0) lineIndex + 1 else 1,
                                message = "Callback implementation $simpleName ($fullName) is loaded dynamically and cast to " +
                                    "${parentInterfaces.joinToString()} without keep rules. R8 will rename/strip it → " +
                                    "ClassNotFoundException or AbstractMethodError at runtime.",
                                suggestion = "Add to proguard-rules.pro: -keep class $fullName { *; }",
                                className = fullName
                            )
                        )
                    }
                }
            }
        }

        return issues
    }
}
