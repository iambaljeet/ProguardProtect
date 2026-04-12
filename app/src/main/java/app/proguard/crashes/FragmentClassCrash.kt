package app.proguard.crashes

import android.content.Context
import androidx.fragment.app.FragmentActivity
import app.proguard.models.ProfileFragment

/**
 * Crash Type 25: FRAGMENT_CLASS_RENAMED
 *
 * Fragment subclasses need a public no-arg constructor AND must not be renamed by R8.
 * The Android FragmentManager serializes the Fragment's class name (FQCN) into the
 * Activity's saved instance state (Parcel/Bundle). When the activity is destroyed and
 * recreated (configuration change, process death + task restore), the FragmentManager
 * calls `FragmentFactory.instantiate(classLoader, className)` with the ORIGINAL class name.
 *
 * If R8 renamed `ProfileFragment` → `x.y.z`, the FragmentManager cannot find the class
 * by its original name → ClassNotFoundException or IllegalStateException at runtime.
 *
 * This affects:
 * - Fragment back stack entries (restored after process death)
 * - Fragments added via <fragment> tag in layout XML (class attribute is a string)
 * - Fragments added programmatically and restored after config changes
 * - Dialog fragments, bottom sheets, and any other Fragment subclasses
 *
 * The crash happens silently — the app starts fine in debug mode (no R8) but crashes
 * in production when the user rotates the screen or the OS kills the process.
 *
 * References:
 * - https://issuetracker.google.com/issues/37149694
 * - https://stackoverflow.com/questions/15072810/proguard-issue-with-fragments
 * - https://developer.android.com/guide/fragments/saving-state
 * - https://github.com/square/leakcanary/issues/459 (R8 renaming Fragment classes)
 * - https://stackoverflow.com/questions/37623946/fragment-class-not-found-after-proguard-obfuscation
 *
 * Detection: Find Fragment subclasses; post-build confirm they were renamed in mapping.txt.
 * Fix: -keep class * extends androidx.fragment.app.Fragment { <init>(); }
 *      OR: -keep class app.proguard.models.ProfileFragment { *; }
 */
object FragmentClassCrash {

    /**
     * Simulates FragmentManager's internal Fragment instantiation.
     * The AndroidX FragmentManager calls exactly this when restoring fragment state.
     *
     * After R8 renames [ProfileFragment] to a short class name like 'a.b', looking up
     * the original name "app.proguard.models.ProfileFragment" fails with ClassNotFoundException.
     */
    fun triggerCrash(context: Context): String {
        val fragmentClassName = ProfileFragment::class.java.name
        // ^ This is the R8-renamed name at runtime (e.g., "a.b")

        // Simulate what FragmentManager does when restoring from saved state:
        // The saved state has the ORIGINAL class name "app.proguard.models.ProfileFragment"
        // but after R8, the class is renamed. Here we show the principle by loading
        // the class directly, then demonstrating what a mismatch looks like.
        return try {
            val clazz = context.classLoader.loadClass(fragmentClassName)
            val fragment = clazz.getDeclaredConstructor().newInstance() as ProfileFragment
            val loaded = fragment.javaClass.name
            if (loaded != "app.proguard.models.ProfileFragment") {
                // Class was renamed by R8!
                throw ClassNotFoundException(
                    "Fragment class 'app.proguard.models.ProfileFragment' was renamed to '$loaded' by R8. " +
                    "FragmentManager would fail to restore this Fragment after process death " +
                    "because it tries to instantiate the class by its ORIGINAL name."
                )
            }
            "ProfileFragment instantiated successfully: $loaded"
        } catch (e: ClassNotFoundException) {
            throw e
        }
    }

    /**
     * Version that demonstrates the crash in a [FragmentActivity] context.
     * In a real scenario, this crash happens automatically when the system
     * restores the Fragment back stack after process death.
     */
    fun trigger(context: Context): String {
        return try {
            triggerCrash(context)
        } catch (e: ClassNotFoundException) {
            "Fragment CRASH: ${e.message}"
        } catch (e: Exception) {
            "Fragment: ${e.javaClass.simpleName} — ${e.message}"
        }
    }
}
