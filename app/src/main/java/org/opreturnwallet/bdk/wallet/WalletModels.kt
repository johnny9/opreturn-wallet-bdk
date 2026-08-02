package org.opreturnwallet.bdk.wallet

import org.bitcoindevkit.Persister
import org.bitcoindevkit.OutPoint
import org.bitcoindevkit.Wallet

data class WalletSession(
    val id: String,
    val network: WalletNetwork,
    val wallet: Wallet,
    val persister: Persister,
)

data class CreatedWallet(
    val session: WalletSession,
    val recoveryPhrase: String,
)

data class WalletBalance(
    val confirmedSats: ULong,
    val pendingSats: ULong,
    val totalSats: ULong,
)

data class MessageTransactionRecord(
    val txid: String,
    val message: String,
    val payloadHex: String,
    val pending: Boolean,
    val blockHeight: UInt?,
    val feeSats: ULong?,
)

data class WalletSnapshot(
    val network: WalletNetwork,
    val balance: WalletBalance,
    val receiveAddress: String,
    val messages: List<MessageTransactionRecord>,
)

data class SpendableUtxo(
    val outPoint: OutPoint,
    val valueSats: ULong,
) {
    val label: String get() = "${outPoint.txid}:${outPoint.vout} · $valueSats sats"
}
