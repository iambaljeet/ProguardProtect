package app.proguard.models

/**
 * Concrete implementation of EventHandler that is loaded dynamically via Class.forName.
 * Without keep rules, R8 may rename/strip this class or its methods.
 */
class AnalyticsEventHandler : EventHandler {
    override fun onEvent(eventName: String, data: Map<String, Any>): String {
        val details = data.entries.joinToString(", ") { "${it.key}=${it.value}" }
        return "Analytics: event='$eventName' data=[$details]"
    }

    override fun getHandlerName(): String {
        return "AnalyticsEventHandler"
    }
}
