package app.proguard.models

/**
 * Custom exception for network errors. The class name is used in error analytics
 * and retry logic that reconstructs exceptions by class name at runtime.
 * R8 renames this class, breaking the string-based lookup.
 * Ref: https://issuetracker.google.com/issues/353143650
 */
class NetworkException(
    message: String,
    val errorCode: Int = 0
) : RuntimeException(message)
