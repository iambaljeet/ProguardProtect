package app.proguard.models

/**
 * Product catalog entry model.
 * Used in Gson TypeToken crash demonstration (Case 9).
 * Reference: https://github.com/google/gson/issues/2658
 *
 * Without -keepattributes Signature + -keep class for this model,
 * TypeToken<List<ProductCatalog>>(){} loses generic type info at runtime.
 */
data class ProductCatalog(
    val id: Int = 0,
    val name: String = "",
    val price: Double = 0.0,
    val category: String = ""
)
