package app.proguard.models

/**
 * Custom runtime annotation for JSON serialization metadata.
 * Used in annotation stripping demonstration (Case 12).
 * Reference: https://issuetracker.google.com/issues/252388315
 *
 * With @Retention(RUNTIME), this annotation should be accessible at runtime.
 * Without -keepattributes *Annotation* or a keep rule for the annotation class,
 * R8 strips it from the compiled code → getAnnotation() returns null → crash.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonModel(val tableName: String, val version: Int = 1)
