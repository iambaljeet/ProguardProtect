package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects Kotlin object (singleton) declarations that will have their INSTANCE
 * field removed or class renamed by R8.
 *
 * Kotlin object declarations compile to:
 *   public final class ConfigManager {
 *       public static final ConfigManager INSTANCE = new ConfigManager();
 *   }
 * R8 may rename the class or remove INSTANCE if it thinks it's unreachable.
 * Reflection code (DI frameworks, objectInstance) then fails with NoSuchFieldError.
 *
 * References:
 * - https://discuss.kotlinlang.org/t/proguard-rules-for-kotlin-object/8444
 * - https://stackoverflow.com/questions/51271286/proguard-generating-notes-for-all-instance-fields-with-kotlin-reflect
 */
class KotlinObjectAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.KOTLIN_OBJECT_INSTANCE_REMOVED

    private val objectDeclPattern = Regex("""^\s*(?:internal\s+|private\s+|public\s+)?object\s+([A-Z][a-zA-Z0-9]*)""")
    // Only match objectInstance access and .INSTANCE field access — not generic Class.forName
    private val objectInstancePattern = Regex("""(\w+)::class\.objectInstance|(\w+)\.INSTANCE\b""")

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()

        // First pass: collect all Kotlin object declarations
        val objectClasses = mutableMapOf<String, Pair<SourceFile, Int>>() // simpleName → (file, line)
        for (source in sourceFiles) {
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                val match = objectDeclPattern.find(line) ?: return@forEachIndexed
                val simpleName = match.groupValues[1]
                objectClasses[simpleName] = source to (index + 1)
            }
        }

        // Second pass: find objects accessed via objectInstance or .INSTANCE
        val reflectedObjects = mutableSetOf<String>()
        for (source in sourceFiles) {
            val fullContent = source.lines.joinToString("\n")

            // Check for SomeName::class.objectInstance or SomeName.INSTANCE
            objectInstancePattern.findAll(fullContent).forEach { match ->
                val name = match.groupValues[1].takeIf { it.isNotEmpty() }
                    ?: match.groupValues[2]
                if (name in objectClasses) reflectedObjects.add(name)
            }

            // Class.forName("FQN").getField("INSTANCE") — extract the specific class name
            // Use a precise pattern to avoid flagging unrelated objects in the same file.
            val classForNameInstancePattern = Regex(
                """Class\.forName\s*\(\s*"([a-zA-Z0-9._${'$'}]+)"\s*\)(?:.*?)getField\s*\(\s*"INSTANCE"\s*\)""",
                RegexOption.DOT_MATCHES_ALL
            )
            classForNameInstancePattern.findAll(fullContent).forEach { instanceMatch ->
                val targetFqn = instanceMatch.groupValues[1]
                objectClasses.forEach { (name, pair) ->
                    val fqn = "${pair.first.packageName}.$name"
                    if (targetFqn == fqn || targetFqn.endsWith(".$name")) {
                        reflectedObjects.add(name)
                    }
                }
            }
        }

        for (simpleName in reflectedObjects) {
            val (source, line) = objectClasses[simpleName] ?: continue
            val fqn = "${source.packageName}.$simpleName"
            if (!parser.isClassKept(fqn, keepRules)) {
                issues.add(ProguardIssue(
                    type = IssueType.KOTLIN_OBJECT_INSTANCE_REMOVED,
                    severity = Severity.WARNING,
                    filePath = source.file.path,
                    lineNumber = line,
                    message = "Kotlin object '$fqn' is accessed via reflection (objectInstance / Class.forName / .INSTANCE). " +
                        "R8 may rename the class or remove the INSTANCE field → NoSuchFieldError at runtime.",
                    suggestion = "-keepclassmembers class $fqn { public static ** INSTANCE; }",
                    className = fqn,
                    memberName = "INSTANCE"
                ))
            }
        }
        return issues
    }
}
