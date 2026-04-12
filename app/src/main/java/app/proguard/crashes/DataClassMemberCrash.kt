package app.proguard.crashes

import app.proguard.models.PriceRecord
import kotlin.reflect.full.memberFunctions

/**
 * Crash Type 22: DATA_CLASS_MEMBER_STRIPPED
 *
 * Kotlin data classes auto-generate `copy()` and `componentN()` methods.
 * R8 in full mode (default since AGP 8.0) removes these methods if they are
 * not called directly in compiled code. When these methods are invoked via
 * Kotlin reflection (KClass.memberFunctions), they may be absent, causing
 * NoSuchMethodException or IllegalArgumentException at runtime.
 *
 * This is a real-world issue in frameworks that rely on Kotlin metadata + reflection
 * to clone/transform data objects (e.g., Redux-style state management, data mappers).
 *
 * Additionally, R8 may remove `componentN()` functions used in destructuring
 * declarations if the destructuring only happens inside reflection-based code.
 *
 * Reference: https://github.com/Kotlin/kotlinx.serialization/issues/2312
 * Reference: https://stackoverflow.com/questions/65748959/r8-removes-kotlin-data-class-copy-method
 * Reference: https://issuetracker.google.com/issues/172677414
 * Reference: https://jakewharton.com/r8-optimization-staticization-of-companion-objects/
 *
 * Detection: Find data class declarations + KClass.memberFunctions / copy() via reflection.
 *            Confirm class renamed in mapping AND copy/componentN not in member list.
 * Fix: -keep class app.proguard.models.PriceRecord { *; }
 *       OR -keepclassmembers class app.proguard.models.PriceRecord { *; }
 */
object DataClassMemberCrash {

    /**
     * Uses Kotlin reflection to call copy() on a data class.
     * R8 may remove copy() if it's only accessed reflectively — NoSuchMethodException.
     */
    fun triggerCrash(): String {
        val original = PriceRecord(
            productId = "PROD-001",
            basePrice = 99.99,
            discountPercent = 10,
            currency = "USD"
        )

        // Access copy() via Kotlin reflection — R8 may have removed it
        val copyFn = original::class.memberFunctions.find { it.name == "copy" }
            ?: throw NoSuchMethodException(
                "copy() method not found in ${original::class.qualifiedName} — " +
                "R8 removed it because it was only referenced via reflection. " +
                "Add -keepclassmembers class app.proguard.models.PriceRecord { *; }"
            )

        // Attempt to call copy() via reflection
        val discounted = copyFn.call(original, "PROD-001", 89.99, 10, "USD")
        return "Discounted record: $discounted"
    }

    /**
     * Uses componentN() functions for destructuring via reflection.
     * R8 may strip component1(), component2(), etc.
     */
    fun triggerDestructuringCrash(): String {
        val record = PriceRecord("PROD-002", 49.99, 5, "EUR")

        // Access component functions via Kotlin reflection
        val component1 = record::class.memberFunctions.find { it.name == "component1" }
            ?: throw NoSuchMethodException(
                "component1() not found in PriceRecord — R8 removed componentN() functions"
            )
        val productId = component1.call(record) as String
        return "Product ID from component1(): $productId"
    }

    fun trigger(): String {
        return try {
            triggerCrash()
        } catch (e: NoSuchMethodException) {
            "DataClassMember CRASH: ${e.message}"
        } catch (e: Exception) {
            "DataClassMember: ${e.javaClass.simpleName} — ${e.message}"
        }
    }
}
