package app.proguard.models

/**
 * A data class used with Gson serialization/deserialization.
 * Fields do NOT have @SerializedName annotations and no keep rules.
 * R8 will rename fields (userName → a, userEmail → b) so JSON keys
 * won't match during deserialization → fields will be null.
 */
data class ApiResponse(
    val userName: String = "",
    val userEmail: String = "",
    val accountBalance: Double = 0.0,
    val isVerified: Boolean = false
) {
    fun getSummary(): String {
        return "User: $userName, Email: $userEmail, Balance: $accountBalance, Verified: $isVerified"
    }
}
