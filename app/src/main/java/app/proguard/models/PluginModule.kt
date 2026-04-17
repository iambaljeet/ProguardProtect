package app.proguard.models

/**
 * Plugin module loaded dynamically via ClassLoader.
 * R8 will remove/rename this class since it has no direct keep rules.
 * Ref: https://r8.googlesource.com/r8/+/refs/heads/master/compatibility-faq.md
 */
class PluginModule {
    fun initialize(): String = "PluginModule initialized"
    fun getVersion(): String = "1.0.0"
}
