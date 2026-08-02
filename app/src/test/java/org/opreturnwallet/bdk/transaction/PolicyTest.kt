package org.opreturnwallet.bdk.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PolicyTest {
    @Test
    fun `default p2wpkh dust threshold is 294 sats`() {
        val p2wpkh = byteArrayOf(0x00, 0x14) + ByteArray(20)
        assertEquals(294uL, DustPolicy.minimumSats(p2wpkh))
        assertThrows(IllegalArgumentException::class.java) {
            DustPolicy.requireNotDust(293uL, p2wpkh)
        }
    }

    @Test
    fun `default p2tr dust threshold is 330 sats`() {
        val p2tr = byteArrayOf(0x51, 0x20) + ByteArray(32)
        assertEquals(330uL, DustPolicy.minimumSats(p2tr))
    }

    @Test
    fun `excessive absolute fee is rejected`() {
        assertThrows(TransactionPolicyException.FeeTooHigh::class.java) {
            FeeSafetyPolicy.enforce(
                feeSats = 100_001uL,
                inputValueSats = 1_000_000uL,
                limits = FeeSafetyLimits(maximumAbsoluteFeeSats = 100_000uL),
            )
        }
    }

    @Test
    fun `excessive fee percentage is rejected`() {
        assertThrows(TransactionPolicyException.FeePercentageTooHigh::class.java) {
            FeeSafetyPolicy.enforce(
                feeSats = 101uL,
                inputValueSats = 1_000uL,
                limits = FeeSafetyLimits(maximumFeePercentOfInputs = 10.0),
            )
        }
    }
}
