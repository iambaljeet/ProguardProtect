package app.proguard.crashes

import android.util.Log
import com.google.gson.Gson

/**
 * Crash Type 3: Gson Serialization Field Renaming
 *
 * Deserializes JSON into ApiResponse using Gson. R8 renames the fields
 * (userName → a, userEmail → b), so the JSON keys no longer match.
 * Result: fields are null/default despite valid JSON → logic error / NPE at runtime.
 */
object SerializationCrash {

    private const val TAG = "SerializationCrash"

    fun trigger(): String {
        return try {
            val json = """
                {
                    "userName": "John Doe",
                    "userEmail": "john@example.com",
                    "accountBalance": 1250.75,
                    "isVerified": true
                }
            """.trimIndent()

            val gson = Gson()
            val response = gson.fromJson(json, app.proguard.models.ApiResponse::class.java)

            Log.d(TAG, "Deserialized: ${response.getSummary()}")

            // This is the "crash" — fields are empty/default because R8 renamed them
            if (response.userName.isEmpty()) {
                Log.e(TAG, "CRASH: Gson deserialization failed - userName is empty due to R8 field renaming!")
                throw RuntimeException(
                    "Gson field mismatch! Expected userName='John Doe' but got '${response.userName}'. " +
                    "R8 renamed the field so JSON key 'userName' no longer matches."
                )
            }

            "✅ Gson deserialization: ${response.getSummary()}"
        } catch (e: RuntimeException) {
            Log.e(TAG, "CRASH: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "CRASH: ${e.javaClass.simpleName}", e)
            throw RuntimeException("Gson deserialization failed: ${e.message}", e)
        }
    }
}
