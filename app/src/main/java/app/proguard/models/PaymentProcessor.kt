package app.proguard.models

/**
 * A payment processing class whose methods are called via reflection.
 * Without keep rules for its methods, R8 renames them → NoSuchMethodError at runtime.
 */
class PaymentProcessor {

    fun processPayment(amount: Double, currency: String): String {
        return "Payment of $amount $currency processed successfully"
    }

    fun validateCard(cardNumber: String): Boolean {
        return cardNumber.length == 16
    }

    fun getTransactionId(): String {
        return "TXN-${System.currentTimeMillis()}"
    }
}
