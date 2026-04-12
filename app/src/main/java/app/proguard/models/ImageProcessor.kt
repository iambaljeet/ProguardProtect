package app.proguard.models

/**
 * Native image processing library wrapper using JNI.
 * Demonstrates R8 native method class renaming (Crash Type 15).
 * Reference: https://stackoverflow.com/questions/19004114/jni-proguard-obfuscation
 * Reference: https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md
 *
 * R8 renames this class (e.g., ImageProcessor → a3). The compiled native library
 * registers its JNI functions as "Java_app_proguard_models_ImageProcessor_processImage".
 * After R8 renames the class, JNI lookup fails → UnsatisfiedLinkError.
 */
class ImageProcessor {
    /**
     * Processes image data using native C++ code.
     * JNI signature: Java_app_proguard_models_ImageProcessor_processImage
     * R8 renames class → JNI signature no longer matches → UnsatisfiedLinkError
     */
    external fun processImage(imageData: ByteArray): ByteArray

    /**
     * Applies filter using native code.
     * JNI signature: Java_app_proguard_models_ImageProcessor_applyFilter
     */
    external fun applyFilter(data: ByteArray, filterType: Int): ByteArray

    companion object {
        init {
            // In real code: System.loadLibrary("image-processor")
            // For demo: load would fail after class is renamed
        }
    }
}
