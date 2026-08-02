package org.opreturnwallet.bdk.transaction

object FeeSafetyPolicy {
    fun enforce(
        feeSats: ULong,
        inputValueSats: ULong,
        limits: FeeSafetyLimits,
    ) {
        if (feeSats > limits.maximumAbsoluteFeeSats) {
            throw TransactionPolicyException.FeeTooHigh(feeSats, limits.maximumAbsoluteFeeSats)
        }
        val percent = if (inputValueSats == 0uL) {
            100.0
        } else {
            feeSats.toDouble() * 100.0 / inputValueSats.toDouble()
        }
        if (percent > limits.maximumFeePercentOfInputs) {
            throw TransactionPolicyException.FeePercentageTooHigh(percent, limits.maximumFeePercentOfInputs)
        }
    }
}
