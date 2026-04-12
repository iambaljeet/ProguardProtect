package app.proguard.crashes

import android.animation.ObjectAnimator
import app.proguard.models.AnimatedMeterView

/**
 * Crash Type 27: OBJECT_ANIMATOR_PROPERTY_RENAMED
 *
 * [ObjectAnimator] resolves property setters by reflection at runtime using the property
 * name string: `"meterLevel"` → `setMeterLevel(float)`. When R8 renames `setMeterLevel`
 * (it sees no direct Kotlin call sites), the runtime lookup fails:
 *
 * ```
 * android.animation.PropertyValuesHolder: Method setMeterLevel() with type float not found
 *   on target class class app.proguard.models.a8
 * java.lang.NoSuchMethodException: setMeterLevel [float]
 * ```
 *
 * The renamed class `a8` (or similar) still exists in the DEX but the method `setMeterLevel`
 * was renamed to `a` or similar — ObjectAnimator cannot find it.
 *
 * Reference: https://developer.android.com/guide/topics/graphics/prop-animation#object-animator
 * Reference: https://developer.android.com/topic/performance/app-optimization/troubleshoot-the-optimization
 * Reference: https://stackoverflow.com/questions/16496575/proguard-objectanimator
 *
 * Detection: Find ObjectAnimator.of*() calls with string property names → derive setter
 *   name → check if setter method was renamed in mapping.txt for the target View class.
 * Fix: -keepclassmembers class app.proguard.models.AnimatedMeterView {
 *     public void setMeterLevel(float);
 *     public float getMeterLevel();
 * }
 */
object ObjectAnimatorCrash {

    /**
     * Animates the meter view's `meterLevel` property from 0 to 100.
     *
     * The string `"meterLevel"` is the critical issue: ObjectAnimator uses it to look up
     * `setMeterLevel(float)` via reflection at runtime. After R8 renames `setMeterLevel`,
     * this lookup throws [NoSuchMethodException], wrapped in [RuntimeException].
     *
     * @param view The [AnimatedMeterView] instance to animate
     */
    fun animateMeterLevel(view: AnimatedMeterView): ObjectAnimator {
        // ObjectAnimator resolves "setMeterLevel" by reflection at runtime.
        // R8 renames setMeterLevel → NoSuchMethodException wrapped in RuntimeException.
        return ObjectAnimator.ofFloat(view, "meterLevel", 0f, 100f).apply {
            duration = 1000L
        }
    }

    fun trigger(): String {
        return "ObjectAnimator: property 'meterLevel' → setMeterLevel(float). " +
            "R8 renames setMeterLevel (no direct call sites) → " +
            "NoSuchMethodException at runtime."
    }
}
