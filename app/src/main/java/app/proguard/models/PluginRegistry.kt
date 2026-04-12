package app.proguard.models

/**
 * A utility class only used through ServiceLoader-like pattern.
 * R8 may strip this class when it can't see the reference.
 */
class PluginRegistry {
    private val plugins = mutableMapOf<String, () -> String>()

    fun register(name: String, factory: () -> String) {
        plugins[name] = factory
    }

    fun execute(name: String): String {
        return plugins[name]?.invoke() ?: "Plugin '$name' not found"
    }

    fun listPlugins(): List<String> = plugins.keys.toList()
}
