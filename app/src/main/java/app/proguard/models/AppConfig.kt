package app.proguard.models

/**
 * Model class whose fields are accessed via reflection (getDeclaredField).
 * Without keep rules, R8 renames the fields → NoSuchFieldException at runtime.
 */
class AppConfig {
    var serverUrl: String = "https://api.example.com"
    var apiKey: String = "sk-1234567890"
    var maxRetries: Int = 3
    var debugMode: Boolean = false
    var timeoutMs: Long = 30000L
}
