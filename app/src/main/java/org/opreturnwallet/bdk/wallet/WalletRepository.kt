package org.opreturnwallet.bdk.wallet

import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.bitcoindevkit.ChainPosition
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Txid
import org.bitcoindevkit.WordCount
import org.opreturnwallet.bdk.chain.EsploraChainService
import org.opreturnwallet.bdk.message.OpReturnScript
import org.opreturnwallet.bdk.message.toHex
import org.opreturnwallet.bdk.storage.EncryptedSeedStore
import org.opreturnwallet.bdk.storage.WalletMetadata
import org.opreturnwallet.bdk.storage.WalletPreferences
import org.opreturnwallet.bdk.transaction.FeeSafetyLimits
import org.opreturnwallet.bdk.transaction.FeeBumpPreview
import org.opreturnwallet.bdk.transaction.FeeBumpTransactionService
import org.opreturnwallet.bdk.transaction.MessageTransactionService
import org.opreturnwallet.bdk.transaction.PreviewOutputKind
import org.opreturnwallet.bdk.transaction.SweepTransactionPreview
import org.opreturnwallet.bdk.transaction.SweepTransactionService
import org.opreturnwallet.bdk.transaction.TransactionPreview
import org.opreturnwallet.bdk.transaction.WriteMode
import org.opreturnwallet.bdk.message.OpReturnPayload
import org.bitcoindevkit.Wallet as BdkWallet

