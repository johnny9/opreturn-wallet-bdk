package org.opreturnwallet.bdk.transaction

object FeeBumpSafetyPolicy {
    fun enforce(
        originalInputs: Set<String>,
        replacementInputs: List<String>,
        originalPreservedOutputs: List<Pair<ULong, String>>,
        replacementPreservedOutputs: List<Pair<ULong, String>>,
        originalHasChange: Boolean,
        addedInputsAreConfirmed: Boolean,
        originalFeeSats: ULong,
        replacementFeeSats: ULong,
        originalFeeRateSatVb: Double,
        replacementFeeRateSatVb: Double,
        replacementVbytes: ULong,
    ) {
        if (originalInputs.isEmpty() || !replacementInputs.toSet().containsAll(originalInputs)) {
            throw TransactionPolicyException.ReplacementInputsMismatch
        }
        if (multiset(originalPreservedOutputs) != multiset(replacementPreservedOutputs)) {
            throw TransactionPolicyException.OutputMismatch
        }
        if (!originalHasChange) throw TransactionPolicyException.NotBumpable
        if (!addedInputsAreConfirmed) {
            throw TransactionPolicyException.ReplacementAddsUnconfirmedInput
        }
        if (!replacementFeeRateSatVb.isFinite() ||
            replacementFeeRateSatVb <= originalFeeRateSatVb
        ) {
            throw TransactionPolicyException.ReplacementFeeRateNotHigher
        }
        if (replacementFeeSats <= originalFeeSats ||
            replacementFeeSats - originalFeeSats < replacementVbytes
        ) {
            throw TransactionPolicyException.ReplacementFeeIncreaseTooSmall
        }
    }

    private fun multiset(values: List<Pair<ULong, String>>): Map<Pair<ULong, String>, Int> =
        values.groupingBy { it }.eachCount()
}
