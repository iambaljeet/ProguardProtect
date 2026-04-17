package app.proguard.crashes

import android.content.Context
import app.proguard.models.UserViewModel

/**
 * Type 32: VIEWMODEL_FACTORY_CONSTRUCTOR_STRIPPED
 * Custom ViewModelProvider.Factory subclass loses its no-arg constructor when R8
 * strips it. Instantiating via reflection (as ViewModelProvider does) throws
 * InstantiationException or IllegalAccessException at runtime.
 * Ref: https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories
 */
class ViewModelFactoryCrash {
    /**
     * Triggers the crash by attempting to instantiate the Factory via reflection,
     * the same way ViewModelProvider.NewInstanceFactory does internally.
     */
    fun triggerCrash(context: Context): String {
        // ViewModelProvider internally reflects on Factory, similar to:
        val factoryClass = UserViewModel.Factory::class.java
        val constructor = factoryClass.getDeclaredConstructor()  // NoSuchMethodException if stripped
        val factory = constructor.newInstance()
        return factory.toString()
    }
}
