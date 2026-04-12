package app.proguard.crashes

import app.proguard.models.NetworkCallback
import app.proguard.models.UserProfile
import java.lang.reflect.ParameterizedType

/**
 * Crash Type 20: GENERIC_SIGNATURE_STRIPPED
 *
 * R8 strips the `Signature` bytecode attribute when `-keepattributes Signature`
 * is missing from ProGuard rules. This attribute stores generic type information
 * (e.g. `NetworkCallback<UserProfile>`). Without it, `javaClass.genericSuperclass`
 * returns a raw `Class` object instead of a `ParameterizedType`.
 *
 * Code that casts `genericSuperclass` to `ParameterizedType` to discover the
 * generic type T at runtime (common in Retrofit callbacks, Gson TypeAdapters,
 * and custom dependency injection frameworks) throws:
 *   ClassCastException: class app.proguard.crashes.UserProfileCallback
 *     cannot be cast to class java.lang.reflect.ParameterizedType
 *
 * This pattern is widely used by libraries like Gson, Retrofit, and Moshi to
 * infer response types without explicit type tokens.
 *
 * Reference: https://stackoverflow.com/questions/6034233/class-cast-exception-on-getting-generic-type-information
 * Reference: https://issuetracker.google.com/issues/112386012
 * Reference: https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#gson-and-generic-type
 * Reference: https://medium.com/@ferrosward/r8-is-stripping-type-generic-signature-from-my-classes-3c3e7ed29bb2
 *
 * Detection: Find classes extending generic base + using genericSuperclass cast.
 *            Confirm class is renamed in mapping AND -keepattributes Signature absent.
 * Fix: -keepattributes Signature, InnerClasses, EnclosingMethod
 */
object GenericSignatureCrash {

    /**
     * Concrete callback implementation that relies on generic Signature attribute.
     * R8 renames this class AND strips the Signature bytecode attribute.
     */
    private class UserProfileCallback : NetworkCallback<UserProfile>()

    /**
     * Simulates what Retrofit/Gson does internally: reads the generic type from the
     * class's Signature attribute to determine the response deserialization type.
     * Throws ClassCastException when R8 strips the Signature attribute.
     */
    fun triggerCrash(): String {
        val callback = UserProfileCallback()
        // R8 strips Signature → genericSuperclass is Class, not ParameterizedType
        val superclass = callback.javaClass.genericSuperclass
        val parameterized = superclass as ParameterizedType   // ClassCastException here
        val typeArg = parameterized.actualTypeArguments[0]
        return "Response type discovered: $typeArg"
    }

    fun trigger(): String {
        return try {
            triggerCrash()
        } catch (e: ClassCastException) {
            "GenericSignature CRASH: ${e.message}"
        } catch (e: Exception) {
            "GenericSignature: ${e.javaClass.simpleName} — ${e.message}"
        }
    }
}
