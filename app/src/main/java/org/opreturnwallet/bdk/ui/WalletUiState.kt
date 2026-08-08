package org.opreturnwallet.bdk.ui

import org.opreturnwallet.bdk.transaction.TransactionPreview
import org.opreturnwallet.bdk.transaction.SweepTransactionPreview
import org.opreturnwallet.bdk.security.SensitiveModel
import org.opreturnwallet.bdk.wallet.MessageTransactionRecord
import org.opreturnwallet.bdk.wallet.SweepTransactionRecord
import org.opreturnwallet.bdk.wallet.WalletNetwork
import org.opreturnwallet.bdk.wallet.WalletSnapshot
import org.opreturnwallet.bdk.wallet.SpendableUtxo

enum class Screen {
    LOADING,
    WELCOME,
    RESTORE,
    RECOVERY_PHRASE,
    VERIFY_RECOVERY,
    UNLOCK,
    HOME,
    COMPOSE_MESSAGE,
    PREVIEW,
    RESULT,
    SWEEP,
    SWEEP_PREVIEW,
    SWEEP_RESULT,
    SETTINGS,
}

enum class ComposeMode {
    STANDARD,
    ANCHOR_SELF,
    ANCHOR_RECIPIENT,
    DEBUG_CONSUME_UTXO,
}

enum class SyncStatus {
    IDLE,
    SYNCING,
    CURRENT,
    ERROR,
}

data class WalletUiState(
    val screen: Screen = Screen.LOADING,
    val selectedNetwork: WalletNetwork = WalletNetwork.SIGNET,
    val mainnetEnabled: Boolean = false,
    val recoveryPhrase: String? = null,
    val restorePhrase: String = "",
    val verificationPhrase: String = "",
    val snapshot: WalletSnapshot? = null,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val messageText: String = "",
    val messageByteCount: Int = 0,
    val feeRateText: String = "2.0",
    val composeMode: ComposeMode = ComposeMode.STANDARD,
    val anchorAmountText: String = "1000",
    val recipientAddress: String = "",
    val debugUtxos: List<SpendableUtxo> = emptyList(),
    val selectedDebugUtxo: SpendableUtxo? = null,
    val preview: TransactionPreview? = null,
    val permanentAcknowledged: Boolean = false,
    val result: MessageTransactionRecord? = null,
    val sweepDestinationAddress: String = "",
    val sweepFeeRateText: String = "2.0",
    val sweepPreview: SweepTransactionPreview? = null,
    val sweepConfirmationText: String = "",
    val sweepResult: SweepTransactionRecord? = null,
    val biometricUnlockEnabled: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) : SensitiveModel("WalletUiState")
