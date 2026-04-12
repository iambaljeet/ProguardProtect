package app.proguard.crashes

import android.util.Log

/**
 * Crash Type 1: ClassNotFoundException
 *
 * This class loads UserProfile via Class.forName() with a dynamically constructed
 * class name - a common pattern in plugin systems, deserialization, and config-driven
 * class loading. R8 cannot trace dynamic strings, so it can't adapt the class name.
 * Without ProGuard keep rules, R8 renames the class → ClassNotFoundException at runtime.
 */
object ReflectionClassCrash {

    private const val TAG = "ReflectionClassCrash"

    // Simulate a class name coming from config/server/database
    // R8 cannot trace string concatenation at runtime
    private fun getClassName(): String {
        val pkg = listOf("app", "proguard", "models").joinToString(".")
        return "$pkg.UserProfile"
    }

    fun trigger(): String {
        return try {
            val className = getClassName()
            Log.d(TAG, "Loading class: $className")
            val clazz = Class.forName(className)
            val instance = clazz.getDeclaredConstructor().newInstance()
            val method = clazz.getDeclaredMethod("getDisplayInfo")
            val result = method.invoke(instance) as String
            Log.d(TAG, "Success: $result")
            "✅ Reflection class loaded: $result"
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "CRASH: ClassNotFoundException", e)
            throw RuntimeException("ProGuard stripped/renamed the class!", e)
        } catch (e: Exception) {
            Log.e(TAG, "CRASH: ${e.javaClass.simpleName}", e)
            throw RuntimeException("Reflection failed: ${e.message}", e)
        }
    }
}
