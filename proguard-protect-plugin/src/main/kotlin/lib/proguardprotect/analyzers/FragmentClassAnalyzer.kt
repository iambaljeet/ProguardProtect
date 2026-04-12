package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.ProguardRulesParser
import lib.proguardprotect.utils.SourceFile

/**
 * Detects Fragment subclasses that may be renamed by R8, breaking the Android
 * Fragment back stack restoration mechanism.
 *
 * **The Problem**: The Android `FragmentManager` persists Fragment class names
 * (fully-qualified class names, FQCNs) in the Activity's saved instance state
 * bundle. When the Activity is recreated (screen rotation, process death + restore),
 * `FragmentManager` calls `FragmentFactory.instantiate(classLoader, className)` with
 * the **original** class name that was saved before obfuscation.
 *
 * If R8 has renamed the Fragment class (e.g., `ProfileFragment` → `a.b.f2`), the
 * framework cannot find the class by its original name → crash with:
 *   `ClassNotFoundException: app.myapp.ProfileFragment`
 * or in newer AndroidX versions:
 *   `IllegalStateException: Fragment ProfileFragment is unresolved`
 *
 * **This affects ALL Fragment subclasses** added to a back stack or used in
 * `<fragment>` layout tags (where the class name is a literal string in XML).
 *
 * **When it happens**:
 * - User rotates device while on a screen with a Fragment → activity recreated → crash
 * - OS kills app process (low memory) → user returns to app → back stack restored → crash
 * - App navigation uses `<fragment>` tag in layout XML with class attribute
 *
 * The crash is SILENT in debug builds (no R8) and only appears in production/release.
 *
 * **References**:
 * - https://issuetracker.google.com/issues/37149694
 * - https://stackoverflow.com/questions/15072810/proguard-issue-with-fragments
 * - https://stackoverflow.com/questions/37623946/fragment-class-not-found-after-proguard-obfuscation
 * - https://developer.android.com/guide/fragments/saving-state
 * - https://github.com/square/leakcanary/issues/459
 *
 * **Fix**: `-keep class * extends androidx.fragment.app.Fragment { <init>(); }`
 * or keep individual Fragment classes that are part of the back stack.
 */
class FragmentClassAnalyzer : BaseAnalyzer() {
    override val issueType = IssueType.FRAGMENT_CLASS_RENAMED

    /**
     * Detects Fragment subclass declarations: `class X : Fragment()` or
     * `class X : DialogFragment()` or `class X : BottomSheetDialogFragment()` etc.
     */
    private val fragmentSubclassPattern = Regex(
        """class\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*(?:[A-Za-z]+\.)*(?:Fragment|DialogFragment|BottomSheetDialogFragment|ListFragment|PreferenceFragment)\s*(?:\([^)]*\))?"""
    )

    /**
     * Matches `FragmentFactory.instantiate(...)` or `Fragment.instantiate(...)` calls
     * with literal class name strings — direct evidence of string-based Fragment loading.
     */
    private val fragmentInstantiatePattern = Regex(
        """(?:Fragment(?:Factory)?\.instantiate|loadClass)\s*\([^,)]+,\s*["']([a-zA-Z][a-zA-Z0-9_.]+Fragment[a-zA-Z0-9_]*)["']"""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()
        val parser = ProguardRulesParser()
        val seen = mutableSetOf<String>()

        for (source in sourceFiles) {
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed

                // Check for Fragment subclass declarations
                val classMatch = fragmentSubclassPattern.find(line)
                if (classMatch != null) {
                    val fragmentSimpleName = classMatch.groupValues[1]
                    // Build FQN from package + class name
                    val fqcn = if (source.packageName.isNotEmpty()) "${source.packageName}.$fragmentSimpleName"
                                else fragmentSimpleName
                    val key = "class:$fqcn"
                    if (seen.add(key)) {
                        // Check if there's a keep rule covering this Fragment class
                        val isKeptByWildcard = keepRules.any { rule ->
                            rule.rawText.contains("extends") &&
                            (rule.rawText.contains("Fragment") || rule.rawText.contains(fqcn))
                        }
                        if (!isKeptByWildcard) {
                            issues.add(
                                ProguardIssue(
                                    type = IssueType.FRAGMENT_CLASS_RENAMED,
                                    severity = Severity.WARNING,
                                    filePath = source.file.path,
                                    lineNumber = index + 1,
                                    message = "Fragment subclass '$fragmentSimpleName' (FQN: '$fqcn') lacks keep rules. " +
                                        "R8 will rename this class. The Android FragmentManager stores Fragment class names " +
                                        "in saved instance state. After obfuscation, back stack restoration fails " +
                                        "with ClassNotFoundException.",
                                    suggestion = "-keep class $fqcn { *; } " +
                                        "OR -keep class * extends androidx.fragment.app.Fragment { <init>(); }",
                                    className = fqcn,
                                    memberName = null
                                )
                            )
                        }
                    }
                    return@forEachIndexed
                }

                // Check for explicit Fragment.instantiate() calls with string class names
                val instantiateMatch = fragmentInstantiatePattern.find(line)
                if (instantiateMatch != null) {
                    val fragmentFqcn = instantiateMatch.groupValues[1]
                    val key = "instantiate:$fragmentFqcn"
                    if (seen.add(key)) {
                        if (!parser.isClassKept(fragmentFqcn, keepRules)) {
                            issues.add(
                                ProguardIssue(
                                    type = IssueType.FRAGMENT_CLASS_RENAMED,
                                    severity = Severity.WARNING,
                                    filePath = source.file.path,
                                    lineNumber = index + 1,
                                    message = "Fragment.instantiate() called with literal class name '$fragmentFqcn'. " +
                                        "R8 renames Fragment classes → ClassNotFoundException when recreating Fragment " +
                                        "from back stack or saved instance state.",
                                    suggestion = "-keep class $fragmentFqcn { *; }",
                                    className = fragmentFqcn,
                                    memberName = null
                                )
                            )
                        }
                    }
                }
            }
        }
        return issues
    }
}
