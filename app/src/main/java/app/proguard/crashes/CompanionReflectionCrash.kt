package app.proguard.crashes

import android.util.Log

/**
 * Crash Type 10: Kotlin Companion Object — R8 Renames Companion Class
 *
 * Reference: https://stackoverflow.com/q/57974556/r8-stripping-out-kotlin-companion-object
 * Reference: https://issuetracker.google.com/issues/353475583
 *
 * Kotlin companion objects compile to a nested class `EnclosingClass$Companion`.
 * When accessed via dynamic class loading or Kotlin reflection:
 *   - `EnclosingClass::class.companionObjectInstance` → returns null if companion stripped
 *   - `Class.forName("app.proguard.models.NetworkConfig\$Companion")` → ClassNotFoundException
 *
 * R8 renames NetworkConfig$Companion to an obfuscated name. Dynamic reflection
 * using the original name fails at runtime.
 *
 * Expected crash: ClassNotFoundException or NullPointerException
 */
object CompanionReflectionCrash {

    private const val TAG = "CompanionReflectionCrash"

    fun trigger(): String {
        // Build the companion class name dynamically so R8 cannot adapt the string
        val pkg = "app.proguard.models"
        val className = "NetworkConfig"
        val companionSuffix = "\$Companion"
        val companionClassName = "$pkg.$className$companionSuffix"

        Log.d(TAG, "Loading companion class: $companionClassName")

        // Load companion class by its original name — R8 renames it, causing ClassNotFoundException
        val companionClass = Class.forName(companionClassName)
        Log.d(TAG, "Companion class loaded: ${companionClass.simpleName}")

        // Access VERSION_CODE field via reflection
        val versionField = companionClass.getDeclaredField("VERSION_CODE")
        versionField.isAccessible = true

        // Get the INSTANCE of the companion object
        val instanceField = Class.forName("$pkg.$className").getDeclaredField("Companion")
        instanceField.isAccessible = true
        val companionInstance = instanceField.get(null)

        val version = versionField.get(companionInstance) as String
        return "✅ Companion: VERSION_CODE=$version, DEFAULT_TIMEOUT=${app.proguard.models.NetworkConfig.DEFAULT_TIMEOUT}"
    }
}
