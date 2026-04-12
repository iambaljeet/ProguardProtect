package app.proguard.crashes

import android.os.Bundle
import android.os.Parcel
import android.util.Log
import app.proguard.models.ShippingAddress

/**
 * Crash Type 13: Parcelable — Class Renamed by R8, Bundle Deserialization Fails
 *
 * Reference: https://issuetracker.google.com/274185314 (R8 renames Parcelable classes)
 * Reference: https://codingtechroom.com/question/fix-badparcelableexception
 *
 * Android's Bundle/Parcel mechanism stores the Parcelable class name as a string.
 * On deserialization, it calls Class.forName(storedClassName) internally.
 * If R8 renamed ShippingAddress → "x7", Class.forName("app.proguard.models.ShippingAddress")
 * fails → android.os.BadParcelableException.
 *
 * This is a common real-world crash: data passed via Intent/Bundle, saved instance state,
 * or cross-process communication all use this serialization mechanism.
 *
 * Expected crash: android.os.BadParcelableException or ClassNotFoundException
 */
object ParcelableCrash {

    private const val TAG = "ParcelableCrash"

    fun trigger(): String {
        val address = ShippingAddress(
            recipientName = "Jane Smith",
            streetAddress = "456 Oak Avenue",
            city = "Springfield",
            postalCode = "12345",
            country = "US"
        )

        // Step 1: Serialize to Parcel (as Android does for Intent/Bundle)
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(address, 0)
            val bytes = parcel.marshall()
            Log.d(TAG, "Serialized ${bytes.size} bytes, class='${address::class.java.name}'")

            // Step 2: Deserialize (simulates cross-process or saved state restoration)
            // Android reads the stored class name and calls Class.forName() internally
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)

            @Suppress("DEPRECATION")
            val restored = parcel.readParcelable<ShippingAddress>(
                ShippingAddress::class.java.classLoader
            ) ?: throw RuntimeException("readParcelable returned null")

            Log.d(TAG, "Deserialized: ${restored.recipientName}, ${restored.city}")
            return "✅ Parcelable: '${restored.recipientName}' at ${restored.streetAddress}, ${restored.city}"
        } finally {
            parcel.recycle()
        }
    }
}
