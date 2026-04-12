package app.proguard.models

/**
 * Kotlin data class used to demonstrate Crash Type 22 (DATA_CLASS_MEMBER_STRIPPED).
 *
 * Kotlin data classes auto-generate: copy(), componentN() for destructuring,
 * equals(), hashCode(), toString(). R8 in full mode (default AGP 8+) removes
 * unused generated methods. If copy()/componentN() are only accessed via
 * Kotlin reflection (KClass.memberFunctions, KFunction.call()), R8 may
 * strip them causing NoSuchMethodException at runtime.
 *
 * Reference: https://github.com/Kotlin/kotlinx.serialization/issues/2312
 * Reference: https://stackoverflow.com/questions/65748959/r8-removes-kotlin-data-class-copy-method
 * Reference: https://issuetracker.google.com/issues/172677414
 */
data class PriceRecord(
    val productId: String,
    val basePrice: Double,
    val discountPercent: Int,
    val currency: String
)
