package app.proguard.crashes

import android.util.Log

/**
 * Crash Type 8: NoClassDefFoundError via ServiceLoader-like pattern.
 *
 * Uses Class.forName to dynamically load a class that R8 can't trace.
 * When the class is stripped by R8, NoClassDefFoundError occurs.
 */
object ServiceLoaderCrash {

    fun trigger(): String {
        return try {
            // Build class name dynamically so R8 can't trace it
            val parts = arrayOf("app", "proguard", "models", "Plugin", "Registry")
            val className = parts.take(3).joinToString(".") + "." + parts.drop(3).joinToString("")
            Log.d("ServiceLoaderCrash", "Loading class: $className")

            val clazz = Class.forName(className)
            val instance = clazz.getDeclaredConstructor().newInstance()

            // Use reflection to call methods
            val registerMethod = clazz.getDeclaredMethod("register", String::class.java, Function0::class.java)
            val factory: () -> String = { "Hello from dynamic plugin!" }
            registerMethod.invoke(instance, "greeting", factory)

            val executeMethod = clazz.getDeclaredMethod("execute", String::class.java)
            val result = executeMethod.invoke(instance, "greeting") as String

            Log.d("ServiceLoaderCrash", "Success: $result")
            result
        } catch (e: NoClassDefFoundError) {
            val msg = "NoClassDefFoundError: ${e.message}"
            Log.e("ServiceLoaderCrash", msg, e)
            throw RuntimeException(msg, e)
        } catch (e: ClassNotFoundException) {
            val msg = "ClassNotFoundException: ${e.message}"
            Log.e("ServiceLoaderCrash", msg, e)
            throw RuntimeException(msg, e)
        } catch (e: Exception) {
            val msg = "Exception: ${e.javaClass.simpleName}: ${e.message}"
            Log.e("ServiceLoaderCrash", msg, e)
            throw RuntimeException(msg, e)
        }
    }
}
