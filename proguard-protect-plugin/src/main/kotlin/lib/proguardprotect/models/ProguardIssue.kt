package lib.proguardprotect.models

/**
 * Represents a single R8/ProGuard vulnerability detected by the plugin.
 *
 * Each issue corresponds to a code pattern that can cause a runtime crash
 * when R8 minification is applied without proper keep rules.
 *
 * @property type The category of the vulnerability
 * @property severity Error (will crash) or Warning (may cause issues)
 * @property filePath Absolute path to the source file containing the vulnerable pattern
 * @property lineNumber Line number in the source file
 * @property message Human-readable description of the issue and R8's actual behavior
 * @property suggestion Fix recommendation (typically a ProGuard keep rule)
 * @property className Fully-qualified class name of the affected class (for mapping lookup)
 * @property memberName Specific method or field name involved (for member-level mapping lookup)
 */
data class ProguardIssue(
    val type: IssueType,
    val severity: Severity,
    val filePath: String,
    val lineNumber: Int,
    val message: String,
    val suggestion: String,
    val className: String? = null,
    val memberName: String? = null
) {
    /**
     * Categories of R8/ProGuard runtime crash vulnerabilities.
     */
    enum class IssueType {
        /** Class.forName() on a class that R8 renamed or removed */
        REFLECTION_CLASS_FOR_NAME,
        /** getDeclaredMethod/getMethod/getDeclaredField/getField on renamed members */
        REFLECTION_METHOD_ACCESS,
        /** Gson/serialization using classes whose fields were renamed by R8 */
        SERIALIZATION_FIELD_RENAME,
        /** Enum.valueOf() or enumConstants on renamed enum classes */
        ENUM_VALUE_OF,
        /** Dynamic instantiation of interface implementations stripped by R8 */
        CALLBACK_INTERFACE_STRIPPED,
        /** R8 devirtualization routing interface call to base class private method */
        DEVIRTUALIZATION_ILLEGAL_ACCESS,
        /** Class not found in final DEX (removed entirely by R8) */
        NO_CLASS_DEF_FOUND,
        /** Gson TypeToken loses generic type info when Signature attribute is stripped */
        GSON_TYPE_TOKEN_STRIPPED,
        /** Kotlin companion object accessed via reflection is renamed by R8 */
        COMPANION_OBJECT_STRIPPED,
        /** Sealed class subtypes are removed from DEX by R8 */
        SEALED_SUBCLASS_STRIPPED,
        /** Custom @Retention(RUNTIME) annotation stripped by R8 */
        ANNOTATION_STRIPPED,
        /** Parcelable class renamed by R8, Bundle/Intent deserialization fails */
        PARCELABLE_CLASS_RENAMED,
        /** WebView @JavascriptInterface methods renamed by R8, JS bridge calls fail */
        JAVASCRIPT_INTERFACE_STRIPPED,
        /** Class containing JNI native methods renamed by R8 → UnsatisfiedLinkError */
        NATIVE_METHOD_RENAMED,
        /** WorkManager Worker subclass renamed/stripped by R8 → ClassNotFoundException */
        WORKMANAGER_WORKER_STRIPPED,
        /** Custom View class renamed by R8 → InflateException when inflating from XML */
        CUSTOM_VIEW_STRIPPED,
        /** Kotlin object INSTANCE field removed by R8 → NoSuchFieldError via reflection */
        KOTLIN_OBJECT_INSTANCE_REMOVED
    }

    /** Severity levels for detected issues. */
    enum class Severity {
        /** Will definitely cause a runtime crash */
        ERROR,
        /** May cause issues depending on execution path */
        WARNING
    }

    override fun toString(): String {
        val loc = "$filePath:$lineNumber"
        return "[$severity] $type at $loc\n  $message\n  Fix: $suggestion"
    }
}
