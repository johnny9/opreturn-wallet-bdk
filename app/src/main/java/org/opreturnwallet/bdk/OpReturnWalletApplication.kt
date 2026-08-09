package org.opreturnwallet.bdk

import android.app.Application
import org.opreturnwallet.bdk.storage.EncryptedSeedStore
import org.opreturnwallet.bdk.storage.WalletPreferences
import org.opreturnwallet.bdk.transaction.FeeBumpTransactionService
import org.opreturnwallet.bdk.transaction.MessageTransactionService
import org.opreturnwallet.bdk.transaction.SweepTransactionService
import org.opreturnwallet.bdk.wallet.WalletRepository

class OpReturnWalletApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val preferences = WalletPreferences(application)
    private val seedStore = EncryptedSeedStore(application)
    private val transactionService = MessageTransactionService(
        debugConsumeEnabled = BuildConfig.ENABLE_DEBUG_CONSUME_UTXO,
    )
    private val feeBumpTransactionService = FeeBumpTransactionService()
    private val sweepTransactionService = SweepTransactionService()
    val walletRepository = WalletRepository(
        filesPath = application.filesDir.absolutePath,
        preferences = preferences,
        encryptedSeedStore = seedStore,
        transactionService = transactionService,
        feeBumpTransactionService = feeBumpTransactionService,
        sweepTransactionService = sweepTransactionService,
        mainnetOnly = BuildConfig.MAINNET_TRIAL,
    )
}
