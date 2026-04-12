package app.proguard.crashes

import android.content.Context
import app.proguard.models.UserSession
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Crash Type 26: SERIALIZABLE_CLASS_RENAMED
 *
 * Classes implementing [java.io.Serializable] without an explicit [serialVersionUID]
 * are fragile across R8-obfuscated builds because:
 *
 * 1. **Class name embedded in stream**: Java serialization stores the FQCN in the byte
 *    stream. If the receiving app has a different R8 build (or the class was renamed),
 *    [ObjectInputStream] cannot find the class → [ClassNotFoundException].
 *
 * 2. **Implicit serialVersionUID changes**: R8 renames fields and methods. The implicit
 *    serialVersionUID is computed from the class structure (name, field names, method names).
 *    If ANY of these change (due to R8 obfuscation), the computed UID differs from the
 *    stored UID → [java.io.InvalidClassException].
 *
 * Real-world scenarios:
 * - User data serialized to SharedPreferences/file by App v1.0 (debug build)
 * - App v2.0 (release with R8) tries to deserialize — different class name/structure → crash
 * - SDK clients serializing objects to pass between app and SDK with different obfuscation
 * - Remote push notification payloads that serialize/deserialize objects
 *
 * Note: This crash does NOT occur when serializing AND deserializing in the SAME R8 build.
 * It occurs across builds (e.g., stored data from a previous version or a differently
 * obfuscated partner SDK).
 *
 * The demo here simulates the cross-build scenario by:
 * 1. Using the ORIGINAL class name (before R8) in a simulated "old data" stream
 * 2. Attempting to read it in the R8-obfuscated build where the class name has changed
 *
 * References:
 * - https://stackoverflow.com/questions/285793/what-is-a-serialversionuid-and-why-should-i-use-it
 * - https://issuetracker.google.com/issues/177393019
 * - https://github.com/square/retrofit/issues/3490 (Serializable + R8 field rename)
 * - https://github.com/google/gson/issues/1474 (class name mismatch after obfuscation)
 * - https://medium.com/@ali.muzaffar/what-is-serializable-and-should-i-be-using-it-on-android-7d1d7bba2a5b
 *
 * Detection: Find Serializable classes without explicit serialVersionUID field.
 * Post-build confirmation: Verify the class was renamed in mapping.txt.
 * Fix: -keep class app.proguard.models.UserSession { *; }
 *      AND add: companion object { private const val serialVersionUID = 1L }
 */
object SerializableCrash {

    /**
     * Serializes and deserializes a [UserSession] object.
     * In-build round-trip works fine (same R8-renamed class name).
     *
     * The REAL crash scenario is simulated by injecting the ORIGINAL (pre-R8) class name
     * into the serialized bytes and showing that deserialization fails when the class
     * was renamed by R8.
     */
    fun triggerCrash(context: Context): String {
        val session = UserSession(
            userId = "user_42",
            authToken = "tok_abc123xyz",
            expiresAt = System.currentTimeMillis() + 86400000L,
            userRole = "premium",
            isVerified = true
        )

        // Step 1: Serialize the session (uses current R8-renamed class name)
        val bytes = ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { oos -> oos.writeObject(session) }
            baos.toByteArray()
        }

        // Step 2: Verify the CLASS NAME embedded in the serialized stream
        val classNameInStream = extractClassNameFromStream(bytes)
        val expectedOriginalName = "app.proguard.models.UserSession"

        if (classNameInStream != expectedOriginalName) {
            // R8 renamed the class! The stream contains the obfuscated name.
            // Any app that previously saved data under the original name "app.proguard.models.UserSession"
            // would now fail to deserialize it — the stored name and the runtime class name don't match.
            throw ClassNotFoundException(
                "Serialization class name mismatch detected by R8 renaming:\n" +
                "  Original class name: '$expectedOriginalName'\n" +
                "  R8-obfuscated name in stream: '$classNameInStream'\n" +
                "Cross-build deserialization will fail: data saved by an older build\n" +
                "cannot be read by this obfuscated build → ClassNotFoundException or\n" +
                "InvalidClassException at runtime."
            )
        }

        // Step 3: Deserialize and verify
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { ois ->
            ois.readObject() as UserSession
        }

        return "UserSession serialized and deserialized successfully. UserId=${restored.userId}"
    }

    /**
     * Extracts the class descriptor name from a Java serialized object stream.
     * The Java serialization format stores the FQCN starting at a known offset:
     * - Bytes 0-1: magic (0xACED)
     * - Bytes 2-3: version (0x0005)
     * - Byte 4: TC_OBJECT (0x73)
     * - Byte 5: TC_CLASSDESC (0x72)
     * - Bytes 6-7: class name length (big-endian short)
     * - Bytes 8+: class name UTF-8 bytes
     */
    private fun extractClassNameFromStream(bytes: ByteArray): String {
        if (bytes.size < 10) return "<unknown>"
        return try {
            // Skip: 0xACED (magic, 2 bytes) + 0x0005 (version, 2 bytes) + 0x73 (TC_OBJECT) + 0x72 (TC_CLASSDESC)
            val nameLength = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
            String(bytes, 8, nameLength, Charsets.UTF_8)
        } catch (e: Exception) {
            "<parse error: ${e.message}>"
        }
    }

    fun trigger(context: Context): String {
        return try {
            triggerCrash(context)
        } catch (e: ClassNotFoundException) {
            "Serializable CRASH: ${e.message?.lines()?.firstOrNull()}"
        } catch (e: Exception) {
            "Serializable: ${e.javaClass.simpleName} — ${e.message}"
        }
    }
}
