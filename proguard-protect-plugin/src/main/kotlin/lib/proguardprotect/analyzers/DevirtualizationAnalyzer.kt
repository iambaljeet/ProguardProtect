package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.SourceFile
import java.io.File

/**
 * Detects potential IllegalAccessError from R8 devirtualization.
 *
 * Pattern: A class extends a base class that has a private method AND implements
 * an interface with a default method of the same signature. If the child class
 * does NOT override that method, R8 may devirtualize the interface default method
 * call to the base class's private method, causing IllegalAccessError at runtime.
 */
/**
 * Detects R8 devirtualization hazards where a child class extends a base with a private method
 * and implements an interface with a default method of the same signature, without overriding it.
 *
 * R8 may resolve calls to the base class's private method instead of the interface default,
 * causing IllegalAccessError at runtime.
 */
class DevirtualizationAnalyzer : BaseAnalyzer() {

    override val issueType = ProguardIssue.IssueType.DEVIRTUALIZATION_ILLEGAL_ACCESS

    data class PrivateMethod(val className: String, val methodName: String, val file: File)
    data class DefaultMethod(val interfaceName: String, val methodName: String, val file: File)
    data class ClassInfo(
        val className: String,
        val parentClass: String?,
        val interfaces: List<String>,
        val methods: Set<String>,
        val file: File,
        val lineNumber: Int
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val privateMethods = mutableListOf<PrivateMethod>()
        val defaultMethods = mutableListOf<DefaultMethod>()
        val classInfos = mutableListOf<ClassInfo>()

        for (source in sourceFiles) {
            if (source.file.name.endsWith(".java")) {
                collectJavaPrivateMethods(source, privateMethods)
                collectJavaDefaultMethods(source, defaultMethods)
                collectJavaClassInfo(source, classInfos)
            }
            if (source.file.name.endsWith(".kt")) {
                collectKotlinClassInfo(source, classInfos)
            }
        }

        val privateMethodsByClass = privateMethods.groupBy { it.className }
        val defaultMethodsByInterface = defaultMethods.groupBy { it.interfaceName }

        for (classInfo in classInfos) {
            val parentClass = classInfo.parentClass ?: continue
            val parentPrivateMethods = privateMethodsByClass[parentClass] ?: continue

            for (iface in classInfo.interfaces) {
                val ifaceDefaultMethods = defaultMethodsByInterface[iface] ?: continue

                for (defaultMethod in ifaceDefaultMethods) {
                    val conflictingPrivate = parentPrivateMethods.find {
                        it.methodName == defaultMethod.methodName
                    }
                    if (conflictingPrivate != null && defaultMethod.methodName !in classInfo.methods) {
                        val fqClassName = resolveFullName(classInfo.className, classInfo.file)
                        issues.add(
                            ProguardIssue(
                                type = issueType,
                                severity = ProguardIssue.Severity.ERROR,
                                filePath = classInfo.file.path,
                                lineNumber = classInfo.lineNumber,
                                message = "Class ${classInfo.className} extends $parentClass (has private ${conflictingPrivate.methodName}()) " +
                                    "and implements $iface (has default ${defaultMethod.methodName}()) without overriding. " +
                                    "R8 devirtualization may resolve calls to the private base method → IllegalAccessError at runtime.",
                                suggestion = "Override ${defaultMethod.methodName}() in ${classInfo.className} and delegate to " +
                                    "$iface.super.${defaultMethod.methodName}(), or add: " +
                                    "-keep class $fqClassName { *; }",
                                className = fqClassName,
                                memberName = defaultMethod.methodName
                            )
                        )
                    }
                }
            }
        }

        return issues
    }

    private fun collectJavaPrivateMethods(source: SourceFile, result: MutableList<PrivateMethod>) {
        val className = source.file.nameWithoutExtension
        val regex = Regex("""private\s+\w+\s+(\w+)\s*\(""")
        for (line in source.lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue
            regex.find(line)?.let { result.add(PrivateMethod(className, it.groupValues[1], source.file)) }
        }
    }

    private fun collectJavaDefaultMethods(source: SourceFile, result: MutableList<DefaultMethod>) {
        val interfaceName = source.file.nameWithoutExtension
        val regex = Regex("""default\s+\w+\s+(\w+)\s*\(""")
        val isInterface = source.lines.any { l ->
            val t = l.trim()
            !t.startsWith("//") && !t.startsWith("*") && Regex("""(?:public\s+)?interface\s+$interfaceName""").containsMatchIn(t)
        }
        if (!isInterface) return
        for (line in source.lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue
            regex.find(line)?.let { result.add(DefaultMethod(interfaceName, it.groupValues[1], source.file)) }
        }
    }

    private fun collectJavaClassInfo(source: SourceFile, result: MutableList<ClassInfo>) {
        val classRegex = Regex("""(?:public\s+)?class\s+(\w+)\s+extends\s+(\w+)(?:\s+implements\s+([\w,\s]+))?\s*\{""")
        val methodRegex = Regex("""(?:public|protected|private)?\s*(?:static\s+)?(?:final\s+)?\w+\s+(\w+)\s*\(""")

        for ((index, line) in source.lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue

            classRegex.find(line)?.let { match ->
                val className = match.groupValues[1]
                val parentClass = match.groupValues[2]
                val interfaces = match.groupValues[3].takeIf { it.isNotBlank() }
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

                val methods = collectMethodsInBlock(source.lines, index, methodRegex)
                result.add(ClassInfo(className, parentClass, interfaces, methods, source.file, index + 1))
            }
        }
    }

    private fun collectKotlinClassInfo(source: SourceFile, result: MutableList<ClassInfo>) {
        val classRegex = Regex("""class\s+(\w+)(?:\s*\([^)]*\))?\s*:\s*(.+?)\s*\{""")
        val methodRegex = Regex("""(?:override\s+)?fun\s+(\w+)\s*\(""")

        for ((index, line) in source.lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue

            classRegex.find(trimmed)?.let { match ->
                val className = match.groupValues[1]
                val superTypes = match.groupValues[2]
                val parts = superTypes.split(",").map { it.trim() }
                var parentClass: String? = null
                val interfaces = mutableListOf<String>()

                for (part in parts) {
                    val typeName = part.replace(Regex("""\([^)]*\)"""), "").trim()
                    if (part.contains("(")) parentClass = typeName else interfaces.add(typeName)
                }

                val methods = collectMethodsInBlock(source.lines, index, methodRegex)
                result.add(ClassInfo(className, parentClass, interfaces, methods, source.file, index + 1))
            }
        }
    }

    private fun collectMethodsInBlock(lines: List<String>, startLine: Int, methodRegex: Regex): Set<String> {
        val methods = mutableSetOf<String>()
        var braceCount = 0
        var started = false
        for (i in startLine until lines.size) {
            val l = lines[i]
            braceCount += l.count { it == '{' } - l.count { it == '}' }
            if (l.contains("{")) started = true
            if (started && braceCount <= 0) break
            if (i > startLine) {
                val trimmed = l.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue
                methodRegex.find(l)?.let { methods.add(it.groupValues[1]) }
            }
        }
        return methods
    }

    private fun resolveFullName(simpleName: String, file: File): String {
        val packageLine = file.readLines().firstOrNull { it.trim().startsWith("package ") }
        val pkg = packageLine?.trim()?.removePrefix("package ")?.removeSuffix(";")?.trim() ?: ""
        return if (pkg.isNotEmpty()) "$pkg.$simpleName" else simpleName
    }
}
