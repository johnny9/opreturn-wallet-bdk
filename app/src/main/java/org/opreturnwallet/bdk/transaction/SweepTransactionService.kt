package org.opreturnwallet.bdk.transaction

import kotlin.math.ceil
import org.bitcoindevkit.Address
import org.bitcoindevkit.FeeRate
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Transaction
import org.bitcoindevkit.TxBuilder
import org.bitcoindevkit.Wallet
import org.opreturnwallet.bdk.message.toHex
import org.opreturnwallet.bdk.wallet.WalletNetwork

class SweepTransactionService {
    fun buildPreview(
        wallet: Wallet,
        persister: Persister,
        network: WalletNetwork,
        destination: String,
        requestedFeeRateSatVb: Double,
        limits: FeeSafetyLimits = FeeSafetyLimits(),
    ): SweepTransactionPreview {
        val address = Address(destination.trim(), network.bdkNetwork)
        val destinationScript = address.scriptPubkey()
        if (wallet.isMine(destinationScript)) {
            throw TransactionPolicyException.DestinationBelongsToWallet
        }

        val spendable = wallet.listUnspent().filterNot { it.isSpent }
        if (spendable.isEmpty()) throw TransactionPolicyException.MissingInput
        val expectedInputs = spendable.map { outPointKey(it.outpoint.txid.toString(), it.outpoint.vout) }.toSet()
        val expectedInputValue = spendable.fold(0uL) { total, output -> total + output.txout.value.toSat() }
        val effectiveRate = maxOf(requestedFeeRateSatVb, limits.minimumFeeRateSatVb)

        val psbt = try {
            TxBuilder()
                .drainWallet()
                .drainTo(destinationScript)
                .feeRate(feeRate(effectiveRate))
                .setExactSequence(RBF_SEQUENCE)
                .finish(wallet)
        } finally {
            wallet.persist(persister)
        }

        val transaction = psbt.extractTx()
        val feeSats = psbt.fee()
        val output = transaction.output().singleOrNull()
            ?: throw TransactionPolicyException.OutputMismatch
        val actualInputs = transaction.input().map {
            outPointKey(it.previousOutput.txid.toString(), it.previousOutput.vout)
        }
        if (!output.scriptPubkey.toBytes().contentEquals(destinationScript.toBytes())) {
            throw TransactionPolicyException.OutputMismatch
        }
        if (wallet.isMine(output.scriptPubkey)) {
            throw TransactionPolicyException.DestinationBelongsToWallet
        }

        val recipientSats = output.value.toSat()
        DustPolicy.requireNotDust(recipientSats, destinationScript.toBytes())
        SweepSafetyPolicy.enforce(
            expectedInputs = expectedInputs,
            actualInputs = actualInputs,
            outputCount = transaction.output().size,
            inputValueSats = expectedInputValue,
            recipientValueSats = recipientSats,
            feeSats = feeSats,
            destinationBelongsToWallet = wallet.isMine(output.scriptPubkey),
        )
        FeeSafetyPolicy.enforce(feeSats, expectedInputValue, limits)
        if (!transaction.isExplicitlyRbf()) throw TransactionPolicyException.RbfDisabled

        val actualFeeRate = feeSats.toDouble() / transaction.vsize().coerceAtLeast(1uL).toDouble()
        return SweepTransactionPreview(
            destinationAddress = address.toString(),
            feeRateSatVb = actualFeeRate,
            feeSats = feeSats,
            inputValueSats = expectedInputValue,
            recipientValueSats = recipientSats,
            inputCount = transaction.input().size,
            estimatedVbytes = transaction.vsize(),
            psbt = psbt,
            unsignedTransaction = transaction,
            commitment = commitment(transaction, feeSats),
        )
    }

    fun signApproved(wallet: Wallet, preview: SweepTransactionPreview): Transaction {
        check(wallet.sign(preview.psbt)) { "Wallet could not finalize the sweep PSBT" }
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

    private fun commitment(transaction: Transaction, feeSats: ULong): TransactionCommitment =
        TransactionCommitment(
            inputs = transaction.input().map {
                outPointKey(it.previousOutput.txid.toString(), it.previousOutput.vout)
            },
            outputs = transaction.output().map {
                it.value.toSat() to it.scriptPubkey.toBytes().toHex()
            },
            feeSats = feeSats,
        )

    private fun feeRate(satPerVbyte: Double): FeeRate {
        require(satPerVbyte.isFinite() && satPerVbyte >= 1.0) {
            "Fee rate must be at least 1 sat/vB"
        }
        return FeeRate.fromSatPerKwu(ceil(satPerVbyte * VBYTES_PER_KWU).toULong())
    }

    private fun outPointKey(txid: String, vout: UInt): String = "$txid:$vout"

    private companion object {
        const val RBF_SEQUENCE: UInt = 0xfffffffdu
        const val VBYTES_PER_KWU = 250.0
    }
}
