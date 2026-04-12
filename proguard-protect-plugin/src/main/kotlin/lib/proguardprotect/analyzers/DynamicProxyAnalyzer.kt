package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects interfaces used with [java.lang.reflect.Proxy.newProxyInstance] that may
 * be renamed or stripped by R8, causing [IllegalArgumentException] or [AbstractMethodError].
 *
 * Java dynamic proxies (and Retrofit's interface proxy mechanism) work by:
 * 1. Taking an array of interface Class objects
 * 2. Creating a runtime class that implements those interfaces
 * 3. Dispatching method calls through an InvocationHandler
 *
 * R8 may rename interfaces that have no concrete implementation class. When the
 * interface is renamed, the Proxy mechanism may fail or produce incorrect behavior.
 * Interface method names being renamed also causes AbstractMethodError when the
 * InvocationHandler tries to dispatch to the renamed method.
 *
 * References:
 * - https://stackoverflow.com/questions/63671867/r8-proguard-dynamic-proxy-illegal-argument-exception
 * - https://issuetracker.google.com/issues/148495258
 * - https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#dynamic-proxies
 * - https://jakewharton.com/r8-optimization-devirtualization/
 */
class DynamicProxyAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.DYNAMIC_PROXY_STRIPPED

    /** Matches `Proxy.newProxyInstance(...)` calls. */
    private val proxyNewInstancePattern = Regex(
        """Proxy\.newProxyInstance\s*\("""
    )

    /**
     * Matches interface Class reference in proxy array: `arrayOf(SomeInterface::class.java)`.
     * Captures the interface class name (group 1).
     */
    private val proxyInterfacePattern = Regex(
        """arrayOf\s*\(\s*([A-Z][a-zA-Z0-9_]*)::class\.java"""
    )

    /** Matches `interface InterfaceName` declarations. */
    private val interfaceDeclarationPattern = Regex(
        """^\s*(?:internal\s+|private\s+|public\s+)?(?:fun\s+)?interface\s+([A-Z][a-zA-Z0-9]*)"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        // First pass: collect all interface declarations
        val interfaces = mutableMapOf<String, Pair<SourceFile, Int>>() // simpleName → (file, line)
        for (source in sourceFiles) {
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                val match = interfaceDeclarationPattern.find(line) ?: return@forEachIndexed
                interfaces[match.groupValues[1]] = source to (index + 1)
            }
        }

        // Second pass: find Proxy.newProxyInstance() calls and extract interface references
        for (source in sourceFiles) {
            val fullContent = source.lines.joinToString("\n")
            if (!proxyNewInstancePattern.containsMatchIn(fullContent)) continue

            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                if (!proxyNewInstancePattern.containsMatchIn(line) &&
                    !proxyInterfacePattern.containsMatchIn(line)) return@forEachIndexed

                // Extract interface names from the proxy array
                proxyInterfacePattern.findAll(line).forEach { match ->
                    val simpleName = match.groupValues[1]
                    val (declFile, declLine) = interfaces[simpleName] ?: return@forEach
                    val fqn = "${declFile.packageName}.$simpleName"

                    if (!parser.isClassKept(fqn, keepRules)) {
                        issues.add(
                            ProguardIssue(
                                type = IssueType.DYNAMIC_PROXY_STRIPPED,
                                severity = Severity.WARNING,
                                filePath = declFile.file.path,
                                lineNumber = declLine,
                                message = "Interface '$fqn' is used with Proxy.newProxyInstance() but has no keep rule. " +
                                    "R8 will rename interface methods → InvocationHandler dispatch fails with " +
                                    "IllegalArgumentException or AbstractMethodError at runtime.",
                                suggestion = "-keep interface $fqn { *; }",
                                className = fqn,
                                memberName = null
                            )
                        )
                    }
                }
            }
        }
        return issues
    }
}
