package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects reflective method access (getDeclaredMethod/getMethod) where the target class
 * does not have ProGuard keep rules for its methods.
 * Without keep rules, R8 renames methods → NoSuchMethodError at runtime.
 */
/**
 * Detects reflective method and field access (`getDeclaredMethod`, `getDeclaredField`)
 * where R8 may rename the target member.
 *
 * Scans for patterns like `clazz.getDeclaredMethod("methodName")` and
 * `clazz.getDeclaredField(dynamicName)` where the member name is a string
 * constant or dynamically constructed variable.
 */
class ReflectionMethodAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.REFLECTION_METHOD_ACCESS

    // Matches: SomeClass::class.java.getDeclaredMethod("methodName" ...)
    private val kotlinReflectPattern = Regex(
        """(\w+)::class\.java\s*\.\s*(?:getDeclaredMethod|getMethod)\s*\(\s*"([^"]+)""""
    )

    // Matches: Class.forName("...").getDeclaredMethod("methodName" ...)
    private val forNameMethodPattern = Regex(
        """Class\.forName\s*\(\s*"([^"]+)"\s*\)\s*\.\s*(?:getDeclaredMethod|getMethod)\s*\(\s*"([^"]+)""""
    )

    // Matches standalone: .getDeclaredMethod("methodName") or .getMethod("methodName")
    private val standaloneMethodPattern = Regex(
        """\.(?:getDeclaredMethod|getMethod)\s*\(\s*"([^"]+)""""
    )

    // Matches: .getDeclaredField("fieldName") or .getField("fieldName") with literal string
    private val fieldPattern = Regex(
        """\.(?:getDeclaredField|getField)\s*\(\s*"([^"]+)""""
    )

    // Matches: .getDeclaredField(variable) or .getField(variable) with dynamic arg
    private val dynamicFieldPattern = Regex(
        """\.(?:getDeclaredField|getField)\s*\(\s*(\w+)\s*\)"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        for (source in sourceFiles) {
            source.lines.forEachIndexed { index, line ->
                val handledRanges = mutableListOf<IntRange>()

                // Check Kotlin-style reflection: SomeClass::class.java.getDeclaredMethod(...)
                kotlinReflectPattern.findAll(line).forEach { match ->
                    handledRanges.add(match.range)
                    val simpleClassName = match.groupValues[1]
                    val methodName = match.groupValues[2]
                    val fullClassName = source.resolveClassName(simpleClassName) ?: "${source.packageName}.$simpleClassName"

                    if (!parser.isMethodKept(fullClassName, methodName, keepRules)) {
                        issues.add(createMethodIssue(source, index, fullClassName, methodName))
                    }
                }

                // Check Class.forName("...").getDeclaredMethod(...)
                forNameMethodPattern.findAll(line).forEach { match ->
                    handledRanges.add(match.range)
                    val className = match.groupValues[1]
                    val methodName = match.groupValues[2]

                    if (!parser.isMethodKept(className, methodName, keepRules)) {
                        issues.add(createMethodIssue(source, index, className, methodName))
                    }
                }

                // Check standalone .getDeclaredMethod("methodName") / .getMethod("methodName")
                // This catches patterns like: variable.getDeclaredMethod("name"), clazz.getDeclaredMethod("name")
                standaloneMethodPattern.findAll(line).forEach { match ->
                    // Skip if already handled by more specific patterns
                    if (handledRanges.any { it.first <= match.range.first && it.last >= match.range.last }) return@forEach

                    val methodName = match.groupValues[1]
                    val contextClassName = findClassContext(source, index)
                    if (contextClassName != null && !parser.isMethodKept(contextClassName, methodName, keepRules)) {
                        issues.add(createMethodIssue(source, index, contextClassName, methodName))
                    }
                }

                // Check field reflection (literal string)
                fieldPattern.findAll(line).forEach { match ->
                    val fieldName = match.groupValues[1]
                    val contextClassName = findClassContext(source, index)
                    if (contextClassName != null && !parser.isFieldKept(contextClassName, keepRules)) {
                        issues.add(
                            ProguardIssue(
                                type = issueType,
                                severity = Severity.ERROR,
                                filePath = source.file.path,
                                lineNumber = index + 1,
                                message = "Reflective field access getDeclaredField(\"$fieldName\") on class $contextClassName " +
                                    "without ProGuard keep rules. R8 will rename this field → NoSuchFieldError at runtime.",
                                suggestion = "Add to proguard-rules.pro: -keepclassmembers class $contextClassName { *; }",
                                className = contextClassName,
                                memberName = fieldName
                            )
                        )
                    }
                }

                // Check field reflection (dynamic/variable argument)
                dynamicFieldPattern.findAll(line).forEach { match ->
                    // Skip if the argument looks like a literal string (already caught above)
                    val arg = match.groupValues[1]
                    if (arg.startsWith("\"")) return@forEach

                    val contextClassName = findClassContext(source, index)
                    if (contextClassName != null && !parser.isFieldKept(contextClassName, keepRules)) {
                        issues.add(
                            ProguardIssue(
                                type = issueType,
                                severity = Severity.ERROR,
                                filePath = source.file.path,
                                lineNumber = index + 1,
                                message = "Reflective field access getDeclaredField($arg) on class $contextClassName " +
                                    "with dynamically constructed field name without ProGuard keep rules. " +
                                    "R8 will rename fields → NoSuchFieldError at runtime.",
                                suggestion = "Add to proguard-rules.pro: -keepclassmembers class $contextClassName { *; }",
                                className = contextClassName,
                                memberName = arg
                            )
                        )
                    }
                }
            }
        }

        return issues
    }

    private fun createMethodIssue(source: SourceFile, lineIndex: Int, className: String, methodName: String): ProguardIssue {
        return ProguardIssue(
            type = issueType,
            severity = Severity.ERROR,
            filePath = source.file.path,
            lineNumber = lineIndex + 1,
            message = "Reflective method access to \"$methodName\" on class $className " +
                "without ProGuard keep rules. R8 will rename this method → NoSuchMethodError at runtime.",
            suggestion = "Add to proguard-rules.pro: -keepclassmembers class $className { *; }",
            className = className,
            memberName = methodName
        )
    }

    private fun findClassContext(source: SourceFile, currentLine: Int): String? {
        val classForNamePattern = Regex("""Class\.forName\s*\(\s*"([^"]+)"""")
        val kotlinClassPattern = Regex("""(\w+)::class\.java""")
        val instantiationPattern = Regex("""(?:val|var)\s+\w+\s*=\s*(?:[\w.]+\.)?([A-Z]\w+)\s*\(""")
        val fqClassPattern = Regex("""((?:[a-z]\w*\.)+[A-Z]\w+)\s*\(""")
        // Pattern to find variable type declaration: val x = ClassName(...) or val x: ClassName
        val varDeclPattern = Regex("""(?:val|var)\s+(\w+)\s*(?::\s*(\w+))?(?:\s*=\s*(\w+)\s*\()?""")

        // Primitive/stdlib types to ignore as context classes
        val ignoreTypes = setOf(
            "Double", "Float", "Int", "Long", "Short", "Byte", "Char", "Boolean",
            "String", "Any", "Object", "Class", "Array", "List", "Map", "Set",
            "Function0", "Function1", "Function2", "Function3", "Function4",
            "Unit", "Nothing", "Pair", "Triple", "Result", "Comparable",
            "Serializable", "Cloneable", "Iterable", "Iterator", "Collection",
            "Sequence", "Number"
        )

        for (i in currentLine downTo maxOf(0, currentLine - 10)) {
            classForNamePattern.find(source.lines[i])?.let {
                return it.groupValues[1]
            }
            fqClassPattern.find(source.lines[i])?.let {
                return it.groupValues[1]
            }
            // For Kotlin ::class.java pattern
            kotlinClassPattern.findAll(source.lines[i]).forEach { match ->
                val simpleName = match.groupValues[1]
                if (simpleName !in ignoreTypes) {
                    // If the name starts with lowercase, it's a variable — resolve its type
                    if (simpleName[0].isLowerCase()) {
                        val resolvedType = resolveVariableType(source, i, simpleName)
                        if (resolvedType != null) return resolvedType
                    } else {
                        return source.resolveClassName(simpleName) ?: "${source.packageName}.$simpleName"
                    }
                }
            }
            instantiationPattern.find(source.lines[i])?.let {
                val simpleName = it.groupValues[1]
                if (simpleName !in ignoreTypes) {
                    return source.resolveClassName(simpleName) ?: "${source.packageName}.$simpleName"
                }
            }
        }
        return null
    }

    /**
     * Resolve a variable name to its class type by searching for its declaration.
     */
    private fun resolveVariableType(source: SourceFile, fromLine: Int, varName: String): String? {
        val declPattern = Regex("""(?:val|var)\s+${Regex.escape(varName)}\s*(?::\s*([A-Z]\w+))?(?:\s*=\s*([A-Z]\w+)\s*\()?""")
        for (i in fromLine downTo maxOf(0, fromLine - 20)) {
            declPattern.find(source.lines[i])?.let { match ->
                val explicitType = match.groupValues[1].takeIf { it.isNotEmpty() }
                val constructorType = match.groupValues[2].takeIf { it.isNotEmpty() }
                val typeName = explicitType ?: constructorType
                if (typeName != null) {
                    return source.resolveClassName(typeName) ?: "${source.packageName}.$typeName"
                }
            }
        }
        return null
    }
}
