package app.proguard.models

/**
 * Interface used with Java dynamic proxy (Proxy.newProxyInstance).
 *
 * This interface is involved in Crash Type 23 (DYNAMIC_PROXY_STRIPPED).
 * Java's dynamic proxy (and Retrofit's implementation) uses Proxy.newProxyInstance()
 * to create a runtime implementation of this interface. If R8 renames or removes the
 * interface (since it has no concrete implementation class), the proxy creation
 * throws IllegalArgumentException at runtime.
 *
 * Reference: https://stackoverflow.com/questions/63671867/r8-proguard-dynamic-proxy-illegal-argument-exception
 * Reference: https://issuetracker.google.com/issues/148495258
 * Reference: https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#dynamic-proxies
 */
interface ApiCallInterface {
    /**
     * Executes an API call with the given endpoint and parameters.
     * Called via InvocationHandler — never called directly in code.
     */
    fun execute(endpoint: String, params: Map<String, String>): String

    /**
     * Cancels the current in-flight API request.
     */
    fun cancel()
}
