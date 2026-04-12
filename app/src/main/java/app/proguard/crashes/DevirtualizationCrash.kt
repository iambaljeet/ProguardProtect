package app.proguard.crashes

import android.util.Log
import app.proguard.models.DataProcessor
import app.proguard.models.Processable

/**
 * Crash Type 7: IllegalAccessError due to R8 devirtualization.
 *
 * DataProcessor extends BaseProcessor (which has private process(String))
 * and implements Processable (which has default process(String)).
 * DataProcessor does NOT override process().
 *
 * R8 may devirtualize the interface default method call to the
 * base class's private method, causing IllegalAccessError at runtime.
 *
 * We use direct instantiation (not reflection) so R8 can see and optimize the call.
 */
object DevirtualizationCrash {

    fun trigger(): String {
        return try {
            val processor = DataProcessor()
            // Cast to interface to call the default method
            val processable: Processable = processor
            val result = processable.process("test data")
            Log.d("DevirtualizationCrash", "Success: $result")
            result
        } catch (e: IllegalAccessError) {
            val msg = "IllegalAccessError: ${e.message}"
            Log.e("DevirtualizationCrash", msg, e)
            throw RuntimeException(msg, e)
        } catch (e: Exception) {
            val msg = "Exception: ${e.javaClass.simpleName}: ${e.message}"
            Log.e("DevirtualizationCrash", msg, e)
            throw RuntimeException(msg, e)
        }
    }
}
