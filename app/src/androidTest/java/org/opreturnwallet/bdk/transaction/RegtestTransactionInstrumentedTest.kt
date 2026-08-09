package org.opreturnwallet.bdk.transaction

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.EsploraClient
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opreturnwallet.bdk.message.OpReturnPayload
import org.opreturnwallet.bdk.message.OpReturnScript
import org.opreturnwallet.bdk.wallet.WalletNetwork

/**
 * Live Regtest contract tests. Supply instrumentation args `regtestMnemonic` and optionally
 * `regtestEsploraUrl`; the wallet represented by the mnemonic must already contain a confirmed UTXO.
 */
@RunWith(AndroidJUnit4::class)
class RegtestTransactionInstrumentedTest {
    private lateinit var wallet: Wallet
    private lateinit var persister: Persister
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setupFundedWallet() {
        val args = InstrumentationRegistry.getArguments()
        val phrase = args.getString("regtestMnemonic")
        assumeTrue("regtestMnemonic instrumentation argument is required", !phrase.isNullOrBlank())
        val mnemonic = Mnemonic.fromString(phrase!!)
        val root = DescriptorSecretKey(Network.REGTEST, mnemonic, null)
        val external = Descriptor.newBip84(root, KeychainKind.EXTERNAL, Network.REGTEST)
        val internal = Descriptor.newBip84(root, KeychainKind.INTERNAL, Network.REGTEST)
        val database = File(context.cacheDir, "regtest-${System.nanoTime()}.sqlite3")
        persister = Persister.newSqlite(database.absolutePath)
        wallet = Wallet(external, internal, Network.REGTEST, persister)

        val endpoint = args.getString("regtestEsploraUrl") ?: "http://10.0.2.2:3002"
        val client = EsploraClient(endpoint)
        val update = client.fullScan(wallet.startFullScan().build(), 20uL, 4uL)
        wallet.applyUpdate(update)
        wallet.persist(persister)
        assumeTrue("The Regtest wallet must have a confirmed spendable UTXO", wallet.balance().confirmed.toSat() > 10_000uL)
    }

    @Test
    fun standardMessageHasOneZeroValueOpReturnAndChange() {
        val payload = OpReturnPayload.fromText("regtest message 🚀")
        val preview = MessageTransactionService(debugConsumeEnabled = false).buildPreview(
            wallet = wallet,
            persister = persister,
            network = WalletNetwork.REGTEST,
            payload = payload,
            requestedFeeRateSatVb = 1.0,
            mode = WriteMode.StandardMessage,
            limits = FeeSafetyLimits(maximumFeePercentOfInputs = 100.0),
        )

        val transaction = preview.unsignedTransaction
        assertTrue(transaction.input().isNotEmpty())
        assertTrue(transaction.isExplicitlyRbf())
        val opReturns = transaction.output().filter { OpReturnScript.decode(it.scriptPubkey.toBytes()) != null }
        assertEquals(1, opReturns.size)
        assertEquals(0uL, opReturns.single().value.toSat())
        assertTrue(OpReturnScript.decode(opReturns.single().scriptPubkey.toBytes())!!.contentEquals(payload.utf8Bytes))
        assertEquals(1, preview.outputs.count { it.kind == PreviewOutputKind.CHANGE })
    }

    @Test
    fun anchorModeHasAnchorAndChangeWithoutDustOverride() {
        val preview = MessageTransactionService(debugConsumeEnabled = false).buildPreview(
            wallet = wallet,
            persister = persister,
            network = WalletNetwork.REGTEST,
            payload = OpReturnPayload.fromText("anchored"),
            requestedFeeRateSatVb = 1.0,
            mode = WriteMode.AnchorToSelf(1_000uL),
            limits = FeeSafetyLimits(maximumFeePercentOfInputs = 100.0),
        )
        assertEquals(1, preview.outputs.count { it.kind == PreviewOutputKind.OP_RETURN })
        assertEquals(1, preview.outputs.count { it.kind == PreviewOutputKind.ANCHOR })
        assertEquals(1, preview.outputs.count { it.kind == PreviewOutputKind.CHANGE })
    }