class WalletRepository(
    private val filesPath: String,
    private val preferences: WalletPreferences,
    private val encryptedSeedStore: EncryptedSeedStore,
    private val transactionService: MessageTransactionService,
    private val feeBumpTransactionService: FeeBumpTransactionService,
    private val sweepTransactionService: SweepTransactionService,
    private val mainnetOnly: Boolean = false,
) {
    @Volatile
    private var activeSession: WalletSession? = null

    suspend fun hasWallet(): Boolean = preferences.currentMetadata() != null

    suspend fun biometricUnlockEnabled(): Boolean =
        preferences.currentMetadata()?.biometricUnlockEnabled ?: false

    suspend fun create(network: WalletNetwork): CreatedWallet = withContext(Dispatchers.IO) {
        enforceNetwork(network)
        val mnemonic = Mnemonic(WordCount.WORDS12)
        createFromMnemonic(network, mnemonic, mnemonic.toString())
    }

    suspend fun restore(network: WalletNetwork, recoveryPhrase: String): CreatedWallet =
        withContext(Dispatchers.IO) {
            enforceNetwork(network)
            val mnemonic = Mnemonic.fromString(recoveryPhrase.trim())
            createFromMnemonic(network, mnemonic, mnemonic.toString())
        }

    suspend fun load(): WalletSession = withContext(Dispatchers.IO) {
        activeSession ?: run {
            val metadata = preferences.currentMetadata() ?: error("No wallet has been created")
            if (mainnetOnly) check(metadata.network == WalletNetwork.MAINNET) {
                "The isolated mainnet build can only load a Mainnet wallet"
            }
            val mnemonicText = encryptedSeedStore.read(metadata.walletId)
            val mnemonic = Mnemonic.fromString(mnemonicText)
            val descriptors = descriptors(metadata.network, mnemonic)
            val persister = persister(metadata.walletId)
            val wallet = BdkWallet.load(
                descriptor = descriptors.first,
                changeDescriptor = descriptors.second,
                persister = persister,
            )
            WalletSession(metadata.walletId, metadata.network, wallet, persister).also {
                activeSession = it
            }
        }
    }

    suspend fun sync(): WalletSnapshot = withContext(Dispatchers.IO) {
        val session = load()
        val metadata = preferences.currentMetadata() ?: error("Wallet metadata missing")
        val chain = EsploraChainService(session.network)
        val update = if (metadata.fullScanComplete) {
            chain.sync(session.wallet)
        } else {
            chain.fullScan(session.wallet)
        }
        session.wallet.applyUpdate(update)
        session.wallet.persist(session.persister)
        if (!metadata.fullScanComplete) preferences.setFullScanComplete(true)
        snapshot(session)
    }

    suspend fun snapshot(): WalletSnapshot = withContext(Dispatchers.IO) {
        snapshot(load())
    }

    suspend fun newReceiveAddress(): String = withContext(Dispatchers.IO) {
        val session = load()
        val address = session.wallet.revealNextAddress(KeychainKind.EXTERNAL).address.toString()
        session.wallet.persist(session.persister)
        address
    }

    suspend fun estimatedFeeRate(targetBlocks: UShort = 3u): Double {
        val session = load()
        return EsploraChainService(session.network).conservativeFeeRate(targetBlocks)
    }

    suspend fun spendableUtxos(): List<SpendableUtxo> = withContext(Dispatchers.IO) {
        load().wallet.listUnspent().filterNot { it.isSpent }.map {
            SpendableUtxo(it.outpoint, it.txout.value.toSat())
        }
    }

    suspend fun buildMessagePreview(
        payload: OpReturnPayload,
        feeRateSatVb: Double,
        mode: WriteMode,
        limits: FeeSafetyLimits = FeeSafetyLimits(),
    ): TransactionPreview = withContext(Dispatchers.IO) {
        val session = load()
        transactionService.buildPreview(
            wallet = session.wallet,
            persister = session.persister,
            network = session.network,
            payload = payload,
            requestedFeeRateSatVb = feeRateSatVb,
            mode = mode,
            limits = limits,
        )
    }

    suspend fun signAndBroadcast(preview: TransactionPreview): MessageTransactionRecord =
        withContext(Dispatchers.IO) {
            val session = load()
            val signed = transactionService.signApproved(session.wallet, preview)
            EsploraChainService(session.network).broadcast(signed)
            session.wallet.persist(session.persister)
            MessageTransactionRecord(
                txid = signed.computeTxid().toString(),
                message = preview.payload.text,
                payloadHex = preview.payload.hex,
                pending = true,
                blockHeight = null,
                feeSats = preview.feeSats,
                feeRateSatVb = preview.feeRateSatVb,
                rbfEligible = preview.outputs.any { it.kind == PreviewOutputKind.CHANGE },
            )
        }

    suspend fun buildFeeBumpPreview(
        originalTxid: String,
        feeRateSatVb: Double,
        limits: FeeSafetyLimits = FeeSafetyLimits(),
    ): FeeBumpPreview = withContext(Dispatchers.IO) {
        val session = load()
        feeBumpTransactionService.buildPreview(
            wallet = session.wallet,
            persister = session.persister,
            originalTxid = originalTxid,
            requestedFeeRateSatVb = feeRateSatVb,
            limits = limits,
        )
    }

    suspend fun signAndBroadcastFeeBump(preview: FeeBumpPreview): FeeBumpTransactionRecord =
        withContext(Dispatchers.IO) {
            val session = load()
            val chain = EsploraChainService(session.network)
            val update = chain.sync(session.wallet)
            session.wallet.applyUpdate(update)
            session.wallet.persist(session.persister)
            val original = session.wallet.getTx(Txid.fromString(preview.originalTxid))
                ?: throw org.opreturnwallet.bdk.transaction.TransactionPolicyException.TransactionNotFound
            if (original.chainPosition !is ChainPosition.Unconfirmed) {
                throw org.opreturnwallet.bdk.transaction.TransactionPolicyException.TransactionNotPending
            }

            val signed = feeBumpTransactionService.signApproved(session.wallet, preview)
            chain.broadcast(signed)
            session.wallet.persist(session.persister)
            FeeBumpTransactionRecord(
                originalTxid = preview.originalTxid,
                replacementTxid = signed.computeTxid().toString(),
                originalFeeSats = preview.originalFeeSats,
                replacementFeeSats = preview.replacementFeeSats,
                replacementFeeRateSatVb = preview.replacementFeeRateSatVb,
                pending = true,
                blockHeight = null,
            )
        }

    suspend fun buildSweepPreview(
        destinationAddress: String,
        feeRateSatVb: Double,
        limits: FeeSafetyLimits = FeeSafetyLimits(),
    ): SweepTransactionPreview = withContext(Dispatchers.IO) {
        val session = load()
        sweepTransactionService.buildPreview(
            wallet = session.wallet,
            persister = session.persister,
            network = session.network,
            destination = destinationAddress,
            requestedFeeRateSatVb = feeRateSatVb,
            limits = limits,
        )
    }

    suspend fun signAndBroadcastSweep(preview: SweepTransactionPreview): SweepTransactionRecord =
        withContext(Dispatchers.IO) {
            val session = load()
            val signed = sweepTransactionService.signApproved(session.wallet, preview)
            EsploraChainService(session.network).broadcast(signed)
            session.wallet.persist(session.persister)
            SweepTransactionRecord(
                txid = signed.computeTxid().toString(),
                destinationAddress = preview.destinationAddress,
                amountSats = preview.recipientValueSats,
                feeSats = preview.feeSats,
                pending = true,
                blockHeight = null,
            )
        }

    suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        preferences.setBiometricUnlockEnabled(enabled)
    }

    private suspend fun createFromMnemonic(
        network: WalletNetwork,
        mnemonic: Mnemonic,
        canonicalPhrase: String,
    ): CreatedWallet {
        check(preferences.currentMetadata() == null) { "A wallet already exists" }
        val walletId = UUID.randomUUID().toString()
        val (external, internal) = descriptors(network, mnemonic)
        val persister = persister(walletId)
        var seedWritten = false
        try {
            encryptedSeedStore.write(walletId, canonicalPhrase)
            seedWritten = true
            val wallet = BdkWallet(
                descriptor = external,
                changeDescriptor = internal,
                network = network.bdkNetwork,
                persister = persister,
            )
            val metadata = WalletMetadata(
                walletId = walletId,
                network = network,
                fullScanComplete = false,
                biometricUnlockEnabled = false,
            )
            preferences.saveMetadata(metadata)
            val session = WalletSession(walletId, network, wallet, persister)
            activeSession = session
            return CreatedWallet(session, canonicalPhrase)
        } catch (error: Throwable) {
            if (seedWritten) encryptedSeedStore.delete(walletId)
            throw error
        }
    }

    private suspend fun enforceNetwork(network: WalletNetwork) {
        if (mainnetOnly) {
            check(network == WalletNetwork.MAINNET) {
                "The isolated mainnet build only permits Mainnet wallets"
            }
        }
        if (network.isMainnet) {
            check(preferences.mainnetEnabled.first()) {
                "Mainnet must be explicitly enabled before creating a wallet"
            }
        }
    }

    private fun descriptors(network: WalletNetwork, mnemonic: Mnemonic): Pair<Descriptor, Descriptor> {
        val root = DescriptorSecretKey(network.bdkNetwork, mnemonic, null)
        return Descriptor.newBip84(root, KeychainKind.EXTERNAL, network.bdkNetwork) to
            Descriptor.newBip84(root, KeychainKind.INTERNAL, network.bdkNetwork)
    }

    private fun persister(walletId: String): Persister =
        Persister.newSqlite("$filesPath/wallet-$walletId.sqlite3")

    private fun snapshot(session: WalletSession): WalletSnapshot {
        val wallet = session.wallet
        val balance = wallet.balance()
        val pending = balance.trustedPending.toSat() + balance.untrustedPending.toSat()
        val receiveAddress = wallet.nextUnusedAddress(KeychainKind.EXTERNAL).address.toString()
        wallet.persist(session.persister)
        val transactions = wallet.transactions()
        val transactionStatuses = transactions.associate { canonicalTx ->
            val position = canonicalTx.chainPosition
            canonicalTx.transaction.computeTxid().toString() to WalletTransactionStatus(
                pending = position is ChainPosition.Unconfirmed,
                blockHeight = (position as? ChainPosition.Confirmed)?.confirmationBlockTime?.blockId?.height,
            )
        }
        val messages = transactions.mapNotNull { canonicalTx ->
            val matches = canonicalTx.transaction.output().mapNotNull { output ->
                OpReturnScript.decode(output.scriptPubkey.toBytes())
            }
            if (matches.size != 1) return@mapNotNull null
            val text = decodeUtf8Strict(matches.single()) ?: return@mapNotNull null
            val position = canonicalTx.chainPosition
            val pendingTransaction = position is ChainPosition.Unconfirmed
            val blockHeight = (position as? ChainPosition.Confirmed)?.confirmationBlockTime?.blockId?.height
            val fee = runCatching { wallet.calculateFee(canonicalTx.transaction).toSat() }.getOrNull()
            val feeRate = fee?.toDouble()
                ?.div(canonicalTx.transaction.vsize().coerceAtLeast(1uL).toDouble())
            val sendsWalletFunds = runCatching {
                wallet.sentAndReceived(canonicalTx.transaction).sent.toSat() > 0uL
            }.getOrDefault(false)
            val hasInternalChange = canonicalTx.transaction.output().any { output ->
                wallet.derivationOfSpk(output.scriptPubkey)?.keychain == KeychainKind.INTERNAL
            }
            MessageTransactionRecord(
                txid = canonicalTx.transaction.computeTxid().toString(),
                message = text,
                payloadHex = matches.single().toHex(),
                pending = pendingTransaction,
                blockHeight = blockHeight,
                feeSats = fee,
                feeRateSatVb = feeRate,
                rbfEligible = pendingTransaction &&
                    fee != null &&
                    sendsWalletFunds &&
                    hasInternalChange &&
                    canonicalTx.transaction.isExplicitlyRbf(),
            )
        }
        return WalletSnapshot(
            network = session.network,
            balance = WalletBalance(
                confirmedSats = balance.confirmed.toSat(),
                pendingSats = pending,
                totalSats = balance.total.toSat(),
            ),
            receiveAddress = receiveAddress,
            messages = messages,
            transactionStatuses = transactionStatuses,
        )
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String? = runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()
}
