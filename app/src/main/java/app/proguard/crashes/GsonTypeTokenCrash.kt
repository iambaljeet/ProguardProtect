package app.proguard.crashes

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Crash Type 9: Gson TypeToken — Generic Signature Stripped by R8
 *
 * Reference: https://github.com/google/gson/issues/2658
 * Reference: https://issuetracker.google.com/issues/421626188
 * Reference: https://google.github.io/gson/Troubleshooting.html
 *
 * R8 strips the Java `Signature` attribute when `-keepattributes Signature` is absent.
 * Gson's TypeToken relies on this attribute to recover the generic type `T` at runtime.
 * Without it, `TypeToken<List<ProductCatalog>>(){}` loses its generic info → crash.
 *
 * Expected crash: RuntimeException "Missing type parameter" OR IllegalStateException
 * from Gson when it cannot determine the parameterized type.
 * Also: ProductCatalog fields may be renamed by R8 (no keep rule), so deserialization
 * returns objects with default/null values → silent data corruption.
 */
object GsonTypeTokenCrash {

    private const val TAG = "GsonTypeTokenCrash"

    fun trigger(): String {
        val json = """
            [
                {"id": 1, "name": "Laptop", "price": 999.99, "category": "Electronics"},
                {"id": 2, "name": "Headphones", "price": 79.99, "category": "Audio"}
            ]
        """.trimIndent()

        val gson = Gson()

        // TypeToken captures the generic type via the Signature bytecode attribute.
        // R8 strips Signature by default → TypeToken loses <List<ProductCatalog>> info.
        val typeToken = object : TypeToken<List<app.proguard.models.ProductCatalog>>() {}
        val products: List<app.proguard.models.ProductCatalog> = gson.fromJson(json, typeToken.type)

        Log.d(TAG, "Parsed ${products.size} products")

        // Even if TypeToken doesn't throw, R8 may rename ProductCatalog fields
        // (id → a, name → b, etc.), so all fields will be default values
        val firstProduct = products.firstOrNull()
            ?: throw RuntimeException("No products deserialized — Gson TypeToken lost generic type info")

        if (firstProduct.name.isEmpty() || firstProduct.id == 0) {
            throw RuntimeException(
                "Gson TypeToken field corruption: id=${firstProduct.id}, name='${firstProduct.name}'. " +
                "R8 stripped Signature attribute or renamed ProductCatalog fields. " +
                "Fix: add -keepattributes Signature and keep ProductCatalog."
            )
        }

        return "✅ TypeToken: parsed ${products.size} products, first='${firstProduct.name}' \$${firstProduct.price}"
    }
}
