package org.opreturnwallet.bdk.wallet

import org.bitcoindevkit.Persister
import org.bitcoindevkit.OutPoint
import org.bitcoindevkit.Wallet
import org.opreturnwallet.bdk.security.SensitiveModel

data class WalletSession(
    val id: String,
    val network: WalletNetwork,
    val wallet: Wallet,
    val persister: Persister,
) : SensitiveModel("WalletSession")

data class CreatedWallet(
    val session: WalletSession,
    val recoveryPhrase: String,
) : SensitiveModel("CreatedWallet")

data class WalletBalance(
    val confirmedSats: ULong,
    val pendingSats: ULong,
    val totalSats: ULong,
) : SensitiveModel("WalletBalance")

data class MessageTransactionRecord(
    val txid: String,
    val message: String,
    val payloadHex: String,
    val pending: Boolean,
    val blockHeight: UInt?,
    val feeSats: ULong?,
    val feeRateSatVb: Double? = null,
    val rbfEligible: Boolean = false,
) : SensitiveModel("MessageTransactionRecord")

data class SweepTransactionRecord(
    val txid: String,
    val destinationAddress: String,
    val amountSats: ULong,
    val feeSats: ULong,
    val pending: Boolean,
    val blockHeight: UInt?,
) : SensitiveModel("SweepTransactionRecord")

data class FeeBumpTransactionRecord(
    val originalTxid: String,
    val replacementTxid: String,
    val originalFeeSats: ULong,
    val replacementFeeSats: ULong,
    val replacementFeeRateSatVb: Double,
    val pending: Boolean,
    val blockHeight: UInt?,
) : SensitiveModel("FeeBumpTransactionRecord")

data class WalletTransactionStatus(
    val pending: Boolean,
    val blockHeight: UInt?,
)

data class WalletSnapshot(
    val network: WalletNetwork,
    val balance: WalletBalance,
    val receiveAddress: String,
    val messages: List<MessageTransactionRecord>,
    val transactionStatuses: Map<String, WalletTransactionStatus> = emptyMap(),
) : SensitiveModel("WalletSnapshot")

data class SpendableUtxo(
    val outPoint: OutPoint,
    val valueSats: ULong,
) : SensitiveModel("SpendableUtxo") {
    val label: String get() = "${outPoint.txid}:${outPoint.vout} · $valueSats sats"
}
