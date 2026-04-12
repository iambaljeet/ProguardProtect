package app.proguard.models

/**
 * Feature theme configuration used to load dynamic drawable resources by name.
 *
 * This class is involved in Crash Type 19 (RESOURCE_SHRUNK_BY_NAME).
 * It constructs resource names dynamically from a theme prefix, so aapt2's
 * resource shrinker cannot statically determine which drawables to keep.
 *
 * With shrinkResources + strict mode, any drawable not directly referenced via
 * R.drawable.xxx will be removed, causing Resources$NotFoundException at runtime.
 *
 * Reference: https://developer.android.com/build/shrink-code#keep-resources
 */
data class FeatureTheme(
    val name: String,
    val bannerResourceName: String = "promo_banner"
)
