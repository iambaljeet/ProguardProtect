package app.proguard.models

/**
 * Sealed class hierarchy representing checkout states.
 * Used in Sealed Class crash demonstration (Case 11).
 * Reference: https://issuetracker.google.com/issues/367205689
 *
 * R8 removes sealed subtypes not directly referenced in code.
 * `sealedSubclasses` reflection returns empty list → crash.
 */
sealed class CheckoutState {
    /** Initial state - cart is empty */
    object Empty : CheckoutState()

    /** Items added to cart */
    data class CartFilled(val itemCount: Int, val totalPrice: Double) : CheckoutState()

    /** Payment processing in progress */
    object Processing : CheckoutState()

    /** Order completed successfully */
    data class OrderPlaced(val orderId: String, val estimatedDelivery: String) : CheckoutState()

    /** Payment or validation failed */
    data class Failed(val errorCode: Int, val reason: String) : CheckoutState()
}
