package app.proguard.models

/**
 * Interface for event handling. Implementations are loaded dynamically.
 */
interface EventHandler {
    fun onEvent(eventName: String, data: Map<String, Any>): String
    fun getHandlerName(): String
}
