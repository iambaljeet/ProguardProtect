package lib.proguardprotect

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * DSL extension for configuring the ProguardProtect Gradle plugin.
 *
 * ## Usage in build.gradle.kts
 * ```kotlin
 * proguardProtect {
 *     failOnError.set(true)          // Fail build on detected issues
 *     enabled.set(true)              // Enable/disable the plugin
 *     targetPackages.set(listOf("com.example"))  // Limit scan scope
 *     extraRulesFiles.set(listOf("custom-rules.pro"))  // Additional rules files
 * }
 * ```
 */
interface ProguardProtectExtension {
    /** Whether to fail the build when confirmed issues are found. Default: true. */
    val failOnError: Property<Boolean>

    /** Whether the plugin is enabled. Default: true. */
    val enabled: Property<Boolean>

    /**
     * Extra ProGuard rules files to consider (in addition to auto-detected ones).
     * These are used for reference in reports only — detection is post-build.
     */
    val extraRulesFiles: ListProperty<String>

    /** Package prefixes to limit scanning scope (empty = scan all source files). */
    val targetPackages: ListProperty<String>
}
