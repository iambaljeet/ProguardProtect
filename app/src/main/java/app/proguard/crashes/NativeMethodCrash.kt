package app.proguard.crashes

import app.proguard.models.ImageProcessor

/**
 * Crash Type 15: NATIVE_METHOD_RENAMED
 *
 * R8 renames the ImageProcessor class, breaking the JNI linkage.
 * The native library (.so) uses the original class name in its JNI function signatures:
 *   Java_app_proguard_models_ImageProcessor_processImage(JNIEnv*, jobject, ...)
 * After R8 renames the class, System.loadLibrary() succeeds but the JNI method
 * lookup fails → UnsatisfiedLinkError when calling processImage().
 *
 * Reference: https://stackoverflow.com/questions/19004114/jni-proguard-obfuscation
 * Reference: https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html
 *
 * Detection: Find classes with external/native methods → check if class renamed in mapping.
 * Fix: -keepclasseswithmembernames class * { native <methods>; }
 */
object NativeMethodCrash {
    fun trigger(): String {
        return "NativeMethod: ImageProcessor declares JNI external methods. " +
            "R8 will rename the class → UnsatisfiedLinkError at runtime when native library " +
            "looks for Java_app_proguard_models_ImageProcessor_processImage."
    }
}
