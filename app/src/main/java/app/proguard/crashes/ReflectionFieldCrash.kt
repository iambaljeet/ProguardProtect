package app.proguard.crashes

import android.util.Log
import app.proguard.models.AppConfig

/**
 * Crash Type 6: NoSuchFieldException via getDeclaredField().
 *
 * R8 renames fields. When we access fields by their original name via reflection,
 * the renamed field can't be found → NoSuchFieldException at runtime.
 */
object ReflectionFieldCrash {

    fun trigger(): String {
        return try {
            val config = AppConfig()

            // Access fields by name via reflection - R8 renames these
            val fieldNames = listOf("server", "Url").joinToString("")
            val clazz = config::class.java
            val field = clazz.getDeclaredField(fieldNames)
            field.isAccessible = true
            val value = field.get(config)

            val result = "Field value: $value"
            Log.d("ReflectionFieldCrash", "Success: $result")
            result
        } catch (e: Exception) {
            val error = "Field crash: ${e::class.simpleName}: ${e.message}"
            Log.e("ReflectionFieldCrash", error, e)
            error
        }
    }
}
