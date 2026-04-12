package app.proguard.crashes

import android.util.Log
import kotlin.reflect.full.*

/**
 * Crash Type 11: Sealed Class Subtypes Stripped by R8
 *
 * Reference: https://issuetracker.google.com/issues/367205689
 * Reference: https://www.codestudy.net/blog/using-kotlin-reflection-sealedsubclasses
 *
 * `CheckoutState::class.sealedSubclasses` uses Kotlin metadata to enumerate subtypes.
 * R8 removes subtypes that aren't directly referenced in non-reflective code.
 * Result: sealedSubclasses returns empty list → NullPointerException downstream.
 *
 * Common pattern: state machine registries, UI state dispatchers, serializers
 * that enumerate all sealed subtypes via reflection.
 *
 * Expected crash: IllegalStateException "Expected 5 checkout states, got 0"
 */
object SealedClassCrash {

    private const val TAG = "SealedClassCrash"

    fun trigger(): String {
        // Use sealedSubclasses to discover all possible checkout states
        // This is a common pattern for building state machine registries
        val allStates = app.proguard.models.CheckoutState::class.sealedSubclasses

        Log.d(TAG, "Found ${allStates.size} sealed subtypes: ${allStates.map { it.simpleName }}")

        check(allStates.size == 5) {
            "Expected 5 CheckoutState subtypes (Empty, CartFilled, Processing, OrderPlaced, Failed), " +
            "but got ${allStates.size}: ${allStates.map { it.simpleName }}. " +
            "R8 removed ${5 - allStates.size} sealed subtypes from the DEX — they weren't directly referenced."
        }

        // Try to create instances of object subtypes via reflection
        val stateNames = allStates.mapNotNull { it.simpleName }
        return "✅ Sealed: found ${allStates.size} CheckoutState subtypes: ${stateNames.joinToString(", ")}"
    }
}
