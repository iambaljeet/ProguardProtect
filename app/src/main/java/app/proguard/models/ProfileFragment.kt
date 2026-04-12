package app.proguard.models

import android.os.Bundle
import androidx.fragment.app.Fragment

/**
 * A Fragment subclass that represents a user profile screen.
 *
 * Fragment subclasses must have a public no-argument constructor because the
 * Android framework (FragmentManager) recreates them by calling the no-arg
 * constructor via reflection when:
 * - The activity is recreated after a configuration change (e.g., screen rotation)
 * - The app process is killed by the OS and the task is restored from the back stack
 * - `FragmentManager.instantiate(classLoader, className)` is called
 *
 * R8 renames Fragment subclasses that lack proper keep rules. The class name is
 * serialized into the Fragment's saved state. When the system tries to reinstantiate
 * the Fragment using the original class name, it fails with:
 *   `ClassNotFoundException: app.proguard.models.ProfileFragment`
 * or
 *   `IllegalStateException: Fragment app.proguard.models.ProfileFragment is unresolved`
 *
 * Reference: https://issuetracker.google.com/issues/37149694
 * Reference: https://stackoverflow.com/questions/15072810/proguard-issue-with-fragments
 */
class ProfileFragment : Fragment() {

    companion object {
        /** Creates an instance with a userId argument (standard Fragment factory pattern). */
        fun newInstance(userId: String): ProfileFragment {
            return ProfileFragment().apply {
                arguments = Bundle().apply {
                    putString("userId", userId)
                }
            }
        }
    }

    /** User ID passed via fragment arguments. Null if fragment was improperly recreated. */
    val userId: String? get() = arguments?.getString("userId")
}
