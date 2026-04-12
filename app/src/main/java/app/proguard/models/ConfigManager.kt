package app.proguard.models

/**
 * Application configuration singleton using Kotlin object declaration.
 * Demonstrates R8 Kotlin object INSTANCE stripping (Crash Type 18).
 * Reference: https://discuss.kotlinlang.org/t/proguard-rules-for-kotlin-object/8444
 * Reference: https://stackoverflow.com/questions/51271286/proguard-generating-notes-for-all-instance-fields-with-kotlin-reflect
 * Reference: https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md
 *
 * Kotlin object declarations compile to a class with a static INSTANCE field.
 * Dependency injection frameworks and reflection code access:
 *   ConfigManager::class.objectInstance  (uses INSTANCE field)
 *   ConfigManager::class.java.getField("INSTANCE")
 * R8 may rename/remove the INSTANCE field → NoSuchFieldError at runtime.
 */
object ConfigManager {
    val apiEndpoint: String = "https://api.example.com"
    val timeout: Int = 30
    val debugMode: Boolean = false

    fun getConfig(key: String): String? = when (key) {
        "endpoint" -> apiEndpoint
        else -> null
    }
}
