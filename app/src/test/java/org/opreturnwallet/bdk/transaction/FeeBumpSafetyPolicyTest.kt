package org.opreturnwallet.bdk.transaction

import org.junit.Assert.assertThrows
import org.junit.Test

class FeeBumpSafetyPolicyTest {
    @Test
    fun `valid replacement preserves outputs and increases fee`() {
        enforce()
    }

    @Test
    fun `replacement must retain every original input`() {
        assertThrows(TransactionPolicyException.ReplacementInputsMismatch::class.java) {
            enforce(replacementInputs = listOf("b:1"))
        }
    }

    @Test
    fun `replacement must preserve non-change outputs exactly`() {
        assertThrows(TransactionPolicyException.OutputMismatch::class.java) {
            enforce(replacementPreservedOutputs = listOf(1uL to "6a"))
        }
    }

    @Test
    fun `transaction without wallet change cannot be bumped`() {
        assertThrows(TransactionPolicyException.NotBumpable::class.java) {
            enforce(originalHasChange = false)
        }
    }

    @Test
    fun `added unconfirmed input is rejected`() {
        assertThrows(TransactionPolicyException.ReplacementAddsUnconfirmedInput::class.java) {
            enforce(
                replacementInputs = listOf("a:0", "b:1"),
                addedInputsAreConfirmed = false,
            )
        }
    }

    @Test
    fun `replacement fee rate must be higher`() {
        assertThrows(TransactionPolicyException.ReplacementFeeRateNotHigher::class.java) {
            enforce(replacementFeeRateSatVb = 1.0)
        }
    }

    @Test
    fun `non-finite replacement fee rate is rejected`() {
        assertThrows(TransactionPolicyException.ReplacementFeeRateNotHigher::class.java) {
            enforce(replacementFeeRateSatVb = Double.NaN)
        }
    }

    @Test
    fun `replacement fee must increase by at least one sat per replacement vbyte`() {
        assertThrows(TransactionPolicyException.ReplacementFeeIncreaseTooSmall::class.java) {
            enforce(replacementFeeSats = 299uL, replacementVbytes = 100uL)
        }
    }

    private fun enforce(
        originalInputs: Set<String> = setOf("a:0"),
        replacementInputs: List<String> = listOf("a:0"),
        originalPreservedOutputs: List<Pair<ULong, String>> = listOf(0uL to "6a01ff"),
        replacementPreservedOutputs: List<Pair<ULong, String>> = originalPreservedOutputs,
        originalHasChange: Boolean = true,
        addedInputsAreConfirmed: Boolean = true,
        originalFeeSats: ULong = 200uL,
        replacementFeeSats: ULong = 300uL,
        originalFeeRateSatVb: Double = 1.0,
        replacementFeeRateSatVb: Double = 2.0,
        replacementVbytes: ULong = 100uL,
    ) {
        FeeBumpSafetyPolicy.enforce(
            originalInputs = originalInputs,
            replacementInputs = replacementInputs,
            originalPreservedOutputs = originalPreservedOutputs,
            replacementPreservedOutputs = replacementPreservedOutputs,
            originalHasChange = originalHasChange,
            addedInputsAreConfirmed = addedInputsAreConfirmed,
            originalFeeSats = originalFeeSats,
            replacementFeeSats = replacementFeeSats,
            originalFeeRateSatVb = originalFeeRateSatVb,
            replacementFeeRateSatVb = replacementFeeRateSatVb,
            replacementVbytes = replacementVbytes,
        )
    }
}
