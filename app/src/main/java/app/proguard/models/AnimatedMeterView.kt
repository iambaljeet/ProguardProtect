package app.proguard.models

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * A custom View that exposes a `meterLevel` property for animation via ObjectAnimator.
 *
 * [ObjectAnimator] resolves property setters at **runtime** by constructing the setter name
 * from the property string: `"meterLevel"` → `setMeterLevel(float)`. This resolution is done
 * via reflection — NOT via a direct Kotlin/Java call site. R8 sees no direct calls to
 * [setMeterLevel] and therefore renames it (e.g., to `a`), causing [NoSuchMethodException]
 * when ObjectAnimator tries to find `setMeterLevel` at runtime.
 *
 * Reference: https://developer.android.com/guide/topics/graphics/prop-animation#object-animator
 * Reference: https://developer.android.com/topic/performance/app-optimization/troubleshoot-the-optimization
 */
class AnimatedMeterView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    private var meterLevelInternal: Float = 0f

    /** Returns the current meter level (0.0–100.0). Read by ObjectAnimator getter resolution. */
    fun getMeterLevel(): Float = meterLevelInternal

    /**
     * Sets the meter level. Called by [android.animation.ObjectAnimator] via reflection using
     * the string property name `"meterLevel"` (resolved to `setMeterLevel` at runtime).
     *
     * R8 renames this method since it has no direct call sites in Kotlin code → NoSuchMethodException.
     * Fix: `-keepclassmembers class app.proguard.models.AnimatedMeterView { public void setMeterLevel(float); }`
     */
    fun setMeterLevel(value: Float) {
        meterLevelInternal = value.coerceIn(0f, 100f)
        invalidate()
    }
}
