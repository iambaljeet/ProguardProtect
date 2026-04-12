package app.proguard.models

import android.os.Parcel
import android.os.Parcelable

/**
 * Shipping address model implementing Android Parcelable.
 * Used in Parcelable crash demonstration (Case 13).
 * Reference: https://issuetracker.google.com/274185314
 * Reference: https://codingtechroom.com/question/fix-badparcelableexception-unmarshalling-parcelable-classes-android
 *
 * Without keep rules, R8 renames this class. When passed via Bundle/Intent
 * and then read back, Android's Parcelable mechanism uses Class.forName(className)
 * with the ORIGINAL class name stored at write time → BadParcelableException.
 */
data class ShippingAddress(
    val recipientName: String,
    val streetAddress: String,
    val city: String,
    val postalCode: String,
    val country: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(recipientName)
        parcel.writeString(streetAddress)
        parcel.writeString(city)
        parcel.writeString(postalCode)
        parcel.writeString(country)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ShippingAddress> {
        override fun createFromParcel(parcel: Parcel) = ShippingAddress(parcel)
        override fun newArray(size: Int) = arrayOfNulls<ShippingAddress>(size)
    }
}
