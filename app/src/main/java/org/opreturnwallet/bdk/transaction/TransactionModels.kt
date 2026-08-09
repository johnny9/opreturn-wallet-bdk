package org.opreturnwallet.bdk.transaction

import org.bitcoindevkit.Psbt
import org.bitcoindevkit.Transaction
import org.opreturnwallet.bdk.message.OpReturnPayload
import org.opreturnwallet.bdk.security.SensitiveModel

data class FeeSafetyLimits(
    val maximumAbsoluteFeeSats: ULong = 100_000uL,
    val maximumFeePercentOfInputs: Double = 10.0,
    val minimumFeeRateSatVb: Double = 1.0,
)

enum class PreviewOutputKind {
    OP_RETURN,
    ANCHOR,
    CHANGE,
}

data class PreviewOutput(
    val valueSats: ULong,
    val scriptHex: String,
    val kind: PreviewOutputKind,
    val address: String? = null,
) : SensitiveModel("PreviewOutput")

data class TransactionCommitment(
    val inputs: List<String>,
    val outputs: List<Pair<ULong, String>>,
    val feeSats: ULong,
) : SensitiveModel("TransactionCommitment")

data class TransactionPreview(
    val payload: OpReturnPayload,
    val mode: WriteMode,
    val feeRateSatVb: Double,
    val feeSats: ULong,
    val inputValueSats: ULong,
    val estimatedVbytes: ULong,
    val outputs: List<PreviewOutput>,
    val psbt: Psbt,
    val unsignedTransaction: Transaction,
    val commitment: TransactionCommitment,
) : SensitiveModel("TransactionPreview") {
    val feeExceedsAnchor: Boolean = when (mode) {
        is WriteMode.AnchorToSelf -> feeSats > mode.amountSats
        is WriteMode.AnchorToRecipient -> feeSats > mode.amountSats
        else -> false
    }
}

data class SweepTransactionPreview(
    val destinationAddress: String,
    val feeRateSatVb: Double,
    val feeSats: ULong,
    val inputValueSats: ULong,
    val recipientValueSats: ULong,
    val inputCount: Int,
    val estimatedVbytes: ULong,
    val psbt: Psbt,
    val unsignedTransaction: Transaction,
    val commitment: TransactionCommitment,
) : SensitiveModel("SweepTransactionPreview")

enum class FeeBumpOutputKind {
    PRESERVED,
    CHANGE,
}

data class FeeBumpOutput(
    val valueSats: ULong,
    val scriptHex: String,
    val kind: FeeBumpOutputKind,
) : SensitiveModel("FeeBumpOutput")

data class FeeBumpPreview(
    val originalTxid: String,
    val originalFeeRateSatVb: Double,
    val replacementFeeRateSatVb: Double,
    val originalFeeSats: ULong,
    val replacementFeeSats: ULong,
    val additionalFeeSats: ULong,
    val inputValueSats: ULong,
    val estimatedVbytes: ULong,
    val outputs: List<FeeBumpOutput>,
    val psbt: Psbt,
    val unsignedTransaction: Transaction,
    val commitment: TransactionCommitment,
) : SensitiveModel("FeeBumpPreview")

sealed class TransactionPolicyException(message: String) : IllegalArgumentException(message) {
    data object MissingInput : TransactionPolicyException("Transaction has no input")
    data object MissingChange : TransactionPolicyException("Transaction does not contain the required wallet change")
    data object RbfDisabled : TransactionPolicyException("Transaction does not signal opt-in RBF")
    data object OutputMismatch : TransactionPolicyException("Transaction outputs do not match the approved preview")
    data object DestinationBelongsToWallet :
        TransactionPolicyException("Sweep destination belongs to this wallet; choose an external wallet address")
    data object IncompleteSweep :
        TransactionPolicyException("Sweep does not spend every currently available wallet UTXO")
    data object TransactionNotFound :
        TransactionPolicyException("The transaction is no longer available in this wallet")
    data object TransactionNotPending :
        TransactionPolicyException("Only an unconfirmed transaction can be fee-bumped")
    data object NotBumpable :
        TransactionPolicyException("This transaction does not have replaceable wallet change")
    data object ReplacementInputsMismatch :
        TransactionPolicyException("The replacement does not spend every original input")
    data object ReplacementAddsUnconfirmedInput :
        TransactionPolicyException("The replacement adds an unconfirmed input")
    data object ReplacementFeeRateNotHigher :
        TransactionPolicyException("Replacement fee rate must be higher than the current fee rate")
    data object ReplacementFeeIncreaseTooSmall :
        TransactionPolicyException("Replacement fee increase is too small for reliable relay")
    data class FeeTooHigh(val feeSats: ULong, val maximumSats: ULong) :
        TransactionPolicyException("Fee $feeSats sats exceeds the $maximumSats sat limit")

    data class FeePercentageTooHigh(val percent: Double, val maximumPercent: Double) :
        TransactionPolicyException("Fee is %.2f%% of inputs; maximum is %.2f%%".format(percent, maximumPercent))
}