    @Test
    fun sweepSpendsEveryUtxoToOneExternalAddressWithoutChange() {
        val spendable = wallet.listUnspent().filterNot { it.isSpent }
        val destinationWallet = newUnfundedWallet("sweep-destination")
        val destination = destinationWallet.nextUnusedAddress(KeychainKind.EXTERNAL).address.toString()

        val preview = SweepTransactionService().buildPreview(
            wallet = wallet,
            persister = persister,
            network = WalletNetwork.REGTEST,
            destination = destination,
            requestedFeeRateSatVb = 1.0,
            limits = FeeSafetyLimits(maximumFeePercentOfInputs = 100.0),
        )

        assertEquals(spendable.size, preview.inputCount)
        assertEquals(spendable.size, preview.unsignedTransaction.input().size)
        assertEquals(1, preview.unsignedTransaction.output().size)
        assertEquals(
            preview.inputValueSats,
            preview.recipientValueSats + preview.feeSats,
        )
        val output = preview.unsignedTransaction.output().single()
        assertTrue(
            output.scriptPubkey.toBytes().contentEquals(
                org.bitcoindevkit.Address(destination, Network.REGTEST).scriptPubkey().toBytes(),
            ),
        )
        assertFalse(wallet.isMine(output.scriptPubkey))
        assertTrue(preview.unsignedTransaction.isExplicitlyRbf())
        assertTrue(OpReturnScript.decode(output.scriptPubkey.toBytes()) == null)
    }

    @Test
    fun sweepRejectsDestinationOwnedByTheSameWallet() {
        val selfAddress = wallet.nextUnusedAddress(KeychainKind.EXTERNAL).address.toString()
        assertThrows(TransactionPolicyException.DestinationBelongsToWallet::class.java) {
            SweepTransactionService().buildPreview(
                wallet = wallet,
                persister = persister,
                network = WalletNetwork.REGTEST,
                destination = selfAddress,
                requestedFeeRateSatVb = 1.0,
                limits = FeeSafetyLimits(maximumFeePercentOfInputs = 100.0),
            )
        }
    }

    @Test
    fun broadcastReplacementPreservesMessageAndOpReturnNeverBecomesUtxo() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Set runBroadcastTests=true to authorize Regtest broadcasts", args.getString("runBroadcastTests") == "true")
        val endpoint = args.getString("regtestEsploraUrl") ?: "http://10.0.2.2:3002"
        val client = EsploraClient(endpoint)
        val balanceBefore = wallet.balance().total.toSat()
        val payload = OpReturnPayload.fromText("replaceable")
        val service = MessageTransactionService(debugConsumeEnabled = false)
        val original = service.buildPreview(
            wallet = wallet,
            persister = persister,
            network = WalletNetwork.REGTEST,
            payload = payload,
            requestedFeeRateSatVb = 1.0,
            mode = WriteMode.StandardMessage,
            limits = FeeSafetyLimits(maximumFeePercentOfInputs = 100.0),
        )
        val originalTx = service.signApproved(wallet, original)
        client.broadcast(originalTx)
        syncUntilSeen(client, originalTx.computeTxid().toString())

        val feeBumpService = FeeBumpTransactionService()
        val replacementPreview = feeBumpService.buildPreview(
            wallet = wallet,
            persister = persister,
            originalTxid = originalTx.computeTxid().toString(),
            requestedFeeRateSatVb = 2.0,
            limits = FeeSafetyLimits(maximumFeePercentOfInputs = 100.0),
        )
        val replacement = feeBumpService.signApproved(wallet, replacementPreview)
        assertTrue(replacement.isExplicitlyRbf())
        assertTrue(replacementPreview.replacementFeeSats > original.feeSats)
        assertTrue(replacementPreview.additionalFeeSats >= replacementPreview.estimatedVbytes)
        val replacementData = replacement.output().mapNotNull { OpReturnScript.decode(it.scriptPubkey.toBytes()) }
        assertEquals(1, replacementData.size)
        assertTrue(replacementData.single().contentEquals(payload.utf8Bytes))
        client.broadcast(replacement)
        syncUntilSeen(client, replacement.computeTxid().toString())

        assertTrue(wallet.listOutput().none { OpReturnScript.isOpReturn(it.txout.scriptPubkey.toBytes()) })
        assertEquals(balanceBefore - replacementPreview.replacementFeeSats, wallet.balance().total.toSat())
    }

    private fun syncUntilSeen(client: EsploraClient, txid: String) {
        repeat(20) {
            val update = client.sync(wallet.startSyncWithRevealedSpks().build(), 4uL)
            wallet.applyUpdate(update)
            wallet.persist(persister)
            if (wallet.transactions().any { it.transaction.computeTxid().toString() == txid }) return
            Thread.sleep(250)
        }
        error("Transaction was not visible through Esplora")
    }

    private fun newUnfundedWallet(label: String): Wallet {
        val mnemonic = Mnemonic(org.bitcoindevkit.WordCount.WORDS12)
        val root = DescriptorSecretKey(Network.REGTEST, mnemonic, null)
        val external = Descriptor.newBip84(root, KeychainKind.EXTERNAL, Network.REGTEST)
        val internal = Descriptor.newBip84(root, KeychainKind.INTERNAL, Network.REGTEST)
        val database = File(context.cacheDir, "$label-${System.nanoTime()}.sqlite3")
        return Wallet(external, internal, Network.REGTEST, Persister.newSqlite(database.absolutePath))
    }
}
