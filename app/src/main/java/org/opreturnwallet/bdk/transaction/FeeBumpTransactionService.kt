package org.opreturnwallet.bdk.transaction

import kotlin.math.ceil
import org.bitcoindevkit.BumpFeeTxBuilder
import org.bitcoindevkit.ChainPosition
import org.bitcoindevkit.FeeRate
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Transaction
import org.bitcoindevkit.Txid
import org.bitcoindevkit.Wallet
import org.opreturnwallet.bdk.message.toHex

class FeeBumpTransactionService {
    fun buildPreview(
        wallet: Wallet,
        persister: Persister,
        originalTxid: String,
        requestedFeeRateSatVb: Double,
        limits: FeeSafetyLimits = FeeSafetyLimits(),
    ): FeeBumpPreview {
        val txid = Txid.fromString(originalTxid)
        val canonical = wallet.getTx(txid) ?: throw TransactionPolicyException.TransactionNotFound
        if (canonical.chainPosition !is ChainPosition.Unconfirmed) {
            throw TransactionPolicyException.TransactionNotPending
        }

        val original = canonical.transaction
        if (!original.isExplicitlyRbf()) throw TransactionPolicyException.RbfDisabled
        if (original.input().isEmpty()) throw TransactionPolicyException.MissingInput

        val originalFeeSats = runCatching { wallet.calculateFee(original).toSat() }
            .getOrElse { throw TransactionPolicyException.NotBumpable }
        val originalVbytes = original.vsize().coerceAtLeast(1uL)
        val originalFeeRateSatVb = originalFeeSats.toDouble() / originalVbytes.toDouble()
        val effectiveRate = maxOf(requestedFeeRateSatVb, limits.minimumFeeRateSatVb)
        if (!effectiveRate.isFinite() || effectiveRate <= originalFeeRateSatVb) {
            throw TransactionPolicyException.ReplacementFeeRateNotHigher
        }

        val originalInputs = original.input().map(::inputKey).toSet()
        val originalOutputs = classifyOutputs(wallet, original)
        val originalHasChange = originalOutputs.any { it.kind == FeeBumpOutputKind.CHANGE }
        val originalPreservedOutputs = preservedOutputCommitment(originalOutputs)

        val psbt = try {
            BumpFeeTxBuilder(
                txid = txid,
                feeRate = feeRate(effectiveRate),
            )
                .setExactSequence(RBF_SEQUENCE)
                .finish(wallet)
        } finally {
            wallet.persist(persister)
        }

        val replacement = psbt.extractTx()
        if (!replacement.isExplicitlyRbf()) throw TransactionPolicyException.RbfDisabled
        val replacementFeeSats = psbt.fee()
        val replacementVbytes = replacement.vsize().coerceAtLeast(1uL)
        val replacementFeeRateSatVb = replacementFeeSats.toDouble() / replacementVbytes.toDouble()
        val replacementInputs = replacement.input().map(::inputKey)
        val replacementOutputs = classifyOutputs(wallet, replacement)
        val replacementPreservedOutputs = preservedOutputCommitment(replacementOutputs)
        val addedInputsAreConfirmed = replacement.input()
            .filterNot { inputKey(it) in originalInputs }
            .all { input ->
                wallet.getUtxo(input.previousOutput)?.chainPosition is ChainPosition.Confirmed
            }

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

        val outputTotal = replacement.output().fold(0uL) { total, output ->
            total + output.value.toSat()
        }
        val inputValueSats = outputTotal + replacementFeeSats
        FeeSafetyPolicy.enforce(replacementFeeSats, inputValueSats, limits)
        val commitment = commitment(replacement, replacementFeeSats)

        return FeeBumpPreview(
            originalTxid = originalTxid,
            originalFeeRateSatVb = originalFeeRateSatVb,
            replacementFeeRateSatVb = replacementFeeRateSatVb,
            originalFeeSats = originalFeeSats,
            replacementFeeSats = replacementFeeSats,
            additionalFeeSats = replacementFeeSats - originalFeeSats,
            inputValueSats = inputValueSats,
            estimatedVbytes = replacementVbytes,
            outputs = replacementOutputs,
            psbt = psbt,
            unsignedTransaction = replacement,
            commitment = commitment,
        )
    }

    fun signApproved(wallet: Wallet, preview: FeeBumpPreview): Transaction {
        check(wallet.sign(preview.psbt)) { "Wallet could not finalize the replacement PSBT" }
        val signedTransaction = preview.psbt.extractTx()
        validateApprovedTransaction(signedTransaction, preview.commitment)
        return signedTransaction
    }

    fun validateApprovedTransaction(
        transaction: Transaction,
        approved: TransactionCommitment,
    ) {
        val actual = commitment(transaction, approved.feeSats)
        if (actual != approved) throw TransactionPolicyException.OutputMismatch
    }

    private fun classifyOutputs(wallet: Wallet, transaction: Transaction): List<FeeBumpOutput> =
        transaction.output().map { output ->
            val script = output.scriptPubkey
            val isInternalChange = wallet.derivationOfSpk(script)?.keychain == KeychainKind.INTERNAL
            FeeBumpOutput(
                valueSats = output.value.toSat(),
                scriptHex = script.toBytes().toHex(),
                kind = if (isInternalChange) FeeBumpOutputKind.CHANGE else FeeBumpOutputKind.PRESERVED,
            )
        }

    private fun preservedOutputCommitment(outputs: List<FeeBumpOutput>): List<Pair<ULong, String>> =
        outputs.filter { it.kind == FeeBumpOutputKind.PRESERVED }
            .map { it.valueSats to it.scriptHex }

    private fun commitment(transaction: Transaction, feeSats: ULong): TransactionCommitment =
        TransactionCommitment(
            inputs = transaction.input().map(::inputKey),
            outputs = transaction.output().map {
                it.value.toSat() to it.scriptPubkey.toBytes().toHex()
            },
            feeSats = feeSats,
        )

    private fun inputKey(input: org.bitcoindevkit.TxIn): String =
        "${input.previousOutput.txid}:${input.previousOutput.vout}"

    private fun feeRate(satPerVbyte: Double): FeeRate {
        require(satPerVbyte.isFinite() && satPerVbyte >= 1.0) {
            "Fee rate must be at least 1 sat/vB"
        }
        return FeeRate.fromSatPerKwu(ceil(satPerVbyte * VBYTES_PER_KWU).toULong())
    }

    private companion object {
        const val RBF_SEQUENCE: UInt = 0xfffffffdu
        const val VBYTES_PER_KWU = 250.0
    }
}
