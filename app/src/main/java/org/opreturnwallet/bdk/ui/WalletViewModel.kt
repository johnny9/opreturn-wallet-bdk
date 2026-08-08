package org.opreturnwallet.bdk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opreturnwallet.bdk.BuildConfig
import org.opreturnwallet.bdk.message.OpReturnPayload
import org.opreturnwallet.bdk.storage.WalletPreferences
import org.opreturnwallet.bdk.transaction.WriteMode
import org.opreturnwallet.bdk.wallet.WalletNetwork
import org.opreturnwallet.bdk.wallet.WalletRepository

class WalletViewModel(
    private val repository: WalletRepository,
    private val preferences: WalletPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(WalletUiState())
    val state: StateFlow<WalletUiState> = _state.asStateFlow()
    private var monitorJob: Job? = null

    init {
        viewModelScope.launch {
            val mainnet = preferences.mainnetEnabled.first()
            val hasWallet = repository.hasWallet()
            val biometric = hasWallet && repository.biometricUnlockEnabled()
            _state.update {
                it.copy(
                    mainnetEnabled = mainnet,
                    selectedNetwork = if (BuildConfig.MAINNET_TRIAL) WalletNetwork.MAINNET else it.selectedNetwork,
                    biometricUnlockEnabled = biometric,
                    screen = when {
                        !hasWallet -> Screen.WELCOME
                        biometric -> Screen.UNLOCK
                        else -> Screen.LOADING
                    },
                )
            }
            if (hasWallet && !biometric) unlock()
        }
    }

    fun selectNetwork(network: WalletNetwork) {
        if (BuildConfig.MAINNET_TRIAL && network != WalletNetwork.MAINNET) return
        if (network.isMainnet && !_state.value.mainnetEnabled) return
        _state.update { it.copy(selectedNetwork = network, error = null) }
    }

    fun setMainnetEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setMainnetEnabled(enabled)
            _state.update {
                it.copy(
                    mainnetEnabled = enabled,
                    selectedNetwork = when {
                        BuildConfig.MAINNET_TRIAL -> WalletNetwork.MAINNET
                        !enabled && it.selectedNetwork.isMainnet -> WalletNetwork.SIGNET
                        else -> it.selectedNetwork
                    },
                )
            }
        }
    }

    fun createWallet() = launchBusy {
        val created = repository.create(_state.value.selectedNetwork)
        _state.update {
            it.copy(
                screen = Screen.RECOVERY_PHRASE,
                recoveryPhrase = created.recoveryPhrase,
                verificationPhrase = "",
            )
        }
    }

    fun showRestore() {
        _state.update { it.copy(screen = Screen.RESTORE, error = null) }
    }

    fun updateRestorePhrase(value: String) {
        _state.update { it.copy(restorePhrase = value, error = null) }
    }

    fun restoreWallet() = launchBusy {
        repository.restore(_state.value.selectedNetwork, _state.value.restorePhrase)
        val snapshot = repository.snapshot()
        _state.update { it.copy(restorePhrase = "", snapshot = snapshot, screen = Screen.HOME) }
        refresh()
        startMonitor()
    }

    fun continueRecoveryVerification() {
        _state.update { it.copy(screen = Screen.VERIFY_RECOVERY, error = null) }
    }

    fun updateVerificationPhrase(value: String) {
        _state.update { it.copy(verificationPhrase = value, error = null) }
    }

    fun verifyRecoveryPhrase() {
        val expected = canonicalWords(_state.value.recoveryPhrase.orEmpty())
        val actual = canonicalWords(_state.value.verificationPhrase)
        if (expected.isEmpty() || actual != expected) {
            _state.update { it.copy(error = "The recovery phrase does not match. Check every word and try again.") }
            return
        }
        _state.update {
            it.copy(
                recoveryPhrase = null,
                verificationPhrase = "",
                screen = Screen.LOADING,
                error = null,
            )
        }
        viewModelScope.launch {
            val snapshot = repository.snapshot()
            _state.update { it.copy(snapshot = snapshot, screen = Screen.HOME) }
            refresh()
            startMonitor()
        }
    }

    fun unlock() = launchBusy {
        repository.load()
        val snapshot = repository.snapshot()
        _state.update { it.copy(snapshot = snapshot, screen = Screen.HOME) }
        refresh()
        startMonitor()
    }

    fun refresh() {
        if (_state.value.syncStatus == SyncStatus.SYNCING) return
        viewModelScope.launch {
            _state.update { it.copy(syncStatus = SyncStatus.SYNCING, error = null) }
            runCatching { repository.sync() }
                .onSuccess { snapshot ->
                    _state.update { state ->
                        val updatedResult = state.result?.let { result ->
                            snapshot.messages.firstOrNull { it.txid == result.txid } ?: result
                        }
                        val updatedSweepResult = state.sweepResult?.let { result ->
                            snapshot.transactionStatuses[result.txid]?.let { status ->
                                result.copy(pending = status.pending, blockHeight = status.blockHeight)
                            } ?: result
                        }
                        state.copy(
                            snapshot = snapshot,
                            result = updatedResult,
                            sweepResult = updatedSweepResult,
                            screen = if (state.screen == Screen.LOADING) Screen.HOME else state.screen,
                            syncStatus = SyncStatus.CURRENT,
                        )
                    }
                }
                .onFailure { error ->
                    val hasSnapshot = _state.value.snapshot != null
                    _state.update {
                        it.copy(
                            screen = if (it.screen == Screen.LOADING && hasSnapshot) Screen.HOME else it.screen,
                            syncStatus = SyncStatus.ERROR,
                            error = error.userMessage(),
                        )
                    }
                }
        }
    }

    fun startCompose() {
        _state.update {
            it.copy(
                screen = Screen.COMPOSE_MESSAGE,
                messageText = "",
                messageByteCount = 0,
                preview = null,
                permanentAcknowledged = false,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.estimatedFeeRate() }
                .onSuccess { estimate -> _state.update { it.copy(feeRateText = "%.2f".format(estimate)) } }
            if (BuildConfig.ENABLE_DEBUG_CONSUME_UTXO && _state.value.snapshot?.network == WalletNetwork.REGTEST) {
                runCatching { repository.spendableUtxos() }.onSuccess { utxos ->
                    _state.update { it.copy(debugUtxos = utxos, selectedDebugUtxo = utxos.firstOrNull()) }
                }
            }
        }
    }

    fun updateMessage(value: String) {
        val byteCount = runCatching { OpReturnPayload.byteCount(value) }.getOrDefault(81)
        _state.update { it.copy(messageText = value, messageByteCount = byteCount, error = null) }
    }

    fun updateFeeRate(value: String) = _state.update { it.copy(feeRateText = value, error = null) }

    fun updateComposeMode(mode: ComposeMode) = _state.update { it.copy(composeMode = mode, error = null) }

    fun updateAnchorAmount(value: String) = _state.update { it.copy(anchorAmountText = value, error = null) }

    fun updateRecipientAddress(value: String) = _state.update { it.copy(recipientAddress = value, error = null) }

    fun selectDebugUtxo(index: Int) {
        _state.update { it.copy(selectedDebugUtxo = it.debugUtxos.getOrNull(index), error = null) }
    }

    fun buildPreview() = launchBusy {
        val current = _state.value
        val payload = OpReturnPayload.fromText(current.messageText)
        val feeRate = current.feeRateText.toDoubleOrNull() ?: error("Enter a valid fee rate")
        val anchorAmount = current.anchorAmountText.toULongOrNull() ?: 1_000uL
        val mode = when (current.composeMode) {
            ComposeMode.STANDARD -> WriteMode.StandardMessage
            ComposeMode.ANCHOR_SELF -> WriteMode.AnchorToSelf(anchorAmount)
            ComposeMode.ANCHOR_RECIPIENT -> WriteMode.AnchorToRecipient(current.recipientAddress, anchorAmount)
            ComposeMode.DEBUG_CONSUME_UTXO -> current.selectedDebugUtxo?.let {
                WriteMode.DebugConsumeSelectedUtxo(it.outPoint, it.valueSats)
            } ?: error("Select exactly one UTXO to consume")
        }
        val preview = repository.buildMessagePreview(payload, feeRate, mode)
        _state.update {
            it.copy(
                preview = preview,
                permanentAcknowledged = false,
                screen = Screen.PREVIEW,
            )
        }
    }

    fun setPermanentAcknowledged(acknowledged: Boolean) {
        _state.update { it.copy(permanentAcknowledged = acknowledged) }
    }

    fun broadcast() = launchBusy {
        val current = _state.value
        check(current.permanentAcknowledged) { "Acknowledge the permanent public message before broadcasting" }
        val preview = current.preview ?: error("Transaction preview is missing")
        val result = repository.signAndBroadcast(preview)
        _state.update { it.copy(result = result, preview = null, screen = Screen.RESULT) }
    }

    fun startSweep() {
        check((_state.value.snapshot?.balance?.totalSats ?: 0uL) > 0uL) {
            "The wallet has no funds to sweep"
        }
        _state.update {
            it.copy(
                screen = Screen.SWEEP,
                sweepDestinationAddress = "",
                sweepPreview = null,
                sweepConfirmationText = "",
                sweepResult = null,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.estimatedFeeRate() }
                .onSuccess { estimate ->
                    _state.update { it.copy(sweepFeeRateText = "%.2f".format(estimate)) }
                }
        }
    }

    fun updateSweepDestination(value: String) =
        _state.update { it.copy(sweepDestinationAddress = value, error = null) }

    fun updateSweepFeeRate(value: String) =
        _state.update { it.copy(sweepFeeRateText = value, error = null) }

    fun buildSweepPreview() = launchBusy {
        val current = _state.value
        val feeRate = current.sweepFeeRateText.toDoubleOrNull() ?: error("Enter a valid fee rate")
        val synchronized = repository.sync()
        val preview = repository.buildSweepPreview(
            destinationAddress = current.sweepDestinationAddress,
            feeRateSatVb = feeRate,
        )
        _state.update {
            it.copy(
                snapshot = synchronized,
                sweepPreview = preview,
                sweepConfirmationText = "",
                screen = Screen.SWEEP_PREVIEW,
            )
        }
    }

    fun updateSweepConfirmation(value: String) =
        _state.update { it.copy(sweepConfirmationText = value, error = null) }

    fun broadcastSweep() = launchBusy {
        val current = _state.value
        check(current.sweepConfirmationText == SWEEP_CONFIRMATION) {
            "Type $SWEEP_CONFIRMATION to authorize the sweep"
        }
        val preview = current.sweepPreview ?: error("Sweep preview is missing")
        val result = repository.signAndBroadcastSweep(preview)
        _state.update {
            it.copy(
                sweepResult = result,
                sweepPreview = null,
                sweepConfirmationText = "",
                screen = Screen.SWEEP_RESULT,
            )
        }
    }

    fun showSettings() = _state.update { it.copy(screen = Screen.SETTINGS, error = null) }

    fun setBiometricUnlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBiometricUnlockEnabled(enabled)
            _state.update { it.copy(biometricUnlockEnabled = enabled) }
        }
    }

    fun lockIfEnabled() {
        val current = _state.value
        if (!current.biometricUnlockEnabled || current.screen == Screen.UNLOCK) return
        monitorJob?.cancel()
        monitorJob = null
        _state.update {
            it.copy(
                screen = Screen.UNLOCK,
                messageText = "",
                messageByteCount = 0,
                preview = null,
                permanentAcknowledged = false,
                sweepDestinationAddress = "",
                sweepPreview = null,
                sweepConfirmationText = "",
                busy = false,
                error = null,
            )
        }
    }

    fun goHome() {
        _state.update {
            it.copy(
                screen = Screen.HOME,
                preview = null,
                sweepPreview = null,
                sweepConfirmationText = "",
                error = null,
            )
        }
        refresh()
    }

    fun goBack() {
        _state.update { current ->
            current.copy(
                screen = when (current.screen) {
                    Screen.RESTORE -> Screen.WELCOME
                    Screen.VERIFY_RECOVERY -> Screen.RECOVERY_PHRASE
                    Screen.COMPOSE_MESSAGE,
                    Screen.PREVIEW,
                    Screen.RESULT,
                    Screen.SWEEP,
                    Screen.SWEEP_RESULT,
                    Screen.SETTINGS,
                    -> Screen.HOME
                    Screen.SWEEP_PREVIEW -> Screen.SWEEP
                    else -> current.screen
                },
                preview = if (current.screen == Screen.PREVIEW) null else current.preview,
                sweepPreview = if (current.screen == Screen.SWEEP_PREVIEW) null else current.sweepPreview,
                sweepConfirmationText = if (current.screen == Screen.SWEEP_PREVIEW) "" else current.sweepConfirmationText,
                restorePhrase = if (current.screen == Screen.RESTORE) "" else current.restorePhrase,
                error = null,
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun startMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = viewModelScope.launch {
            while (true) {
                delay(30.seconds)
                if (_state.value.screen in setOf(Screen.HOME, Screen.RESULT, Screen.SWEEP_RESULT)) refresh()
            }
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { block() }
                .onFailure { error -> _state.update { it.copy(error = error.userMessage()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    private fun canonicalWords(phrase: String): List<String> =
        phrase.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)

    private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank) ?: "Operation failed"

    class Factory(
        private val repository: WalletRepository,
        private val preferences: WalletPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WalletViewModel(repository, preferences) as T
    }

    private companion object {
        const val SWEEP_CONFIRMATION = "SWEEP"
    }
}
