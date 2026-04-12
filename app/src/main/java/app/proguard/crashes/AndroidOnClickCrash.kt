package app.proguard.crashes

import android.view.View

/**
 * Crash Type 29: ANDROID_ONCLICK_METHOD_RENAMED
 *
 * Android XML layouts support `android:onClick="methodName"` to bind a click event to
 * a method in the host Activity. Android resolves this at **runtime** by reflection:
 * ```java
 * method = context.getClass().getMethod(handlerName, View.class)
 * ```
 *
 * If [handlePaymentSubmit] is only referenced from the XML layout (never called directly
 * from Kotlin), R8 will rename it (e.g., to `a`). At runtime, the framework cannot find
 * `handlePaymentSubmit(View)` in the Activity:
 * ```
 * java.lang.IllegalStateException: Could not find method handlePaymentSubmit(View)
 *   in a parent or ancestor Context for android:onClick attribute defined on View
 *   class android.widget.Button with id 'btnSubmitPayment'
 * ```
 *
 * Reference: https://stackoverflow.com/questions/20665616/proguard-illegalstateexception-could-not-find-method
 * Reference: https://issuetracker.google.com/issues/37129458
 * Reference: https://developer.android.com/guide/topics/ui/declaring-layout#onClick
 *
 * Detection: Scan layout XMLs for android:onClick="method" → find method in source →
 *   check if method was renamed in mapping.txt for the host class.
 * Fix: -keepclassmembers class app.proguard.crashes.AndroidOnClickCrash {
 *     public void handlePaymentSubmit(android.view.View);
 * }
 */
object AndroidOnClickCrash {

    /**
     * Payment submit handler referenced ONLY from `res/layout/payment_form_demo.xml`
     * via `android:onClick="handlePaymentSubmit"`.
     *
     * Because this method has no direct call sites in Kotlin code (it's only referenced
     * from XML), R8 renames it. The XML attribute holds the original string → the
     * runtime method lookup fails with [IllegalStateException].
     *
     * NOTE: The `@JvmStatic` annotation ensures this generates a static method signature,
     * consistent with how Activities expose onClick handlers to the framework.
     */
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun handlePaymentSubmit(view: View) {
        // Referenced only from res/layout/payment_form_demo.xml android:onClick.
        // R8 renames this method → IllegalStateException at runtime when Android
        // tries to find "handlePaymentSubmit(View)" by reflection.
    }

    fun trigger(): String {
        return "AndroidOnClick: handlePaymentSubmit(View) referenced only from " +
            "android:onClick in XML. R8 renames it → " +
            "IllegalStateException: Could not find method at runtime."
    }
}
