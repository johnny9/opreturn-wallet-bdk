package org.opreturnwallet.bdk.transaction

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.bitcoindevkit.ChainPosition
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.EsploraClient
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Wallet
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opreturnwallet.bdk.message.OpReturnPayload
import org.opreturnwallet.bdk.message.OpReturnScript
import org.opreturnwallet.bdk.wallet.WalletNetwork

/** Opt-in, spending live Signet acceptance test for the first usable release. */
@RunWith(AndroidJUnit4::class)
class SignetLifecycleInstrumentedTest {
    @Test(timeout = 2_100_000L)
    fun writeConfirmRestartRestoreAndRediscoverAsciiAndUnicodeMessages() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Set runLiveSignet=true to authorize Signet broadcasts", args.getString("runLiveSignet") == "true")
        val phrase = args.getString("signetMnemonic")
        assumeTrue("A funded signetMnemonic instrumentation argument is required", !phrase.isNullOrBlank())

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mnemonic = Mnemonic.fromString(phrase!!)
        val root = DescriptorSecretKey(Network.SIGNET, mnemonic, null)
        val external = Descriptor.newBip84(root, KeychainKind.EXTERNAL, Network.SIGNET)
        val internal = Descriptor.newBip84(root, KeychainKind.INTERNAL, Network.SIGNET)
        val client = EsploraClient("https://blockstream.info/signet/api/")
        val firstDb = Persister.newSqlite(File(context.cacheDir, "signet-live-${System.nanoTime()}.sqlite3").absolutePath)
        val wallet = Wallet(external, internal, Network.SIGNET, firstDb)
        applyFullScan(wallet, firstDb, client)
        assumeTrue("The Signet wallet must be funded", wallet.balance().trustedSpendable.toSat() > 20_000uL)

        val service = MessageTransactionService(debugConsumeEnabled = false)
        val txids = listOf("Signet acceptance", "Unicode 🚀 é").map { text ->
            val preview = service.buildPreview(
                wallet = wallet,
                persister = firstDb,
                network = WalletNetwork.SIGNET,
                payload = OpReturnPayload.fromText(text),
                requestedFeeRateSatVb = 2.0,
                mode = WriteMode.StandardMessage,
                limits = FeeSafetyLimits(maximumFeePercentOfInputs = 100.0),
            )
            val signed = service.signApproved(wallet, preview)
            client.broadcast(signed)
            syncUntilSeen(wallet, firstDb, client, signed.computeTxid().toString())
            signed.computeTxid().toString()
        }

        waitForConfirmations(wallet, firstDb, client, txids)

        // App restart: load the same persisted state with secret descriptors reconstructed from seed.
        val restarted = Wallet.load(external, internal, firstDb)
        assertTrue(txids.all { id -> restarted.transactions().any { it.transaction.computeTxid().toString() == id } })

        // Recovery: create a new BDK database and rediscover both transactions with a full scan.
        val restoredDb = Persister.newSqlite(File(context.cacheDir, "signet-restored-${System.nanoTime()}.sqlite3").absolutePath)
        val restored = Wallet(external, internal, Network.SIGNET, restoredDb)
        applyFullScan(restored, restoredDb, client)
        val decoded = restored.transactions().flatMap { canonical ->
            canonical.transaction.output().mapNotNull { output ->
                OpReturnScript.decode(output.scriptPubkey.toBytes())?.toString(Charsets.UTF_8)
            }
        }
        assertTrue(decoded.contains("Signet acceptance"))
        assertTrue(decoded.contains("Unicode 🚀 é"))
    }

    private fun applyFullScan(wallet: Wallet, persister: Persister, client: EsploraClient) {
        wallet.applyUpdate(client.fullScan(wallet.startFullScan().build(), 20uL, 4uL))
        wallet.persist(persister)
    }

    private fun syncUntilSeen(wallet: Wallet, persister: Persister, client: EsploraClient, txid: String) {
        repeat(30) {
            applySync(wallet, persister, client)
            if (wallet.transactions().any { it.transaction.computeTxid().toString() == txid }) return
            Thread.sleep(1_000)
        }
        error("Signet transaction was not visible through Esplora")
    }

    private fun waitForConfirmations(
        wallet: Wallet,
        persister: Persister,
        client: EsploraClient,
        txids: List<String>,
    ) {
        repeat(60) {
            applySync(wallet, persister, client)
            val confirmed = wallet.transactions()
                .filter { it.chainPosition is ChainPosition.Confirmed }
                .map { it.transaction.computeTxid().toString() }
                .toSet()
            if (txids.all(confirmed::contains)) return
            Thread.sleep(30_000)
        }
        error("Signet transactions did not confirm within 30 minutes")
    }

    private fun applySync(wallet: Wallet, persister: Persister, client: EsploraClient) {
        wallet.applyUpdate(client.sync(wallet.startSyncWithRevealedSpks().build(), 4uL))
        wallet.persist(persister)
    }
}
