package org.opreturnwallet.bdk.wallet

import org.bitcoindevkit.Network

enum class WalletNetwork(
    val displayName: String,
    val bdkNetwork: Network,
    val defaultEsploraUrl: String,
    val explorerTransactionBaseUrl: String?,
) {
    REGTEST(
        displayName = "Regtest",
        bdkNetwork = Network.REGTEST,
        defaultEsploraUrl = "http://10.0.2.2:3002",
        explorerTransactionBaseUrl = null,
    ),
    SIGNET(
        displayName = "Signet",
        bdkNetwork = Network.SIGNET,
        defaultEsploraUrl = "https://blockstream.info/signet/api/",
        explorerTransactionBaseUrl = "https://mempool.space/signet/tx/",
    ),
    TESTNET4(
        displayName = "Testnet4",
        bdkNetwork = Network.TESTNET4,
        defaultEsploraUrl = "https://mempool.space/testnet4/api/",
        explorerTransactionBaseUrl = "https://mempool.space/testnet4/tx/",
    ),
    MAINNET(
        displayName = "Mainnet",
        bdkNetwork = Network.BITCOIN,
        defaultEsploraUrl = "https://mempool.space/api/",
        explorerTransactionBaseUrl = "https://mempool.space/tx/",
    ),
    ;

    val isMainnet: Boolean get() = this == MAINNET

    fun explorerUrl(txid: String): String? = explorerTransactionBaseUrl?.plus(txid)
}
