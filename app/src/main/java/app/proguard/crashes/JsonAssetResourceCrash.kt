package app.proguard.crashes

import android.content.Context
import android.content.res.Resources
import org.json.JSONObject

/**
 * Crash Type 24: JSON_ASSET_RESOURCE_STRIPPED
 *
 * A common real-world pattern in dynamic UI apps: instead of hardcoding drawable references
 * in code, the app reads a JSON config file from assets (e.g., from a CMS or remote config
 * stored locally) and uses the resource names to load drawables at runtime via getIdentifier().
 *
 * The problem: aapt2's resource shrinker only sees code-level references (R.drawable.xxx).
 * Strings inside JSON asset files are INVISIBLE to aapt2. With `shrinkResources = true` and
 * strict mode enabled (`tools:shrinkMode="strict"` in keep.xml), aapt2 removes ALL resources
 * that are not directly referenced via R.type.name in compiled code — including resources
 * whose names appear only in JSON files.
 *
 * Result: getIdentifier("feature_banner", "drawable", pkg) returns 0 → Resources$NotFoundException
 *
 * This is a critical pattern in apps using:
 * - Remote config / feature flags stored as JSON in assets
 * - CMS-driven UI where resource names come from content JSON
 * - A/B testing frameworks that configure UI via JSON config files
 *
 * References:
 * - https://developer.android.com/build/shrink-code#keep-resources
 * - https://issuetracker.google.com/issues/37124708 (aapt2 strict mode resource shrinking)
 * - https://stackoverflow.com/questions/69862538/resources-removed-by-r8-when-loaded-from-json-config
 * - https://medium.com/androiddevelopers/smallerapk-part-3-removing-unused-resources-1511f9e3f761
 *
 * Detection: Scan assets/[name].json files for string values matching Android resource name patterns,
 *            cross-reference with resources.txt from the final build artifact.
 * Fix: Add tools:keep for all resources referenced from JSON in res/raw/keep.xml:
 *      tools:keep="@drawable/feature_banner,@drawable/promo_overlay"
 */
object JsonAssetResourceCrash {

    /**
     * Reads drawable resource names from [ui_config.json] in assets and attempts to load each.
     * With shrinkResources + strict mode, the resources are removed from the APK because
     * aapt2 cannot detect they are referenced inside the JSON file.
     */
    fun triggerCrash(context: Context): String {
        val configJson = context.assets.open("ui_config.json").bufferedReader().use { it.readText() }
        val config = JSONObject(configJson)

        // Collect all resource names from the JSON config (simulating a real CMS-driven UI loader)
        val resourceNames = mutableListOf<String>()
        collectStringValues(config, resourceNames)

        val sb = StringBuilder()
        var crashOccurred = false

        for (resourceName in resourceNames) {
            // Resource names are lowercase strings with underscores — typical drawable names
            if (!resourceName.matches(Regex("[a-z][a-z0-9_]*"))) continue

            val resId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
            if (resId == 0) {
                sb.appendLine("MISSING: '$resourceName' — stripped by aapt2 strict shrinking")
                crashOccurred = true
            } else {
                sb.appendLine("OK: '$resourceName' found (resId=$resId)")
            }
        }

        if (crashOccurred) {
            throw Resources.NotFoundException(
                "JSON asset resource(s) removed by aapt2 strict shrinking:\n$sb\n" +
                "Resources referenced only in JSON files are invisible to aapt2 and removed " +
                "when shrinkResources=true with strict mode. Add tools:keep in res/raw/keep.xml."
            )
        }

        return "All JSON-referenced resources found: ${resourceNames.filter { it.matches(Regex("[a-z][a-z0-9_]*")) }}"
    }

    /** Recursively collects all string leaf values from a JSONObject. */
    private fun collectStringValues(json: JSONObject, result: MutableList<String>) {
        for (key in json.keys()) {
            when (val value = json.get(key)) {
                is String -> result.add(value)
                is JSONObject -> collectStringValues(value, result)
            }
        }
    }

    fun trigger(context: Context): String {
        return try {
            triggerCrash(context)
        } catch (e: Resources.NotFoundException) {
            "JsonAssetResource CRASH: ${e.message?.lines()?.firstOrNull()}"
        } catch (e: Exception) {
            "JsonAssetResource: ${e.javaClass.simpleName} — ${e.message}"
        }
    }
}
