package app.proguard.models

import java.lang.reflect.ParameterizedType

/**
 * Abstract generic base class for typed network callbacks.
 *
 * This class is involved in Crash Type 20 (GENERIC_SIGNATURE_STRIPPED).
 * Subclasses use `(javaClass.genericSuperclass as ParameterizedType)` to
 * introspect the generic type argument T at runtime (common in Retrofit/Gson callbacks).
 *
 * When `-keepattributes Signature` is missing from ProGuard rules, R8 strips
 * the `Signature` bytecode attribute. At runtime, `genericSuperclass` returns
 * a raw `Class` instead of a `ParameterizedType`, causing:
 *   ClassCastException: class cannot be cast to ParameterizedType
 *
 * Reference: https://stackoverflow.com/questions/6034233/class-cast-exception-on-getting-generic-type-information
 * Reference: https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#gson-and-generic-type
 */
abstract class NetworkCallback<T> {
    /**
     * Introspects the generic type parameter T at runtime.
     * CRASHES when R8 strips Signature attribute: genericSuperclass is not ParameterizedType.
     */
    fun getResponseType(): Class<*> {
        val superclass = javaClass.genericSuperclass
        val parameterized = superclass as ParameterizedType   // ClassCastException without Signature attr
        @Suppress("UNCHECKED_CAST")
        return parameterized.actualTypeArguments[0] as Class<*>
    }
}
