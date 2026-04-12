package app.proguard.models

/**
 * A helper class that is ONLY accessed via Class.forName() reflection.
 * Without ProGuard keep rules, R8 will rename or remove this class,
 * causing ClassNotFoundException at runtime.
 */
class UserProfile {
    var name: String = "Default User"
    var email: String = "user@example.com"
    var age: Int = 25

    fun getDisplayInfo(): String {
        return "$name ($email), age $age"
    }
}
