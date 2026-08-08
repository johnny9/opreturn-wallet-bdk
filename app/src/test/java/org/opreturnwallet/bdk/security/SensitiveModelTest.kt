package org.opreturnwallet.bdk.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.opreturnwallet.bdk.message.OpReturnPayload
import org.opreturnwallet.bdk.storage.WalletMetadata
import org.opreturnwallet.bdk.transaction.WriteMode
import org.opreturnwallet.bdk.ui.WalletUiState
import org.opreturnwallet.bdk.wallet.MessageTransactionRecord
import org.opreturnwallet.bdk.wallet.SweepTransactionRecord
import org.opreturnwallet.bdk.wallet.WalletBalance
import org.opreturnwallet.bdk.wallet.WalletNetwork
import org.opreturnwallet.bdk.wallet.WalletSnapshot

class SensitiveModelTest {
    @Test
    fun `payload string representation never contains pre-broadcast message bytes`() {
        val privateMessage = "private pre-broadcast message"
        val payload = OpReturnPayload.fromText(privateMessage)

        assertRedacted(payload, privateMessage, payload.hex)
    }

    @Test
    fun `wallet UI state string representation never contains private wallet values`() {
        val recovery = "sensitive recovery material"
        val restore = "sensitive restore material"
        val message = "message not broadcast yet"
        val address = "bc1qprivatewalletaddress"
        val snapshot = WalletSnapshot(
            network = WalletNetwork.MAINNET,
            balance = WalletBalance(50_000uL, 0uL, 50_000uL),
            receiveAddress = address,
            messages = listOf(
                MessageTransactionRecord("private-txid", message, "deadbeef", true, null, 200uL),
            ),
        )
        val state = WalletUiState(
            recoveryPhrase = recovery,
            restorePhrase = restore,
            verificationPhrase = recovery,
            snapshot = snapshot,
            messageText = message,
            recipientAddress = address,
            sweepDestinationAddress = address,
            result = snapshot.messages.single(),
            sweepResult = SweepTransactionRecord("sweep-txid", address, 49_800uL, 200uL, true, null),
        )

        assertRedacted(state, recovery, restore, message, address, "private-txid", "sweep-txid")
        assertRedacted(snapshot, message, address, "private-txid")
        assertRedacted(snapshot.balance, "50000")
        assertRedacted(snapshot.messages.single(), message, "deadbeef", "private-txid")
        assertRedacted(state.sweepResult!!, address, "sweep-txid")
    }

    @Test
    fun `metadata and recipient modes redact identifiers and addresses`() {
        val walletId = "private-wallet-id"
        val address = "bc1qprivatewalletaddress"

        assertRedacted(
            WalletMetadata(walletId, WalletNetwork.MAINNET, true, true),
            walletId,
        )
        assertRedacted(WriteMode.AnchorToRecipient(address, 1_000uL), address, "1000")
    }

    private fun assertRedacted(value: Any, vararg privateValues: String) {
        val rendered = value.toString()
        assertEquals("${value.javaClass.simpleName}([REDACTED])", rendered)
        privateValues.forEach { privateValue ->
            assertFalse("String representation exposed $privateValue", rendered.contains(privateValue))
        }
    }
}
