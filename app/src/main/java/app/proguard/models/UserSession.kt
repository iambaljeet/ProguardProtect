package app.proguard.models

import java.io.Serializable

/**
 * Model class representing a persisted user session.
 *
 * CRITICAL: This class implements [Serializable] WITHOUT an explicit [serialVersionUID].
 * When R8 obfuscates the class (renames it from 'UserSession' to e.g. 'a.b.c'),
 * any data serialized by an older build of the app (with the original class name
 * embedded in the serialized stream) cannot be deserialized by the newer build.
 *
 * The implicit serialVersionUID is computed from the class name, its structure,
 * implemented interfaces, and field types. When R8 renames the class or its fields,
 * the implicit UID changes → [java.io.InvalidClassException] at runtime.
 *
 * Additionally: the class name stored in the serialized stream will be the
 * R8-renamed name (e.g., 'a.b.c'). If another version of the app that has NOT been
 * obfuscated in the same way tries to read this data, it gets [ClassNotFoundException].
 *
 * Fix: Add `private const val serialVersionUID = 1L` AND add a keep rule:
 *      `-keep class app.proguard.models.UserSession { *; }`
 *
 * Reference: https://issuetracker.google.com/issues/177393019
 * Reference: https://stackoverflow.com/questions/285793/what-is-a-serialversionuid-and-why-should-i-use-it
 */
data class UserSession(
    val userId: String,
    val authToken: String,
    val expiresAt: Long,
    val userRole: String,
    val isVerified: Boolean
) : Serializable
// ⚠️ No explicit serialVersionUID — R8 will change the implicit UID when it renames fields!
// Fix: add `companion object { private const val serialVersionUID = 1L }`
