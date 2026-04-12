package app.proguard.models

import android.webkit.JavascriptInterface

/**
 * WebView JavaScript bridge handler.
 * Demonstrates R8 JavascriptInterface stripping (Crash Type 14).
 * Reference: https://codingtechroom.com/question/how-to-resolve-javascript-interface-issues-with-android-proguard
 * Reference: https://stackoverflow.com/questions/17629507/how-to-configure-proguard-for-javascript-interface
 *
 * Without keep rules, R8 renames methods annotated with @JavascriptInterface.
 * JavaScript in WebView calls methods by their original Java name.
 * After R8 obfuscation, those names no longer exist → runtime failure.
 */
class WebBridgeHandler {
    /**
     * Called from JavaScript via: window.bridge.getUserName()
     * R8 renames this to something like "a()" → JS cannot call it.
     */
    @JavascriptInterface
    fun getUserName(): String = "JohnDoe"

    /**
     * Called from JavaScript via: window.bridge.submitForm(data)
     */
    @JavascriptInterface
    fun submitForm(data: String): Boolean {
        return data.isNotEmpty()
    }

    /**
     * Called from JavaScript via: window.bridge.showNativeToast(msg)
     */
    @JavascriptInterface
    fun showNativeToast(message: String) {
        // Would show a toast but just for demonstration
    }
}
