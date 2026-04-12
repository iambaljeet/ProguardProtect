package app.proguard.models

/**
 * User settings data class annotated with @JsonModel for serialization.
 * Used in annotation stripping demonstration (Case 12).
 *
 * @JsonModel provides metadata read at runtime for database table mapping.
 * If R8 strips the @JsonModel annotation, getAnnotation() returns null → crash.
 */
@JsonModel(tableName = "user_settings", version = 3)
data class UserSettings(
    val userId: String = "",
    val theme: String = "light",
    val notifications: Boolean = true,
    val language: String = "en"
)
