package app.proguard.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * ViewModel with custom factory. R8 may strip the factory's no-arg constructor
 * since it's only instantiated via reflection by ViewModelProvider.
 * Ref: https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories
 */
class UserViewModel : ViewModel() {
    var userName: String = ""

    /**
     * Custom factory - R8 strips the no-arg constructor since it's not
     * directly called from Kotlin code (only via reflection by ViewModelProvider).
     */
    class Factory : ViewModelProvider.Factory {
        // No-arg constructor - R8 may remove this as "unused"
        // ViewModelProvider.NewInstanceFactory uses reflection to call it
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UserViewModel() as T
        }
    }
}
