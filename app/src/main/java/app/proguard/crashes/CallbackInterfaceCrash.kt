package app.proguard.crashes

import android.util.Log
import app.proguard.models.EventHandler

/**
 * Crash Type 5: Callback interface implementation loaded dynamically.
 *
 * The concrete class AnalyticsEventHandler implements EventHandler and is loaded
 * via Class.forName() with dynamic string construction. Without keep rules, R8
 * renames/strips the class → ClassNotFoundException or AbstractMethodError at runtime.
 */
object CallbackInterfaceCrash {

    fun trigger(): String {
        return try {
            // Dynamically construct class name so R8 can't trace it
            val pkg = listOf("app", "proguard", "models").joinToString(".")
            val handlerClassName = "$pkg.AnalyticsEventHandler"

            val handlerClass = Class.forName(handlerClassName)
            val handler = handlerClass.getDeclaredConstructor().newInstance() as EventHandler

            val result = handler.onEvent(
                "purchase",
                mapOf("item" to "Premium", "price" to 9.99, "currency" to "USD")
            )
            val name = handler.getHandlerName()

            val output = "$name: $result"
            Log.d("CallbackInterfaceCrash", "Success: $output")
            output
        } catch (e: Exception) {
            val error = "Callback crash: ${e::class.simpleName}: ${e.message}"
            Log.e("CallbackInterfaceCrash", error, e)
            error
        }
    }
}
