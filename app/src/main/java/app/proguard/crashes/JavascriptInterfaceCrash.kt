package app.proguard.crashes

import android.webkit.WebView
import app.proguard.models.WebBridgeHandler

/**
 * Crash Type 14: JAVASCRIPT_INTERFACE_STRIPPED
 *
 * R8 renames methods annotated with @JavascriptInterface on WebBridgeHandler.
 * JavaScript calls window.bridge.getUserName() but R8 renamed it to "a()" →
 * the JS call fails, often silently, or with "TypeError: window.bridge.getUserName is not a function".
 *
 * Reference: https://codingtechroom.com/question/how-to-resolve-javascript-interface-issues-with-android-proguard
 * Reference: https://stackoverflow.com/questions/17629507/how-to-configure-proguard-for-javascript-interface
 *
 * Detection: Check if class containing @JavascriptInterface methods is renamed in mapping.
 * Fix: -keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
 */
object JavascriptInterfaceCrash {
    /**
     * Adds WebBridgeHandler to a WebView as a JavaScript interface.
     * After R8 obfuscation, JavaScript cannot find the bridge methods by name.
     */
    fun setupWebBridge(webView: WebView) {
        val bridge = WebBridgeHandler()
        // "bridge" is the JS object name: window.bridge.getUserName()
        // R8 renames getUserName() → JavaScript calls fail at runtime
        webView.addJavascriptInterface(bridge, "bridge")
    }

    fun trigger(): String {
        return "JavascriptInterface: WebBridgeHandler methods annotated with @JavascriptInterface " +
            "will be renamed by R8. JS calls like window.bridge.getUserName() will fail at runtime."
    }
}
