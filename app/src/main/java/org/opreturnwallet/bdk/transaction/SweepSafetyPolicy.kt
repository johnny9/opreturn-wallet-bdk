package org.opreturnwallet.bdk.transaction

object SweepSafetyPolicy {
    fun enforce(
        expectedInputs: Set<String>,
        actualInputs: List<String>,
        outputCount: Int,
        inputValueSats: ULong,
        recipientValueSats: ULong,
        feeSats: ULong,
        destinationBelongsToWallet: Boolean,
    ) {
        if (expectedInputs.isEmpty()) throw TransactionPolicyException.MissingInput
        if (actualInputs.size != expectedInputs.size || actualInputs.toSet() != expectedInputs) {
            throw TransactionPolicyException.IncompleteSweep
        }
        if (outputCount != 1) throw TransactionPolicyException.OutputMismatch
        if (destinationBelongsToWallet) {
            throw TransactionPolicyException.DestinationBelongsToWallet
        }
        if (feeSats > inputValueSats || recipientValueSats != inputValueSats - feeSats) {
            throw TransactionPolicyException.IncompleteSweep
        }
    }
}
