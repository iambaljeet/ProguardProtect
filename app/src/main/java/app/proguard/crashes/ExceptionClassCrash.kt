package app.proguard.crashes

import android.content.Context
import app.proguard.models.NetworkException

/**
 * Type 33: EXCEPTION_CLASS_RENAMED
 * Custom exception class renamed by R8. Error recovery/analytics code stores
 * exception class names as strings (e.g., in SharedPreferences, databases, or APIs)
 * and later tries to reconstruct them via Class.forName(). After R8 obfuscation,
 * the stored name no longer matches the renamed class.
 * Ref: https://issuetracker.google.com/issues/353143650
 */
class ExceptionClassCrash {
    companion object {
        // The exception class name is stored as a string (e.g., from a config/API)
        // This is a common pattern in error reporting/retry frameworks
        private const val EXCEPTION_CLASS = "app.proguard.models.NetworkException"
    }

    /**
     * Triggers the crash: loads and instantiates the exception by its original class name.
     * After R8 renames NetworkException → e.g. 'x3', ClassNotFoundException is thrown.
     */
    fun triggerCrash(context: Context): String {
        // Common in error recovery frameworks that store exception types by name
        val exceptionClass = Class.forName(EXCEPTION_CLASS)  // ClassNotFoundException after R8
        val constructor = exceptionClass.getDeclaredConstructor(String::class.java, Int::class.java)
        val exception = constructor.newInstance("Network error", 404) as NetworkException
        return exception.message ?: "unknown"
    }
}
