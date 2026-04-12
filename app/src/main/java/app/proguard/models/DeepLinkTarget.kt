package app.proguard.models

/**
 * Holds deep-link navigation targets that reference Activity class names as strings.
 *
 * This class is involved in Crash Type 21 (COMPONENT_CLASS_NOT_FOUND).
 * The fully-qualified class name is stored as a string and used in ComponentName()
 * or Intent.setClassName() for navigation. If R8 renames the target Activity,
 * the stored string becomes stale → ActivityNotFoundException at runtime.
 *
 * Reference: https://stackoverflow.com/questions/36829329/proguard-activitynotfoundexception
 * Reference: https://medium.com/@tokudu/how-to-whitelist-unnamed-java-classes-in-proguard-1f3c80f2ce34
 */
data class DeepLinkTarget(
    val label: String,
    val activityClassName: String
)
