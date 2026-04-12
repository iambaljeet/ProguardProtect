package app.proguard.crashes

import android.content.Context
import android.content.res.Resources
import app.proguard.models.FeatureTheme

/**
 * Crash Type 19: RESOURCE_SHRUNK_BY_NAME
 *
 * When `shrinkResources = true` is combined with `tools:shrinkMode="strict"` in keep.xml,
 * aapt2 only keeps resources that are directly referenced via R.type.name in code.
 * Resources accessed ONLY via Context.getIdentifier() string lookup are REMOVED.
 * The resource `promo_banner` drawable exists in source but is not referenced via
 * R.drawable.promo_banner — only accessed by the string "promo_banner" at runtime.
 *
 * Result: getIdentifier("promo_banner", "drawable", pkg) returns 0 → loadResource fails
 *   with android.content.res.Resources$NotFoundException at runtime.
 *
 * Note: Without strict mode, aapt2 uses "safe" shrinking that keeps resources
 * referenced by literal strings in getIdentifier(). Strict mode removes ALL
 * dynamically accessed resources not explicitly listed in keep.xml.
 *
 * Reference: https://developer.android.com/build/shrink-code#keep-resources
 * Reference: https://medium.com/androiddevelopers/smallerapk-part-4-multi-apk-through-abi-and-density-splits-477083989006
 * Reference: https://stackoverflow.com/questions/38069175/getidentifier-returns-0-after-minification
 *
 * Detection: Find getIdentifier(literal, ...) → verify resource exists in APK.
 * Fix: Add <item name="promo_banner" type="drawable" tools:keep="@drawable/promo_banner"/>
 *      in res/raw/keep.xml (see the commented-out entry there).
 */
object ResourceNameCrash {

    /**
     * Attempts to load a drawable by name using getIdentifier().
     * With shrinkResources + strict mode, promo_banner is removed from the APK.
     * getIdentifier() returns 0 → getDrawable(0) throws Resources$NotFoundException.
     */
    fun triggerCrash(context: Context): String {
        // Using a literal string on one line so the plugin's static analysis can detect this pattern.
        // promo_banner is referenced ONLY by string literal — aapt2 strict mode removes it from APK.
        @Suppress("UNUSED_VARIABLE")
        val theme = FeatureTheme(name = "promo")
        val resourceId = context.resources.getIdentifier("promo_banner", "drawable", context.packageName)
        if (resourceId == 0) {
            throw Resources.NotFoundException(
                "Resource 'promo_banner' not found — aapt2 strict mode removed it from the APK. " +
                "Add tools:keep=\"@drawable/promo_banner\" in res/raw/keep.xml to fix."
            )
        }
        // If resource exists, load it (would succeed if keep rule is present)
        @Suppress("DEPRECATION")
        context.resources.getDrawable(resourceId)
        return "promo_banner drawable loaded successfully (resource was kept)"
    }

    fun trigger(context: Context): String {
        return try {
            triggerCrash(context)
        } catch (e: Resources.NotFoundException) {
            "ResourceName CRASH: ${e.message}"
        } catch (e: Exception) {
            "ResourceName: ${e.javaClass.simpleName} — ${e.message}"
        }
    }
}
