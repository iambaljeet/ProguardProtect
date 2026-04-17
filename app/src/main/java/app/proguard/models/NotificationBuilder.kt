package app.proguard.models

/**
 * Notification builder with default parameters, exposed to Java via @JvmOverloads.
 * R8 may strip the generated single/double-argument overloads since they're not
 * directly called from Kotlin code (only the full 3-arg version is referenced).
 * Ref: https://kotlinlang.org/docs/java-to-kotlin-interop.html#overloads-generation
 */
class NotificationBuilder {
    /**
     * @JvmOverloads generates:
     *   sendNotification(title: String)
     *   sendNotification(title: String, body: String)
     *   sendNotification(title: String, body: String, priority: Int)
     * R8 may remove the first two overloads as "unused".
     */
    @JvmOverloads
    fun sendNotification(
        title: String,
        body: String = "No body",
        priority: Int = 0
    ): Boolean {
        return title.isNotEmpty()
    }
}
