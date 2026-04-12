package app.proguard.crashes

import android.util.Log
import app.proguard.models.JsonModel
import app.proguard.models.UserSettings

/**
 * Crash Type 12: Custom Runtime Annotation Stripped by R8
 *
 * Reference: https://issuetracker.google.com/issues/252388315 (R8 fullMode strips annotations)
 * Reference: https://medium.com/@sixtinbydizora/why-your-app-works-in-debug-but-breaks-in-release
 *
 * Custom annotations with @Retention(RUNTIME) should survive R8, but without
 * explicit `-keepattributes *Annotation*` or a keep rule for the annotation class,
 * R8 strips the annotation definition. At runtime, `getAnnotation(JsonModel::class.java)`
 * returns null → NullPointerException when accessing annotation properties.
 *
 * Common in annotation-based frameworks (DI, ORM, REST mappers, etc.)
 *
 * Expected crash: NullPointerException — annotation returns null
 */
object AnnotationCrash {

    private const val TAG = "AnnotationCrash"

    fun trigger(): String {
        val clazz = UserSettings::class.java
        Log.d(TAG, "Reading @JsonModel from ${clazz.simpleName}")

        // Reads the @JsonModel annotation from UserSettings class
        // Returns null if R8 stripped the annotation → NPE
        val annotation = clazz.getAnnotation(JsonModel::class.java)
            ?: throw NullPointerException(
                "@JsonModel annotation was stripped by R8 from ${clazz.simpleName}. " +
                "Fix: add -keepattributes *Annotation* and keep the annotation class."
            )

        Log.d(TAG, "Found annotation: tableName=${annotation.tableName}, version=${annotation.version}")
        return "✅ Annotation: tableName='${annotation.tableName}', version=${annotation.version}"
    }
}
