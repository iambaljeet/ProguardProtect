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
        KOTLIN_OBJECT_INSTANCE_REMOVED,
        /** Resource accessed only via getIdentifier() string is removed by aapt2 strict shrinking */
        RESOURCE_SHRUNK_BY_NAME,
        /** R8 strips Signature bytecode attribute → genericSuperclass cast to ParameterizedType fails */
        GENERIC_SIGNATURE_STRIPPED,
        /** Activity/Service class referenced by string in ComponentName/Intent renamed by R8 */
        COMPONENT_CLASS_NOT_FOUND,
        /** Data class copy()/componentN() methods removed by R8, accessed via Kotlin reflection */
        DATA_CLASS_MEMBER_STRIPPED,
        /** Interface used with Proxy.newProxyInstance() renamed/stripped by R8 */
        DYNAMIC_PROXY_STRIPPED,
        /** Resource name embedded in JSON asset file; aapt2 cannot see it → resource shrunk away */
        JSON_ASSET_RESOURCE_STRIPPED,
        /** Fragment subclass renamed by R8; FragmentManager back-stack restore fails */
        FRAGMENT_CLASS_RENAMED,
        /** Serializable class renamed by R8; cross-build ObjectInputStream deserialization fails */
        SERIALIZABLE_CLASS_RENAMED,
        /** ObjectAnimator property setter renamed by R8; string-based property lookup fails at runtime */
        OBJECT_ANIMATOR_PROPERTY_RENAMED,
        /** Class.forName() with nested class notation ("X$Y") — outer class renamed breaks path */
        INNER_CLASS_REFLECTION_RENAMED,
        /** android:onClick handler method renamed by R8; runtime reflection lookup fails */
        ANDROID_ONCLICK_METHOD_RENAMED,
        /** ClassLoader.loadClass() with string class name — same failure mode as Class.forName() */
        CLASSLOADER_LOADCLASS,
        /** @JvmOverloads generates synthetic overloads that R8 strips as unreachable */
        JVMOVERLOADS_OVERLOAD_STRIPPED,
        /** Custom ViewModelProvider.Factory loses its no-arg constructor when R8 strips it */
        VIEWMODEL_FACTORY_CONSTRUCTOR_STRIPPED,
        /** Custom exception class renamed by R8; string-based lookup fails with ClassNotFoundException */
        EXCEPTION_CLASS_RENAMED
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
