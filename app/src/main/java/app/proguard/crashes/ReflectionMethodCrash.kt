package app.proguard.crashes

import android.util.Log

/**
 * Crash Type 2: NoSuchMethodError via getDeclaredMethod()
 *
 * Loads a known class but accesses its method by name via reflection.
 * R8 renames the method → getDeclaredMethod("processPayment") fails
 * with NoSuchMethodException at runtime.
 */
object ReflectionMethodCrash {

    private const val TAG = "ReflectionMethodCrash"

    fun trigger(): String {
        return try {
            val processor = app.proguard.models.PaymentProcessor()
            val clazz = processor.javaClass

            // Reflective method access — R8 will rename "processPayment" to something like "a"
            val method = clazz.getDeclaredMethod("processPayment", Double::class.java, String::class.java)
            method.isAccessible = true
            val result = method.invoke(processor, 99.99, "USD") as String

            Log.d(TAG, "Success: $result")
            "✅ Reflection method call: $result"
        } catch (e: NoSuchMethodException) {
            Log.e(TAG, "CRASH: NoSuchMethodException", e)
            throw RuntimeException("ProGuard renamed the method!", e)
        } catch (e: Exception) {
            Log.e(TAG, "CRASH: ${e.javaClass.simpleName}", e)
            throw RuntimeException("Reflection method failed: ${e.message}", e)
        }
    }
}
