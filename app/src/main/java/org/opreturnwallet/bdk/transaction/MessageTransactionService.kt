package org.opreturnwallet.bdk.transaction

import kotlin.math.ceil
import org.bitcoindevkit.Address
import org.bitcoindevkit.Amount
import org.bitcoindevkit.FeeRate
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Script
import org.bitcoindevkit.Transaction
import org.bitcoindevkit.TxBuilder
import org.bitcoindevkit.Wallet
import org.opreturnwallet.bdk.message.OpReturnPayload
import org.opreturnwallet.bdk.message.OpReturnScript
import org.opreturnwallet.bdk.message.toHex
import org.opreturnwallet.bdk.wallet.WalletNetwork

class MessageTransactionService(
    private val debugConsumeEnabled: Boolean,
) {
    fun buildPreview(
        wallet: Wallet,
        persister: Persister,
        network: WalletNetwork,
        payload: OpReturnPayload,
        requestedFeeRateSatVb: Double,
        mode: WriteMode,
        limits: FeeSafetyLimits = FeeSafetyLimits(),
    ): TransactionPreview {
        require(payload.utf8Bytes.isNotEmpty()) { "Message must not be empty" }
        require(payload.utf8Bytes.size <= 80) { "Message exceeds 80 UTF-8 bytes" }

        val anchor = resolveAnchor(wallet, network, mode)
        if (mode is WriteMode.AnchorToSelf) {
            // The fresh external anchor address was revealed before later policy checks.
            wallet.persist(persister)
        }
        var builder = TxBuilder()
            .addData(payload.utf8Bytes)
            .setExactSequence(RBF_SEQUENCE)

        if (anchor != null) {
            DustPolicy.requireNotDust(anchor.amountSats, anchor.script.toBytes())
            builder = builder.addRecipient(anchor.script, Amount.fromSat(anchor.amountSats))
        }

        val effectiveRequestedRate = maxOf(requestedFeeRateSatVb, limits.minimumFeeRateSatVb)
        builder = when (mode) {
            is WriteMode.DebugConsumeSelectedUtxo -> {
                require(debugConsumeEnabled && network == WalletNetwork.REGTEST) {
                    "Consume-selected-UTXO mode is only available in debug Regtest builds"
                }
                require(mode.inputValueSats <= limits.maximumAbsoluteFeeSats) {
                    "Selected UTXO exceeds the debug maximum fee"
                }
                builder
                    .addUtxo(mode.outPoint)
                    .manuallySelectedOnly()
                    .feeAbsolute(Amount.fromSat(mode.inputValueSats))
            }

            else -> builder.feeRate(feeRate(effectiveRequestedRate))
        }

        val psbt = try {
            builder.finish(wallet)
        } finally {
            // Address revelation and successful transaction construction both stage wallet changes.
            // Persist even when construction fails so a revealed address is never accidentally reused.
            wallet.persist(persister)
        }
        val transaction = psbt.extractTx()
        val feeSats = psbt.fee()
        val outputTotal = transaction.output().fold(0uL) { sum, output -> sum + output.value.toSat() }
        val inputTotal = outputTotal + feeSats
        val effectiveLimits = if (mode is WriteMode.DebugConsumeSelectedUtxo) {
            limits.copy(maximumFeePercentOfInputs = 100.0)
        } else {
            limits
        }
        FeeSafetyPolicy.enforce(feeSats, inputTotal, effectiveLimits)

        if (!transaction.isExplicitlyRbf()) throw TransactionPolicyException.RbfDisabled
        if (transaction.input().isEmpty()) throw TransactionPolicyException.MissingInput

        val outputs = classifyAndValidateOutputs(
            wallet = wallet,
            transaction = transaction,
            payload = payload,
            mode = mode,
            anchor = anchor,
        )
        val actualFeeRate = feeSats.toDouble() / transaction.vsize().coerceAtLeast(1uL).toDouble()
        val commitment = commitment(transaction, feeSats)

        return TransactionPreview(
            payload = payload,
            mode = mode,
            feeRateSatVb = if (mode is WriteMode.DebugConsumeSelectedUtxo) actualFeeRate else effectiveRequestedRate,
            feeSats = feeSats,
            inputValueSats = inputTotal,
            estimatedVbytes = transaction.vsize(),
            outputs = outputs,
            psbt = psbt,
            unsignedTransaction = transaction,
            commitment = commitment,
        )
    }

    fun signApproved(wallet: Wallet, preview: TransactionPreview): Transaction {
        check(wallet.sign(preview.psbt)) { "Wallet could not finalize the PSBT" }
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

    private fun resolveAnchor(
        wallet: Wallet,
        network: WalletNetwork,
        mode: WriteMode,
    ): ResolvedAnchor? = when (mode) {
        WriteMode.StandardMessage,
        is WriteMode.DebugConsumeSelectedUtxo,
        -> null

        is WriteMode.AnchorToSelf -> {
            val address = wallet.revealNextAddress(KeychainKind.EXTERNAL).address
            ResolvedAnchor(
                script = address.scriptPubkey(),
                amountSats = mode.amountSats,
                address = address.toString(),
            )
        }

        is WriteMode.AnchorToRecipient -> {
            val address = Address(mode.address.trim(), network.bdkNetwork)
            ResolvedAnchor(
                script = address.scriptPubkey(),
                amountSats = mode.amountSats,
                address = address.toString(),
            )
        }
    }

    private fun classifyAndValidateOutputs(
        wallet: Wallet,
        transaction: Transaction,
        payload: OpReturnPayload,
        mode: WriteMode,
        anchor: ResolvedAnchor?,
    ): List<PreviewOutput> {
        var opReturnCount = 0
        var anchorCount = 0
        var changeCount = 0

        val outputs = transaction.output().map { output ->
            val script = output.scriptPubkey
            val scriptBytes = script.toBytes()
            val value = output.value.toSat()
            val decoded = OpReturnScript.decode(scriptBytes)

            when {
                decoded != null -> {
                    require(value == 0uL) { "OP_RETURN output must have zero value" }
                    require(decoded.contentEquals(payload.utf8Bytes)) { "OP_RETURN payload differs from preview" }
                    opReturnCount++
                    PreviewOutput(value, scriptBytes.toHex(), PreviewOutputKind.OP_RETURN)
                }

                anchor != null &&
                    scriptBytes.contentEquals(anchor.script.toBytes()) &&
                    value == anchor.amountSats -> {
                    anchorCount++
                    PreviewOutput(value, scriptBytes.toHex(), PreviewOutputKind.ANCHOR, anchor.address)
                }

                wallet.isMine(script) -> {
                    changeCount++
                    PreviewOutput(value, scriptBytes.toHex(), PreviewOutputKind.CHANGE)
                }

                else -> throw TransactionPolicyException.OutputMismatch
            }
        }

        require(opReturnCount == 1) { "Transaction must contain exactly one OP_RETURN output" }
        when (mode) {
            WriteMode.StandardMessage -> {
                require(anchorCount == 0)
                if (changeCount == 0) throw TransactionPolicyException.MissingChange
            }
            is WriteMode.AnchorToSelf,
            is WriteMode.AnchorToRecipient,
            -> {
                require(anchorCount == 1) { "Transaction must contain exactly one anchor output" }
                if (changeCount == 0) throw TransactionPolicyException.MissingChange
            }
            is WriteMode.DebugConsumeSelectedUtxo -> {
                require(outputs.size == 1) { "Debug consume mode must not create change" }
            }
        }
        return outputs
    }

    private fun commitment(transaction: Transaction, feeSats: ULong): TransactionCommitment =
        TransactionCommitment(
            inputs = transaction.input().map { "${it.previousOutput.txid}:${it.previousOutput.vout}" },
            outputs = transaction.output().map { it.value.toSat() to it.scriptPubkey.toBytes().toHex() },
            feeSats = feeSats,
        )

    private fun feeRate(satPerVbyte: Double): FeeRate {
        require(satPerVbyte.isFinite() && satPerVbyte >= 1.0) { "Fee rate must be at least 1 sat/vB" }
        return FeeRate.fromSatPerKwu(ceil(satPerVbyte * VBYTES_PER_KWU).toULong())
    }

    private data class ResolvedAnchor(
        val script: Script,
        val amountSats: ULong,
        val address: String,
    )

    private companion object {
        const val RBF_SEQUENCE: UInt = 0xfffffffdu
        const val VBYTES_PER_KWU = 250.0
    }
}
