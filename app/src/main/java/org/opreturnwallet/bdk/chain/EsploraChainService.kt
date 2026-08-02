package org.opreturnwallet.bdk.chain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoindevkit.EsploraClient
import org.bitcoindevkit.Transaction
import org.bitcoindevkit.Update
import org.bitcoindevkit.Wallet
import org.opreturnwallet.bdk.wallet.WalletNetwork

data class ChainTip(val height: UInt)

class EsploraChainService(
    val network: WalletNetwork,
    endpointOverride: String? = null,
) {
    val endpoint: String = endpointOverride ?: network.defaultEsploraUrl
    private val client = EsploraClient(endpoint)

    suspend fun fullScan(wallet: Wallet, stopGap: ULong = 20uL): Update = withContext(Dispatchers.IO) {
        client.fullScan(
            request = wallet.startFullScan().build(),
            stopGap = stopGap,
            parallelRequests = PARALLEL_REQUESTS,
        )
    }

    suspend fun sync(wallet: Wallet): Update = withContext(Dispatchers.IO) {
        client.sync(
            request = wallet.startSyncWithRevealedSpks().build(),
            parallelRequests = PARALLEL_REQUESTS,
        )
    }

    suspend fun broadcast(transaction: Transaction) = withContext(Dispatchers.IO) {
        client.broadcast(transaction)
    }

    suspend fun feeEstimates(): Map<UShort, Double> = withContext(Dispatchers.IO) {
        client.getFeeEstimates()
    }

    suspend fun tip(): ChainTip = withContext(Dispatchers.IO) {
        ChainTip(client.getHeight())
    }

    suspend fun conservativeFeeRate(targetBlocks: UShort = 3u): Double {
        val estimates = feeEstimates()
        val exact = estimates[targetBlocks]
        val nearest = estimates.entries
            .minByOrNull { kotlin.math.abs(it.key.toInt() - targetBlocks.toInt()) }
            ?.value
        return maxOf(MINIMUM_RELAY_FEE_SAT_VB, exact ?: nearest ?: DEFAULT_FEE_RATE_SAT_VB)
    }

    private companion object {
        const val PARALLEL_REQUESTS = 4uL
        const val MINIMUM_RELAY_FEE_SAT_VB = 1.0
        const val DEFAULT_FEE_RATE_SAT_VB = 2.0
    }
}
