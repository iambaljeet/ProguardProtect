package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects custom `ViewModelProvider.Factory` implementations whose no-arg constructor
 * may be stripped by R8.
 *
 * `ViewModelProvider` instantiates custom factories via reflection using the no-arg constructor.
 * When R8 strips the constructor (it's not called directly from Kotlin — only via reflection),
 * `ViewModelProvider` throws `InstantiationException` or `IllegalAccessException` at runtime.
 *
 * Detection strategy:
 * 1. Find classes implementing `ViewModelProvider.Factory` or extending `ViewModelProvider.NewInstanceFactory`
 * 2. Extract the class FQCN (including nested class notation for inner Factory classes)
 * 3. Post-build: confirmed if the class was renamed in mapping.txt
 *
 * Ref: https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories
 */
class ViewModelFactoryAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.VIEWMODEL_FACTORY_CONSTRUCTOR_STRIPPED

    // Match class implementing ViewModelProvider.Factory
    private val factoryImplPattern = Regex(
        """class\s+([A-Z][a-zA-Z0-9_]*)\s*(?::[^{]*)?(?:ViewModelProvider\.Factory|ViewModelProvider\.NewInstanceFactory)"""
    )

    // Match inner/nested Factory class inside a ViewModel class
    private val innerFactoryPattern = Regex(
        """class\s+([A-Z][a-zA-Z0-9_]*)"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        for (source in sourceFiles) {
            val pkg = source.packageName
            val lines = source.lines

            // Track outer class name for nested Factory detection
            var outerClassName: String? = null

            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                // Track outer ViewModel class
                val viewModelPattern = Regex("""^(?:open\s+|abstract\s+|data\s+)?class\s+([A-Z][a-zA-Z0-9]+)\s*(?::|[{(])""")
                viewModelPattern.find(trimmed)?.let {
                    // Only update outer class if we're at class level (not inner)
                    if (!trimmed.contains("Factory") && !trimmed.contains("ViewModelProvider")) {
                        outerClassName = it.groupValues[1]
                    }
                }

                // Check for ViewModelProvider.Factory implementation
                factoryImplPattern.find(line)?.let { match ->
                    val factoryClassName = match.groupValues[1]
                    // Build FQCN — could be inner class (OuterClass$Factory) or top-level
                    val fqcn = if (outerClassName != null && factoryClassName != outerClassName) {
                        "$pkg.$outerClassName\$$factoryClassName"
                    } else {
                        "$pkg.$factoryClassName"
                    }

                    if (!parser.isClassKept(fqcn, keepRules)) {
                        issues.add(
                            ProguardIssue(
                                type = issueType,
                                severity = Severity.ERROR,
                                filePath = source.file.path,
                                lineNumber = index + 1,
                                message = "ViewModelProvider.Factory implementation '$fqcn' may have its no-arg constructor " +
                                    "stripped by R8. ViewModelProvider instantiates the factory via reflection " +
                                    "using the no-arg constructor. If R8 removes it, " +
                                    "InstantiationException or IllegalAccessException is thrown at runtime.",
                                suggestion = "Add to proguard-rules.pro:\n" +
                                    "-keep class $fqcn {\n    public <init>();\n}",
                                className = fqcn
                            )
                        )
                    }
                }
            }
        }

        return issues
    }
}
