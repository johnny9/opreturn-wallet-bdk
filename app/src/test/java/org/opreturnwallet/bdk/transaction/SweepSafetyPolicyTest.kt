package org.opreturnwallet.bdk.transaction

import org.junit.Assert.assertThrows
import org.junit.Test

class SweepSafetyPolicyTest {
    @Test
    fun `valid sweep spends every input to recipient plus fee`() {
        SweepSafetyPolicy.enforce(
            expectedInputs = setOf("a:0", "b:1"),
            actualInputs = listOf("a:0", "b:1"),
            outputCount = 1,
            inputValueSats = 20_000uL,
            recipientValueSats = 19_800uL,
            feeSats = 200uL,
            destinationBelongsToWallet = false,
        )
    }

    @Test
    fun `missing wallet input is rejected`() {
        assertThrows(TransactionPolicyException.IncompleteSweep::class.java) {
            SweepSafetyPolicy.enforce(
                expectedInputs = setOf("a:0", "b:1"),
                actualInputs = listOf("a:0"),
                outputCount = 1,
                inputValueSats = 20_000uL,
                recipientValueSats = 19_800uL,
                feeSats = 200uL,
                destinationBelongsToWallet = false,
            )
        }
    }

    @Test
    fun `change output is rejected`() {
        assertThrows(TransactionPolicyException.OutputMismatch::class.java) {
            SweepSafetyPolicy.enforce(
                expectedInputs = setOf("a:0"),
                actualInputs = listOf("a:0"),
                outputCount = 2,
                inputValueSats = 20_000uL,
                recipientValueSats = 19_800uL,
                feeSats = 200uL,
                destinationBelongsToWallet = false,
            )
        }
    }

    @Test
    fun `wallet owned destination is rejected`() {
        assertThrows(TransactionPolicyException.DestinationBelongsToWallet::class.java) {
            SweepSafetyPolicy.enforce(
                expectedInputs = setOf("a:0"),
                actualInputs = listOf("a:0"),
                outputCount = 1,
                inputValueSats = 20_000uL,
                recipientValueSats = 19_800uL,
                feeSats = 200uL,
                destinationBelongsToWallet = true,
            )
        }
    }

    @Test
    fun `incorrect recipient accounting is rejected`() {
        assertThrows(TransactionPolicyException.IncompleteSweep::class.java) {
            SweepSafetyPolicy.enforce(
                expectedInputs = setOf("a:0"),
                actualInputs = listOf("a:0"),
                outputCount = 1,
                inputValueSats = 20_000uL,
                recipientValueSats = 19_799uL,
                feeSats = 200uL,
                destinationBelongsToWallet = false,
            )
        }
    }
}
