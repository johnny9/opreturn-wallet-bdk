package org.opreturnwallet.bdk.transaction

/** Mirrors Bitcoin Core's default 3 sat/vB dust threshold calculation. */
object DustPolicy {
    private const val DUST_RELAY_MULTIPLIER = 3uL
    private const val WITNESS_INPUT_SIZE_VBYTES = 67uL
    private const val LEGACY_INPUT_SIZE_BYTES = 148uL

    fun minimumSats(scriptPubKey: ByteArray): ULong {
        if (scriptPubKey.firstOrNull()?.toInt()?.and(0xff) == 0x6a) return 0uL
        val outputSize = 8uL + compactSizeLength(scriptPubKey.size) + scriptPubKey.size.toULong()
        val spendSize = if (isWitnessProgram(scriptPubKey)) WITNESS_INPUT_SIZE_VBYTES else LEGACY_INPUT_SIZE_BYTES
        return (outputSize + spendSize) * DUST_RELAY_MULTIPLIER
    }

    fun requireNotDust(amountSats: ULong, scriptPubKey: ByteArray) {
        val minimum = minimumSats(scriptPubKey)
        require(amountSats >= minimum) {
            "Anchor amount $amountSats sats is below the $minimum sat dust threshold"
        }
    }

    private fun isWitnessProgram(script: ByteArray): Boolean {
        if (script.size !in 4..42) return false
        val version = script[0].toInt() and 0xff
        val programLength = script[1].toInt() and 0xff
        return (version == 0x00 || version in 0x51..0x60) && programLength + 2 == script.size
    }

    private fun compactSizeLength(size: Int): ULong = when {
        size < 253 -> 1uL
        size <= 0xffff -> 3uL
        else -> 5uL
    }
}
