package app.proguard.crashes

import android.content.Context
import app.proguard.models.CustomProgressView

/**
 * Crash Type 17: CUSTOM_VIEW_STRIPPED
 *
 * Custom views are referenced in XML layouts by their fully qualified class name.
 * Android's LayoutInflater inflates them via Class.forName(className).newInstance(...).
 * When R8 renames CustomProgressView (e.g., to "c2"), LayoutInflater fails:
 *   android.view.InflateException: Binary XML file line #5: Error inflating class
 *   app.proguard.models.CustomProgressView
 *   Caused by: ClassNotFoundException: Didn't find class "app.proguard.models.CustomProgressView"
 *
 * Reference: https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html
 * Reference: https://krossovochkin.com/posts/2021_01_18_debugging_proguard_configuration_issues/
 *
 * Detection: Find classes extending View/ViewGroup/FrameLayout etc → check if renamed.
 * Fix: -keep class * extends android.view.View { public <init>(android.content.Context, android.util.AttributeSet); }
 */
object CustomViewCrash {
    fun inflateCustomView(context: Context): CustomProgressView {
        // In XML: <app.proguard.models.CustomProgressView ... />
        // LayoutInflater does Class.forName("app.proguard.models.CustomProgressView")
        // After R8 rename: ClassNotFoundException → InflateException
        return CustomProgressView(context)
    }

    fun trigger(): String {
        return "CustomView: CustomProgressView extends FrameLayout and has AttributeSet constructor. " +
            "R8 will rename it → LayoutInflater fails with InflateException: ClassNotFoundException."
    }
}
