package lib.proguardprotect.utils

import java.io.File

/**
 * Represents a parsed ProGuard/R8 keep rule.
 *
 * @property directive The keep directive (e.g., "-keep", "-keepclassmembers", "-keepnames")
 * @property classPattern The class pattern (e.g., "com.example.MyClass" or "com.example.**")
 * @property memberPatterns List of member patterns inside the braces (e.g., ["public *;", "<init>(...);"])
 * @property extendsPattern Class pattern from `extends` clause, if present
 * @property implementsPattern Class pattern from `implements` clause, if present
 * @property rawText The original rule text for reference
 */
data class KeepRule(
    val directive: String, // -keep, -keepclassmembers, -keepclasseswithmembers, etc.
    val classPattern: String, // e.g., "com.example.MyClass" or "com.example.**"
    val memberPatterns: List<String> = emptyList(), // e.g., ["public *;", "<init>(...);" ]
    val extendsPattern: String? = null, // e.g., "android.app.Activity"
    val implementsPattern: String? = null,
    val rawText: String
)

/**
 * Parses ProGuard/R8 configuration files and provides methods to check
 * whether specific classes, methods, or fields are protected by keep rules.
 *
 * Used by source-level analyzers to determine if a dynamic access pattern
 * is protected. In post-build mode, analyzers use empty rule lists so that
 * ALL patterns are reported for cross-referencing with mapping.txt.
 */
class ProguardRulesParser {

    fun parse(file: File): List<KeepRule> {
        if (!file.exists()) return emptyList()
        return parse(file.readText())
    }

    fun parse(content: String): List<KeepRule> {
        val rules = mutableListOf<KeepRule>()
        // Remove comments
        val cleaned = content.lines()
            .map { it.replace(Regex("#.*$"), "").trim() }
            .joinToString(" ")

        // Match -keep variants with their class specs
        val keepPattern = Regex(
            """(-keep\w*)\s+(?:,\s*\w+\s+)*class\s+([\w.*?]+)(?:\s+extends\s+([\w.*?]+))?(?:\s+implements\s+([\w.*?]+))?\s*(?:\{([^}]*)})?"""
        )

        keepPattern.findAll(cleaned).forEach { match ->
            val directive = match.groupValues[1]
            val classPattern = match.groupValues[2]
            val extendsPattern = match.groupValues[3].takeIf { it.isNotEmpty() }
            val implementsPattern = match.groupValues[4].takeIf { it.isNotEmpty() }
            val membersBlock = match.groupValues[5].trim()
            val memberPatterns = if (membersBlock.isNotEmpty()) {
                membersBlock.split(";")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            } else emptyList()

            rules.add(
                KeepRule(
                    directive = directive,
                    classPattern = classPattern,
                    memberPatterns = memberPatterns,
                    extendsPattern = extendsPattern,
                    implementsPattern = implementsPattern,
                    rawText = match.value
                )
            )
        }

        return rules
    }

    /**
     * Checks if a rule's class pattern matches the given class name,
     * considering extends/implements qualifiers.
     * When a rule has extends/implements constraints with a wildcard class pattern,
     * we can't verify the constraint at source level, so we skip the match.
     */
    private fun ruleMatchesClass(className: String, rule: KeepRule): Boolean {
        if (!matchesClassPattern(className, rule.classPattern)) return false
        // If the rule has extends/implements but uses a wildcard class pattern,
        // we can't verify the constraint → don't consider it a match
        if ((rule.extendsPattern != null || rule.implementsPattern != null) &&
            (rule.classPattern == "*" || rule.classPattern == "**")) {
            return false
        }
        return true
    }

    fun isClassKept(className: String, rules: List<KeepRule>): Boolean {
        return rules.any { rule ->
            (rule.directive == "-keep" || rule.directive == "-keepnames") &&
                ruleMatchesClass(className, rule)
        }
    }

    fun isClassMembersKept(className: String, rules: List<KeepRule>): Boolean {
        return rules.any { rule ->
            ruleMatchesClass(className, rule) &&
                (rule.directive == "-keepclassmembers" || rule.directive == "-keep" ||
                    rule.directive == "-keepclasseswithmembers") &&
                (rule.memberPatterns.isEmpty() || rule.memberPatterns.any { it.trim() == "*" })
        }
    }

    fun isMethodKept(className: String, methodName: String, rules: List<KeepRule>): Boolean {
        return rules.any { rule ->
            ruleMatchesClass(className, rule) &&
                rule.memberPatterns.any { member ->
                    val trimmed = member.trim()
                    // Wildcard "*" alone keeps all members
                    (trimmed == "*") ||
                    // Specific method name match
                    trimmed.contains(methodName) ||
                    // Unqualified <methods> keeps all methods (but not if preceded by "native" or annotations)
                    (trimmed.contains("<methods>") && !trimmed.contains("native") && !trimmed.contains("@"))
                }
        }
    }

    fun isFieldKept(className: String, rules: List<KeepRule>): Boolean {
        return rules.any { rule ->
            ruleMatchesClass(className, rule) &&
                (rule.memberPatterns.isEmpty() ||
                    rule.memberPatterns.any { member ->
                        member.trim() == "*" || member.contains("<fields>") && !member.contains("@")
                    })
        }
    }

    fun isEnumKept(className: String, rules: List<KeepRule>): Boolean {
        return rules.any { rule ->
            ruleMatchesClass(className, rule) &&
                rule.memberPatterns.any { member ->
                    member.contains("values()") || member.contains("valueOf") || member.trim() == "*"
                }
        }
    }

    private fun matchesClassPattern(className: String, pattern: String): Boolean {
        if (pattern == className) return true
        if (pattern == "**" || pattern == "*") return true

        val regex = pattern
            .replace(".", "\\.")
            .replace("**", "DOUBLE_WILDCARD")
            .replace("*", "[^.]*")
            .replace("DOUBLE_WILDCARD", ".*")

        return Regex("^$regex$").matches(className)
    }
}
