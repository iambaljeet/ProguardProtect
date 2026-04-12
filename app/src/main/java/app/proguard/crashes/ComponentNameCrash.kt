package app.proguard.crashes

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.proguard.models.DeepLinkTarget

/**
 * Crash Type 21: COMPONENT_CLASS_NOT_FOUND
 *
 * `ComponentName(pkg, "com.example.SomeActivity")` and
 * `Intent().setClassName(pkg, "fully.qualified.ClassName")` hardcode a class name as
 * a string. R8 renames Activity classes that lack proper keep rules. When Android's
 * ActivityManager tries to find the activity by its original class name at runtime,
 * it fails with ActivityNotFoundException.
 *
 * This is common in:
 * - Deep-link routing systems that store ActivityFQCN in a navigation graph config
 * - Plugin architectures that dynamically load activities by name
 * - Inter-app communication via explicit Intents with class name strings
 *
 * Reference: https://stackoverflow.com/questions/36829329/proguard-activitynotfoundexception
 * Reference: https://stackoverflow.com/questions/22680117/proguard-and-using-class-names-as-strings
 * Reference: https://issuetracker.google.com/issues/173432204
 *
 * Detection: Find ComponentName() or setClassName() with string literals → look up in mapping.
 * Fix: -keep class app.proguard.crashes.DeepLinkActivity { *; }
 *       OR -keep class * extends android.app.Activity { *; }
 */
object ComponentNameCrash {

    /**
     * The target activity class — referenced ONLY via string, no direct class ref.
     * R8 will rename this to a short obfuscated name since there is no direct reference.
     *
     * In a real app, this would be a proper Activity in the manifest, but here we
     * simulate the class being present in the same package yet renamed by R8.
     */
    private const val DEEP_LINK_ACTIVITY_CLASS = "app.proguard.crashes.DeepLinkActivity"

    /**
     * Creates a ComponentName referencing a renamed class.
     * Android framework stores and resolves activities by their original FQCN.
     * After R8 renames the class, resolution fails → ActivityNotFoundException.
     */
    fun triggerCrash(context: Context): String {
        // Using a literal string so the plugin can statically detect this pattern.
        val intent = Intent().apply {
            component = ComponentName(context.packageName, "app.proguard.crashes.DeepLinkActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // resolveActivity returns null if class was renamed by R8
        val resolvedActivity = context.packageManager.resolveActivity(intent, 0)
            ?: throw ClassNotFoundException(
                "Activity 'app.proguard.crashes.DeepLinkActivity' not found — R8 renamed it. " +
                "Add -keep class app.proguard.crashes.DeepLinkActivity to proguard-rules.pro"
            )
        return "Resolved activity: ${resolvedActivity.activityInfo.name}"
    }

    fun trigger(context: Context): String {
        return try {
            triggerCrash(context)
        } catch (e: ClassNotFoundException) {
            "ComponentName CRASH: ${e.message}"
        } catch (e: Exception) {
            "ComponentName: ${e.javaClass.simpleName} — ${e.message}"
        }
    }
}

/**
 * Stub Activity class to represent a deep-link target.
 * Only referenced via string "app.proguard.crashes.DeepLinkActivity" — never directly.
 * R8 will rename it since there are no keep rules and no direct class references.
 */
class DeepLinkActivity : android.app.Activity()
