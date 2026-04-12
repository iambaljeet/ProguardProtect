package app.proguard.models

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Custom view component for showing progress with custom styling.
 * Demonstrates R8 custom view stripping (Crash Type 17).
 * Reference: https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html
 * Reference: https://blog.logrocket.com/r8-code-shrinking-android-guide
 *
 * Custom views are referenced by their fully qualified class name in XML layouts.
 * Android's LayoutInflater uses Class.forName(className) to instantiate them.
 * When R8 renames CustomProgressView → InflateException: ClassNotFoundException.
 */
class CustomProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var progress: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    fun setProgressText(text: String) {
        // Update progress display
    }
}
