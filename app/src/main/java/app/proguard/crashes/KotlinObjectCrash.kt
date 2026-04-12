package app.proguard.crashes

import app.proguard.models.ConfigManager

/**
 * Crash Type 18: KOTLIN_OBJECT_INSTANCE_REMOVED
 *
 * Kotlin object declarations compile to classes with a static INSTANCE field.
 * DI frameworks and reflection code access this field:
 *   ConfigManager::class.objectInstance
 *   → internally: ConfigManager.class.getField("INSTANCE").get(null)
 *
 * R8 may rename the ConfigManager class or remove its INSTANCE field.
 * When INSTANCE is missing: NoSuchFieldError at runtime.
 * When class is renamed: ClassNotFoundException finding "app.proguard.models.ConfigManager"
 *
 * Reference: https://discuss.kotlinlang.org/t/proguard-rules-for-kotlin-object/8444
 * Reference: https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md
 *
 * Detection: Find Kotlin object declarations → check if renamed in mapping.
 *            Also check if INSTANCE field exists in DEX.
 * Fix: -keepclassmembers class * { public static ** INSTANCE; }
 */
object KotlinObjectCrash {
    fun triggerCrash(): String {
        // Access via reflection - as DI frameworks do
        val clazz = Class.forName("app.proguard.models.ConfigManager")
        // R8 renamed ConfigManager → ClassNotFoundException
        // OR removed INSTANCE field → NoSuchFieldError
        val instance = clazz.getField("INSTANCE").get(null)
        return instance.toString()
    }

    fun accessViaKotlinReflect(): String {
        // Kotlin reflection accesses INSTANCE field internally
        val instance = ConfigManager::class.objectInstance
            ?: throw IllegalStateException("ConfigManager.INSTANCE is null — R8 removed it")
        return instance.apiEndpoint
    }

    fun trigger(): String {
        return try {
            triggerCrash()
        } catch (e: ClassNotFoundException) {
            "KotlinObject CRASH: ClassNotFoundException — R8 renamed ConfigManager. ${e.message}"
        } catch (e: NoSuchFieldException) {
            "KotlinObject CRASH: NoSuchFieldError — R8 removed INSTANCE field. ${e.message}"
        } catch (e: Exception) {
            "KotlinObject: ${e.javaClass.simpleName} — ${e.message}"
        }
    }
}
