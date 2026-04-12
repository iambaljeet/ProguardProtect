package app.proguard.models

/**
 * An enum class whose constants are looked up by string name via enumValueOf().
 * Without keep rules, R8 renames enum constants → IllegalArgumentException at runtime.
 */
enum class OrderStatus {
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    fun getDisplayName(): String {
        return name.lowercase().replaceFirstChar { it.uppercase() }
    }
}
