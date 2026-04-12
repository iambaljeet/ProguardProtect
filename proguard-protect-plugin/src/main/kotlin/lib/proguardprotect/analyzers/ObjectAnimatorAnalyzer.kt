package lib.proguardprotect.analyzers

import lib.proguardprotect.models.ProguardIssue
import lib.proguardprotect.models.ProguardIssue.IssueType
import lib.proguardprotect.models.ProguardIssue.Severity
import lib.proguardprotect.utils.KeepRule
import lib.proguardprotect.utils.SourceFile

/**
 * Detects [android.animation.ObjectAnimator] property names whose corresponding setters
 * will be renamed by R8, causing [NoSuchMethodException] at runtime.
 *
 * ## Root Cause
 * `ObjectAnimator.ofFloat(target, "propertyName", ...)` resolves the setter by reflection
 * at runtime: `"propertyName"` → `setPropertyName(float)`. R8 renames setters that have
 * no direct call sites in the code — they're only invoked via ObjectAnimator reflection.
 *
 * ## Detection Strategy
 * 1. Find `ObjectAnimator.of*(target, "propName", ...)` calls in source
 * 2. Derive the setter name: `"set" + propName.capitalize()`
 * 3. Find a View subclass in the project with that setter method
 * 4. Report the issue with the class and member name for mapping confirmation
 *
 * ## Post-Build Confirmation (in [lib.proguardprotect.PostBuildAnalysisTask])
 * The setter method name is checked against mapping.txt. If R8 renamed it
 * (member entry shows `setPropertyName -> a`), the issue is confirmed.
 *
 * References:
 * - https://developer.android.com/guide/topics/graphics/prop-animation#object-animator
 * - https://developer.android.com/topic/performance/app-optimization/troubleshoot-the-optimization
 * - https://stackoverflow.com/questions/16496575/proguard-objectanimator
 */
class ObjectAnimatorAnalyzer : BaseAnalyzer() {

    override val issueType = IssueType.OBJECT_ANIMATOR_PROPERTY_RENAMED

    /** Matches ObjectAnimator.ofFloat/ofInt/ofArgb/ofObject with a string as the 2nd arg. */
    private val animatorCallPattern = Regex(
        """ObjectAnimator\s*\.\s*of(?:Float|Int|Argb|Object)\s*\(\s*(\w+)\s*,\s*"(\w+)""""
    )

    /**
     * Matches class declarations extending any View-related class.
     * Handles both classes without constructors (`class MyView : FrameLayout`) and
     * classes with constructor parameters (`class MyView(ctx: Context) : FrameLayout(ctx)`).
     */
    private val viewInheritPattern = Regex(
        """(?:class|object)\s+(\w+)(?:\([^)]*\))*\s*(?::[^{]*?|\s+:\s*[^{]*?)(?:View|ViewGroup|FrameLayout|LinearLayout|RelativeLayout|ConstraintLayout|RecyclerView|TextView|ImageView|EditText|Button|CheckBox|CardView)\b"""
    )

    /** Matches a method declaration for a setter (e.g., `fun setMeterLevel(`). */
    private fun setterMethodPattern(setterName: String) = Regex(
        """fun\s+${Regex.escape(setterName)}\s*\("""
    )

    override fun analyze(sourceFiles: List<SourceFile>, keepRules: List<KeepRule>): List<ProguardIssue> {
        val issues = mutableListOf<ProguardIssue>()

        // Build index: setter name → list of (FQN, file) for all View subclasses
        val viewSetterIndex = mutableMapOf<String, MutableList<Pair<String, SourceFile>>>()
        for (source in sourceFiles) {
            if (source.file.extension == "xml") continue
            val content = source.lines.joinToString("\n")
            val classMatch = viewInheritPattern.find(content) ?: continue
            val className = classMatch.groupValues[1]
            val fqn = if (source.packageName.isNotEmpty()) "${source.packageName}.$className" else className
            // Index all setters in this file
            source.lines.forEach { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("//") && !trimmed.startsWith("*")) {
                    val setterMatch = Regex("""fun\s+(set[A-Z]\w+)\s*\(""").find(trimmed)
                    if (setterMatch != null) {
                        val setterName = setterMatch.groupValues[1]
                        viewSetterIndex.getOrPut(setterName) { mutableListOf() }.add(Pair(fqn, source))
                    }
                }
            }
        }

        // Now scan for ObjectAnimator calls
        for (source in sourceFiles) {
            if (source.file.extension == "xml") continue
            source.lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed

                val match = animatorCallPattern.find(line) ?: return@forEachIndexed
                val propertyName = match.groupValues[2]
                if (propertyName.isBlank()) return@forEachIndexed

                // Derive setter name: "meterLevel" → "setMeterLevel"
                val setterName = "set" + propertyName.replaceFirstChar { it.uppercase() }

                // Find View classes with this setter
                val candidates = viewSetterIndex[setterName] ?: return@forEachIndexed
                if (candidates.isEmpty()) return@forEachIndexed

                // If exactly one candidate: confident match (ERROR level)
                // If multiple: still report (WARNING) — post-build will narrow down
                val confidence = if (candidates.size == 1) Severity.ERROR else Severity.WARNING
                val (targetFqn, _) = candidates.first()

                val suggestion = if (candidates.size == 1) {
                    "-keepclassmembers class $targetFqn {\n" +
                    "    public void $setterName(...);\n" +
                    "    public * get${propertyName.replaceFirstChar { it.uppercase() }}(...);\n}"
                } else {
                    "-keepclassmembers class <YourViewClass> {\n" +
                    "    public void $setterName(...);\n}"
                }

                issues.add(ProguardIssue(
                    type = IssueType.OBJECT_ANIMATOR_PROPERTY_RENAMED,
                    severity = confidence,
                    filePath = source.file.path,
                    lineNumber = index + 1,
                    message = "ObjectAnimator property '$propertyName' → setter '$setterName' resolved by " +
                        "reflection at runtime. R8 renames '$setterName' (no direct call sites) → " +
                        "NoSuchMethodException or silent animation failure. " +
                        "Target View class: ${candidates.joinToString { it.first }}",
                    suggestion = suggestion,
                    className = targetFqn,
                    memberName = setterName
                ))
            }
        }
        return issues
    }
}
