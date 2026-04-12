package app.proguard.crashes

import app.proguard.models.ApiCallInterface
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * Crash Type 23: DYNAMIC_PROXY_STRIPPED
 *
 * Java's `Proxy.newProxyInstance()` creates a runtime implementation of an interface.
 * Retrofit and many AOP frameworks use this approach. R8 may rename or remove
 * interfaces that have no concrete implementation class in compiled code — only
 * a `Proxy.newProxyInstance()` reference.
 *
 * When the interface is renamed by R8, `Proxy.newProxyInstance()` throws:
 *   IllegalArgumentException: app.proguard.models.ApiCallInterface is not an interface
 * Because R8 renamed it to something like "a3" but the InvocationHandler still
 * references the original Class object — which now may point to a non-interface.
 *
 * Also: if the interface has no keep rules, R8 may inline/remove its methods,
 * causing AbstractMethodError when the proxy tries to dispatch the call.
 *
 * Reference: https://stackoverflow.com/questions/63671867/r8-proguard-dynamic-proxy-illegal-argument-exception
 * Reference: https://issuetracker.google.com/issues/148495258
 * Reference: https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#dynamic-proxies
 * Reference: https://jakewharton.com/r8-optimization-devirtualization/
 *
 * Detection: Find Proxy.newProxyInstance() calls → check if passed interface was renamed.
 * Fix: -keep interface app.proguard.models.ApiCallInterface { *; }
 */
object DynamicProxyCrash {

    /**
     * Creates a dynamic proxy for ApiCallInterface using Java's Proxy mechanism.
     * This is exactly what Retrofit does internally for @GET/@POST annotated methods.
     *
     * R8 renames ApiCallInterface since it has no concrete implementation.
     * Proxy.newProxyInstance() with a renamed interface → IllegalArgumentException.
     */
    fun triggerCrash(): String {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "execute" -> "Proxied response for ${args?.get(0)}"
                "cancel" -> null
                else -> throw UnsupportedOperationException("Unknown: ${method.name}")
            }
        }

        // R8 may rename ApiCallInterface → the proxy instantiation silently uses the
        // renamed class, but downstream code that casts to ApiCallInterface by name
        // (e.g., via Class.forName) will fail
        @Suppress("UNCHECKED_CAST")
        val proxy = Proxy.newProxyInstance(
            ApiCallInterface::class.java.classLoader,
            arrayOf(ApiCallInterface::class.java),    // R8 renamed this interface
            handler
        ) as ApiCallInterface

        // Calling through the proxy — if R8 renamed/stripped methods, AbstractMethodError
        return proxy.execute("https://api.example.com/data", mapOf("key" to "value"))
    }

    fun trigger(): String {
        return try {
            triggerCrash()
        } catch (e: IllegalArgumentException) {
            "DynamicProxy CRASH: IllegalArgumentException — R8 renamed ApiCallInterface. ${e.message}"
        } catch (e: AbstractMethodError) {
            "DynamicProxy CRASH: AbstractMethodError — R8 stripped interface methods. ${e.message}"
        } catch (e: Exception) {
            "DynamicProxy: ${e.javaClass.simpleName} — ${e.message}"
        }
    }
}
