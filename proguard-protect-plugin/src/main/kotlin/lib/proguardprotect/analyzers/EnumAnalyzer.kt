package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects Enum.valueOf() or enumValueOf() calls where enum constants are
 * accessed by string name without ProGuard keep rules.
 * Also detects dynamic enum class loading via Class.forName + enumConstants.
 * Without protection, R8 renames enum class/constants → crashes at runtime.
 */
/**
 * Detects dynamically accessed enum classes that R8 may rename or strip.
 *
 * Patterns: `Class.forName("...enum").enumConstants` or `Enum.valueOf(clazz, name)` where
 * the enum class is loaded via dynamically constructed string. R8 renames enum constants and
 * classes → ClassNotFoundException or IllegalArgumentException at runtime.
 */
class EnumAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.ENUM_VALUE_OF

    // Matches: MyEnum.valueOf("SOME_VALUE")
    private val enumValueOfPattern = Regex(
        """(\w+)\.valueOf\s*\(\s*"([^"]+)"\s*\)"""
    )

    // Matches: enumValueOf<MyEnum>("SOME_VALUE")
    private val kotlinEnumValueOfPattern = Regex(
        """enumValueOf\s*<\s*(\w+)\s*>\s*\(\s*"([^"]+)"\s*\)"""
    )

    // Matches: java.lang.Enum.valueOf(MyEnum::class.java, "SOME_VALUE")
    private val javaEnumValueOfPattern = Regex(
        """Enum\.valueOf\s*\(\s*(\w+)::class\.java\s*,\s*"([^"]+)"\s*\)"""
    )

    // Matches dynamic patterns: .enumConstants, Enum.valueOf(variable, ...)
    private val enumConstantsPattern = Regex("""\.enumConstants""")
    private val dynamicEnumValueOfPattern = Regex("""Enum\.valueOf\s*\(""")

    // Detects Class.forName with dynamic string construction referencing an enum
    private val classForNamePattern = Regex("""Class\.forName\s*\(""")

    // Detects string construction patterns that build class names
    private val joinToStringPattern = Regex("""listOf\s*\(([^)]+)\)\.joinToString\s*\(\s*"([^"]*)"\s*\)""")
    private val stringInterpolPattern = Regex("""\$\{?\w+}?\.\w+""")

    // Detects enum class declarations
    private val enumDeclPattern = Regex(
        """enum\s+class\s+(\w+)"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        // Collect all declared enums: simpleName -> fullName
        val declaredEnums = mutableMapOf<String, String>()
        for (source in sourceFiles) {
            source.lines.forEach { line ->
                val trimmed = line.trim()
                // Skip comment lines to avoid false positives
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEach
                enumDeclPattern.find(line)?.let { match ->
                    val name = match.groupValues[1]
                    declaredEnums[name] = "${source.packageName}.$name"
                }
            }
        }

        for (source in sourceFiles) {
            val reportedLines = mutableSetOf<Int>()

            source.lines.forEachIndexed { index, line ->
                // Skip comment lines
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                // Check MyEnum.valueOf("...")
                enumValueOfPattern.findAll(line).forEach { match ->
                    val enumName = match.groupValues[1]
                    val valueName = match.groupValues[2]
                    val fullName = declaredEnums[enumName]
                        ?: source.resolveClassName(enumName)
                        ?: "${source.packageName}.$enumName"

                    if (declaredEnums.containsKey(enumName) && !parser.isEnumKept(fullName, keepRules) && reportedLines.add(index)) {
                        issues.add(createEnumIssue(source, index, fullName, enumName, valueName))
                    }
                }

                // Check enumValueOf<MyEnum>("...")
                kotlinEnumValueOfPattern.findAll(line).forEach { match ->
                    val enumName = match.groupValues[1]
                    val valueName = match.groupValues[2]
                    val fullName = declaredEnums[enumName]
                        ?: source.resolveClassName(enumName)
                        ?: "${source.packageName}.$enumName"

                    if (declaredEnums.containsKey(enumName) && !parser.isEnumKept(fullName, keepRules) && reportedLines.add(index)) {
                        issues.add(createEnumIssue(source, index, fullName, enumName, valueName))
                    }
                }

                // Check Enum.valueOf(MyEnum::class.java, "...")
                javaEnumValueOfPattern.findAll(line).forEach { match ->
                    val enumName = match.groupValues[1]
                    val valueName = match.groupValues[2]
                    val fullName = declaredEnums[enumName]
                        ?: source.resolveClassName(enumName)
                        ?: "${source.packageName}.$enumName"

                    if (declaredEnums.containsKey(enumName) && !parser.isEnumKept(fullName, keepRules) && reportedLines.add(index)) {
                        issues.add(createEnumIssue(source, index, fullName, enumName, valueName))
                    }
                }

                // Check dynamic enum access: .enumConstants or Enum.valueOf(variable, ...)
                if (enumConstantsPattern.containsMatchIn(line) || dynamicEnumValueOfPattern.containsMatchIn(line)) {
                    // Look backward to find which enum class is being accessed
                    val enumClass = findDynamicEnumClass(source, index, declaredEnums)
                    if (enumClass != null && reportedLines.add(index)) {
                        val fullName = declaredEnums[enumClass] ?: "${source.packageName}.$enumClass"
                        if (!parser.isClassKept(fullName, keepRules)) {
                            issues.add(
                                ProguardIssue(
                                    type = issueType,
                                    severity = Severity.ERROR,
                                    filePath = source.file.path,
                                    lineNumber = index + 1,
                                    message = "Enum $enumClass is accessed dynamically (enumConstants/Enum.valueOf) without ProGuard keep rules. " +
                                        "R8 will rename the enum class and constants → crash at runtime.",
                                    suggestion = "Add to proguard-rules.pro: -keep class $fullName { *; }",
                                    className = fullName,
                                    memberName = null
                                )
                            )
                        }
                    }
                }
            }

            // Also check: if any Class.forName in this file references a known enum class
            detectEnumClassForName(source, declaredEnums, parser, keepRules, issues)
        }

        return issues
    }

    /**
     * Detect when Class.forName() is used to load a known enum class dynamically.
     * Looks for enum class names appearing in string construction patterns nearby.
     */
    private fun detectEnumClassForName(
        source: SourceFile,
        declaredEnums: Map<String, String>,
        parser: ProguardRulesParser,
        keepRules: List<KeepRule>,
        issues: MutableList<ProguardIssue>
    ) {
        val fileContent = source.lines.joinToString("\n")
        val reportedEnums = mutableSetOf<String>()

        // Check if file uses Class.forName
        if (!classForNamePattern.containsMatchIn(fileContent)) return
        // Check if file also accesses enum-specific features
        if (!enumConstantsPattern.containsMatchIn(fileContent) &&
            !dynamicEnumValueOfPattern.containsMatchIn(fileContent) &&
            !fileContent.contains(".name ==") && !fileContent.contains(".name ==")) return

        // Look for enum class names referenced in string literals or string construction
        for ((simpleName, fullName) in declaredEnums) {
            if (reportedEnums.contains(simpleName)) continue

            // Check if this enum's name appears as part of a dynamic string in the file
            val nameAppears = fileContent.contains("\"$simpleName\"") ||
                fileContent.contains(".$simpleName") ||
                fileContent.contains("\"$fullName\"")

            // Also check if the package parts appear in joinToString patterns
            val packageParts = fullName.split(".")
            val packageInJoinToString = source.lines.any { line ->
                joinToStringPattern.find(line)?.let { match ->
                    val parts = match.groupValues[1]
                    packageParts.dropLast(1).all { parts.contains("\"$it\"") }
                } ?: false
            }

            // Check if enum name is appended to a package string
            val enumNameAppended = source.lines.any { line ->
                line.contains("\"\$") && line.contains(simpleName) && !line.trim().startsWith("//")
            }

            if (nameAppears || packageInJoinToString || enumNameAppended) {
                if (!parser.isClassKept(fullName, keepRules) && reportedEnums.add(simpleName)) {
                    // Find the line with Class.forName for reporting
                    val lineIndex = source.lines.indexOfFirst { classForNamePattern.containsMatchIn(it) }
                    issues.add(
                        ProguardIssue(
                            type = issueType,
                            severity = Severity.ERROR,
                            filePath = source.file.path,
                            lineNumber = if (lineIndex >= 0) lineIndex + 1 else 1,
                            message = "Enum class $simpleName ($fullName) is loaded dynamically via Class.forName without keep rules. " +
                                "R8 will rename the class → ClassNotFoundException at runtime.",
                            suggestion = "Add to proguard-rules.pro: -keep class $fullName { *; }",
                            className = fullName,
                            memberName = null
                        )
                    )
                }
            }
        }
    }

    /**
     * Look backward from the current line to find which enum class is being accessed dynamically.
     */
    private fun findDynamicEnumClass(
        source: SourceFile,
        lineIndex: Int,
        declaredEnums: Map<String, String>
    ): String? {
        // Check up to 15 lines backward for enum class references
        val startLine = maxOf(0, lineIndex - 15)
        for (i in lineIndex downTo startLine) {
            val line = source.lines[i]
            // Look for Class<Enum<*>> cast or enum class name reference
            for (enumName in declaredEnums.keys) {
                if (line.contains(enumName) && !line.trim().startsWith("//")) {
                    return enumName
                }
            }
            // Look for "Enum<*>" or "Enum<" suggesting enum class usage
            if (line.contains("Enum<") || line.contains("enumConstants")) {
                // Check if any enum name appears further up
                for (j in i downTo startLine) {
                    for (enumName in declaredEnums.keys) {
                        if (source.lines[j].contains(enumName)) return enumName
                    }
                }
            }
        }
        return null
    }

    private fun createEnumIssue(
        source: SourceFile, lineIndex: Int,
        fullName: String, simpleName: String, valueName: String
    ): ProguardIssue {
        return ProguardIssue(
            type = issueType,
            severity = Severity.ERROR,
            filePath = source.file.path,
            lineNumber = lineIndex + 1,
            message = "Enum $simpleName is accessed by string value \"$valueName\" without ProGuard keep rules. " +
                "R8 will rename enum constants → IllegalArgumentException at runtime.",
            suggestion = "Add to proguard-rules.pro: -keepclassmembers enum $fullName { *; }",
            className = fullName,
            memberName = valueName
        )
    }
}
