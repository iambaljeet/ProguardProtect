package app.proguard.models

/**
 * Network configuration class with companion object holding constants.
 * Used in Companion Object crash demonstration (Case 10).
 * Reference: https://stackoverflow.com/q/57974556
 *
 * The companion object is accessed dynamically via Kotlin reflection.
 * Without keep rules, R8 renames NetworkConfig$Companion → crash.
 */
class NetworkConfig(val baseUrl: String, val timeout: Int) {

    companion object {
        const val DEFAULT_TIMEOUT = 30
        const val VERSION_CODE = "2.1.0"
        const val ENVIRONMENT = "production"

        fun createDefault() = NetworkConfig("https://api.example.com", DEFAULT_TIMEOUT)

        fun fromEnv(env: String) = when (env) {
            "staging" -> NetworkConfig("https://staging.api.example.com", 60)
            "dev" -> NetworkConfig("https://dev.api.example.com", 120)
            else -> createDefault()
        }
    }
}
