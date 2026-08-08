package org.opreturnwallet.bdk.security

/**
 * Prevents Kotlin data classes that contain wallet-private data from generating a value-bearing
 * [toString]. This is defense in depth: production source must not log these objects at all.
 */
abstract class SensitiveModel protected constructor(
    private val modelName: String,
) {
    final override fun toString(): String = "$modelName([REDACTED])"
}
