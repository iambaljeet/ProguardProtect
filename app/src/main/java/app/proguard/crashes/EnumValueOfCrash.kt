package app.proguard.crashes

import android.util.Log

/**
 * Crash Type 4: Enum.valueOf() with dynamically constructed enum name.
 *
 * R8 may rename enum constants. When we do Enum.valueOf() with a string
 * that was the original constant name, it will fail with IllegalArgumentException.
 *
 * NOTE: We use dynamic string construction so R8 can't trace and adapt the constant names.
 */
object EnumValueOfCrash {

    fun trigger(): String {
        return try {
            // Dynamically construct the enum class name so R8 can't trace it
            val pkg = listOf("app", "proguard", "models").joinToString(".")
            val enumClassName = "$pkg.OrderStatus"

            // Dynamically construct the constant name so R8 can't adapt it
            val statusParts = listOf("SHIP", "PED")
            val statusName = statusParts.joinToString("")

            @Suppress("UNCHECKED_CAST")
            val enumClass = Class.forName(enumClassName)
            val enumConstants = enumClass.enumConstants as Array<Enum<*>>
            val value = enumConstants.first { it.name == statusName }

            val result = "Order status: ${value.name}"
            Log.d("EnumValueOfCrash", "Success: $result")
            result
        } catch (e: Exception) {
            val error = "Enum crash: ${e::class.simpleName}: ${e.message}"
            Log.e("EnumValueOfCrash", error, e)
            error
        }
    }
}
