package app.proguard.crashes

import android.content.Context
import app.proguard.models.PluginModule

/**
 * Type 30: CLASSLOADER_LOADCLASS
 * ClassLoader.loadClass() with a class name string is not detected by tools looking
 * only for Class.forName(). Both patterns have the same runtime failure mode.
 * Ref: https://r8.googlesource.com/r8/+/refs/heads/master/compatibility-faq.md
 */
class ClassLoaderCrash {
    /**
     * Uses Thread.currentThread().contextClassLoader.loadClass() to load a class
     * by name string. R8 removes PluginModule entirely since there's no direct reference.
     * At runtime: ClassNotFoundException because PluginModule was removed.
     */
    fun triggerCrash(context: Context): String {
        // This is the crash - loadClass() with string name not caught by Class.forName detectors
        val cls = Thread.currentThread().contextClassLoader!!
            .loadClass("app.proguard.models.PluginModule")  // ClassNotFoundException at runtime
        val instance = cls.newInstance() as PluginModule
        return instance.initialize()
    }
}
