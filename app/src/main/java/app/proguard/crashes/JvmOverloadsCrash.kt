package app.proguard.crashes

import android.content.Context
import app.proguard.models.NotificationBuilder

/**
 * Type 31: JVMOVERLOADS_OVERLOAD_STRIPPED
 * @JvmOverloads generates multiple Java overloads for a Kotlin function with defaults.
 * R8 removes overloads it doesn't see being called. Invoking them via reflection
 * causes NoSuchMethodError at runtime.
 * Ref: https://kotlinlang.org/docs/java-to-kotlin-interop.html#overloads-generation
 */
class JvmOverloadsCrash {
    /**
     * Triggers the crash by using reflection to call the 1-arg overload generated
     * by @JvmOverloads. If R8 removed it, getDeclaredMethod() throws NoSuchMethodError.
     */
    fun triggerCrash(context: Context): String {
        val builder = NotificationBuilder()
        // Reflective call to the @JvmOverloads-generated single-arg overload
        val method = builder::class.java.getDeclaredMethod("sendNotification", String::class.java)
        return method.invoke(builder, "Test").toString()  // NoSuchMethodError if overload stripped
    }
}
