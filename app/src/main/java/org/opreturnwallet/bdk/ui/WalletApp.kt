@file:OptIn(ExperimentalMaterial3Api::class)

package org.opreturnwallet.bdk.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import org.opreturnwallet.bdk.BuildConfig
import org.opreturnwallet.bdk.transaction.PreviewOutputKind
import org.opreturnwallet.bdk.transaction.TransactionPreview
import org.opreturnwallet.bdk.transaction.WriteMode
import org.opreturnwallet.bdk.wallet.MessageTransactionRecord
import org.opreturnwallet.bdk.wallet.WalletNetwork
import org.opreturnwallet.bdk.wallet.WalletSnapshot

@Composable
fun OpReturnWalletApp(viewModel: WalletViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val sensitive = state.screen in setOf(
        Screen.RESTORE,
        Screen.RECOVERY_PHRASE,
        Screen.VERIFY_RECOVERY,
        Screen.UNLOCK,
    )
    SecureScreen(sensitive)

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    OpReturnTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (state.screen !in setOf(Screen.LOADING, Screen.WELCOME, Screen.UNLOCK, Screen.HOME)) {
                    TopAppBar(
                        title = { Text(screenTitle(state.screen)) },
                        navigationIcon = {
                            IconButton(onClick = viewModel::goBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                }
            },
        ) { padding ->
            Surface(Modifier.fillMaxSize().padding(padding)) {
                when (state.screen) {
                    Screen.LOADING -> LoadingScreen("Opening wallet…")
                    Screen.WELCOME -> WelcomeScreen(state, viewModel)
                    Screen.RESTORE -> RestoreScreen(state, viewModel)
                    Screen.RECOVERY_PHRASE -> RecoveryPhraseScreen(state, viewModel)
                    Screen.VERIFY_RECOVERY -> VerifyRecoveryScreen(state, viewModel)
                    Screen.UNLOCK -> UnlockScreen(state, viewModel)
                    Screen.HOME -> HomeScreen(state, viewModel)
                    Screen.COMPOSE_MESSAGE -> ComposeMessageScreen(state, viewModel)
                    Screen.PREVIEW -> PreviewScreen(state, viewModel)
                    Screen.RESULT -> ResultScreen(state, viewModel)
                    Screen.SWEEP -> SweepScreen(state, viewModel)
                    Screen.SWEEP_PREVIEW -> SweepPreviewScreen(state, viewModel)
                    Screen.SWEEP_RESULT -> SweepResultScreen(state, viewModel)
                    Screen.SETTINGS -> SettingsScreen(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(state: WalletUiState, viewModel: WalletViewModel) {
    var advancedExpanded by remember { mutableStateOf(false) }
    ScreenColumn {
        Text(
            if (BuildConfig.MAINNET_TRIAL) "Mainnet trial wallet" else "Write to Bitcoin",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "A focused wallet for publishing one conservative UTF-8 OP_RETURN message while returning the rest to change.",
            style = MaterialTheme.typography.bodyLarge,
        )
        PrivacyCard()
        if (BuildConfig.MAINNET_TRIAL) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("REAL BITCOIN", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Text("This separately installed build only creates Mainnet wallets. Use a fresh phrase and a small test balance.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.mainnetEnabled,
                            onCheckedChange = viewModel::setMainnetEnabled,
                        )
                        Text("I understand Mainnet transactions spend real bitcoin")
                    }
                }
            }
        }
        Text("Network", style = MaterialTheme.typography.titleMedium)
        NetworkSelector(state, viewModel)
        Button(
            onClick = viewModel::createWallet,
            enabled = !state.busy && (!BuildConfig.MAINNET_TRIAL || state.mainnetEnabled),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create software wallet") }
        OutlinedButton(
            onClick = viewModel::showRestore,
            enabled = !state.busy && (!BuildConfig.MAINNET_TRIAL || state.mainnetEnabled),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restore from recovery phrase") }
        if (!BuildConfig.MAINNET_TRIAL) {
            TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                Text(if (advancedExpanded) "Hide advanced networks" else "Advanced network settings")
            }
            if (advancedExpanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.mainnetEnabled,
                        onCheckedChange = viewModel::setMainnetEnabled,
                    )
                    Text("I understand this enables real-bitcoin mainnet transactions")
                }
            }
        }
        BusyIndicator(state.busy)
    }
}

@Composable
private fun NetworkSelector(state: WalletUiState, viewModel: WalletViewModel) {
    val networks = if (BuildConfig.MAINNET_TRIAL) listOf(WalletNetwork.MAINNET) else buildList {
        add(WalletNetwork.REGTEST)
        add(WalletNetwork.SIGNET)
        add(WalletNetwork.TESTNET4)
        if (state.mainnetEnabled) add(WalletNetwork.MAINNET)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        networks.forEach { network ->
            FilterChip(
                selected = state.selectedNetwork == network,
                onClick = { viewModel.selectNetwork(network) },
                enabled = !network.isMainnet || state.mainnetEnabled,
                label = { Text(network.displayName) },
            )
        }
    }
}

@Composable
private fun RestoreScreen(state: WalletUiState, viewModel: WalletViewModel) {
    ScreenColumn {
        Text("Restore wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Enter the BIP39 words exactly. They are processed locally and encrypted before storage.")
        NetworkSelector(state, viewModel)
        OutlinedTextField(
            value = state.restorePhrase,
            onValueChange = viewModel::updateRestorePhrase,
            label = { Text("Recovery phrase") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = viewModel::restoreWallet,
            enabled = state.restorePhrase.isNotBlank() && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restore and scan") }
        BusyIndicator(state.busy)
    }
}

@Composable
private fun RecoveryPhraseScreen(state: WalletUiState, viewModel: WalletViewModel) {
    val words = state.recoveryPhrase.orEmpty().split(" ")
    ScreenColumn {
        Text("Save these recovery words", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Write them down offline in order. Anyone with these words can spend the wallet.")
        Card(Modifier.fillMaxWidth()) {
            SelectionContainer {
                Text(
                    words.mapIndexed { index, word -> "${index + 1}. $word" }.joinToString("   "),
                    modifier = Modifier.padding(20.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Text("Screenshots are blocked on this screen. The app will ask you to re-enter the phrase next.")
        Button(onClick = viewModel::continueRecoveryVerification, modifier = Modifier.fillMaxWidth()) {
            Text("I wrote it down")
        }
    }
}

@Composable
private fun VerifyRecoveryScreen(state: WalletUiState, viewModel: WalletViewModel) {
    ScreenColumn {
        Text("Verify recovery phrase", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Re-enter all words in order. This confirms your backup before the wallet can be used.")
        OutlinedTextField(
            value = state.verificationPhrase,
            onValueChange = viewModel::updateVerificationPhrase,
            minLines = 4,
            label = { Text("Recovery phrase") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = viewModel::verifyRecoveryPhrase,
            enabled = state.verificationPhrase.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Verify and open wallet") }
    }
}

@Composable
private fun UnlockScreen(state: WalletUiState, viewModel: WalletViewModel) {
    ScreenColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Wallet locked", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Authenticate on this device to decrypt and open the wallet.")
        BiometricButton(
            label = "Unlock",
            enabled = !state.busy,
            onAuthenticated = viewModel::unlock,
        )
        BusyIndicator(state.busy)
    }
}

@Composable
private fun HomeScreen(state: WalletUiState, viewModel: WalletViewModel) {
    val snapshot = state.snapshot
    if (snapshot == null) {
        LoadingScreen("Synchronizing wallet…")
        return
    }
    var showReceive by remember { mutableStateOf(false) }
    if (showReceive) {
        ReceiveDialog(snapshot.receiveAddress, onDismiss = { showReceive = false })
    }
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (BuildConfig.MAINNET_TRIAL) "OP_RETURN Wallet · Mainnet Trial" else "OP_RETURN Wallet",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(snapshot.network.displayName, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "Sync") }
            IconButton(onClick = viewModel::showSettings) { Icon(Icons.Default.Settings, "Settings") }
        }
        BalanceCard(snapshot)
        if (BuildConfig.MAINNET_TRIAL) {
            Text(
                "This isolated build uses real bitcoin. Verify every address and fee before signing.",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            when (state.syncStatus) {
                SyncStatus.SYNCING -> "Synchronizing…"
                SyncStatus.CURRENT -> "Wallet is synchronized"
                SyncStatus.ERROR -> "Synchronization needs attention"
                SyncStatus.IDLE -> "Not synchronized"
            },
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { showReceive = true }, modifier = Modifier.weight(1f)) { Text("Receive") }
            Button(onClick = viewModel::startCompose, modifier = Modifier.weight(1f)) { Text("Write message") }
        }
        OutlinedButton(
            onClick = viewModel::startSweep,
            enabled = snapshot.balance.totalSats > 0uL && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sweep wallet to external address") }
        HorizontalDivider()
        Text("Previous message transactions", style = MaterialTheme.typography.titleLarge)
        if (snapshot.messages.isEmpty()) {
            Text("No OP_RETURN messages found in this wallet yet.")
        } else {
            snapshot.messages.forEach { MessageCard(it, snapshot.network) }
        }
    }
}

@Composable
private fun BalanceCard(snapshot: WalletSnapshot) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Confirmed", style = MaterialTheme.typography.labelLarge)
            Text("${snapshot.balance.confirmedSats} sats", style = MaterialTheme.typography.headlineMedium)
            Text("Pending: ${snapshot.balance.pendingSats} sats")
        }
    }
}

@Composable
private fun MessageCard(record: MessageTransactionRecord, network: WalletNetwork) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(record.message, maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(if (record.pending) "Pending" else "Confirmed at block ${record.blockHeight}")
            Text(record.txid, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            network.explorerUrl(record.txid)?.let { url ->
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }) {
                    Text("Open in explorer")
                }
            }
        }
    }
}

@Composable
private fun ComposeMessageScreen(state: WalletUiState, viewModel: WalletViewModel) {
    ScreenColumn {
        Text("Compose message", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Text(
                "The message will be public and effectively permanent. It cannot be edited or deleted.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedTextField(
            value = state.messageText,
            onValueChange = viewModel::updateMessage,
            label = { Text("UTF-8 message") },
            minLines = 5,
            supportingText = {
                Text("${state.messageByteCount} / 80 bytes · UTF-8", color = if (state.messageByteCount > 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            },
            isError = state.messageByteCount > 80,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.feeRateText,
            onValueChange = viewModel::updateFeeRate,
            label = { Text("Fee rate (sat/vB)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Transaction mode", style = MaterialTheme.typography.titleMedium)
        ModeOption("Standard message", "Zero-sat OP_RETURN plus wallet change", state.composeMode == ComposeMode.STANDARD) {
            viewModel.updateComposeMode(ComposeMode.STANDARD)
        }
        ModeOption("Anchor to self", "1,000-sat default output plus wallet change", state.composeMode == ComposeMode.ANCHOR_SELF) {
            viewModel.updateComposeMode(ComposeMode.ANCHOR_SELF)
        }
        ModeOption("Anchor to recipient", "Ordinary recipient output plus wallet change", state.composeMode == ComposeMode.ANCHOR_RECIPIENT) {
            viewModel.updateComposeMode(ComposeMode.ANCHOR_RECIPIENT)
        }
        if (BuildConfig.ENABLE_DEBUG_CONSUME_UTXO && state.snapshot?.network == WalletNetwork.REGTEST) {
            ModeOption(
                "Debug: consume selected UTXO",
                "No change. The entire selected input remainder becomes the fee.",
                state.composeMode == ComposeMode.DEBUG_CONSUME_UTXO,
            ) { viewModel.updateComposeMode(ComposeMode.DEBUG_CONSUME_UTXO) }
        }
        if (state.composeMode in setOf(ComposeMode.ANCHOR_SELF, ComposeMode.ANCHOR_RECIPIENT)) {
            OutlinedTextField(
                value = state.anchorAmountText,
                onValueChange = viewModel::updateAnchorAmount,
                label = { Text("Anchor amount (sats)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.composeMode == ComposeMode.ANCHOR_RECIPIENT) {
            OutlinedTextField(
                value = state.recipientAddress,
                onValueChange = viewModel::updateRecipientAddress,
                label = { Text("Recipient address") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.composeMode == ComposeMode.DEBUG_CONSUME_UTXO) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "DANGER: no change is created. The selected UTXO value becomes the mining fee. Regtest debug builds only.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
            state.debugUtxos.forEachIndexed { index, utxo ->
                FilterChip(
                    selected = state.selectedDebugUtxo == utxo,
                    onClick = { viewModel.selectDebugUtxo(index) },
                    label = { Text(utxo.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Button(
            onClick = viewModel::buildPreview,
            enabled = state.messageByteCount in 1..80 && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Build transaction preview") }
        BusyIndicator(state.busy)
    }
}

@Composable
private fun ModeOption(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PreviewScreen(state: WalletUiState, viewModel: WalletViewModel) {
    val preview = state.preview ?: return LoadingScreen("Building preview…")
    ScreenColumn {
        Text("Transaction preview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        LabeledValue("Message", preview.payload.text)
        LabeledValue("UTF-8 bytes", "${preview.payload.byteCount} / 80")
        LabeledValue("Hex", preview.payload.hex, monospace = true)
        HorizontalDivider()
        Text("Outputs", style = MaterialTheme.typography.titleMedium)
        preview.outputs.forEach { output ->
            val label = when (output.kind) {
                PreviewOutputKind.OP_RETURN -> "OP_RETURN"
                PreviewOutputKind.ANCHOR -> "Anchor${output.address?.let { " · $it" } ?: ""}"
                PreviewOutputKind.CHANGE -> "Change to this wallet"
            }
            Text("${output.valueSats} sats    $label")
        }
        LabeledValue("Estimated fee", "${preview.feeSats} sats at ${"%.2f".format(preview.feeRateSatVb)} sat/vB")
        LabeledValue("Estimated size", "${preview.estimatedVbytes} vB")
        if (preview.feeExceedsAnchor) {
            Text("Warning: the fee exceeds the anchor amount.", color = MaterialTheme.colorScheme.error)
        }
        if (preview.mode is WriteMode.DebugConsumeSelectedUtxo) {
            Text(
                "DANGER: ${preview.feeSats} sats — the full selected input — will be paid as fee.",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.permanentAcknowledged,
                    onCheckedChange = viewModel::setPermanentAcknowledged,
                )
                Text("This message will be public and effectively permanent. It cannot be edited or deleted.")
            }
        }
        Button(
            onClick = viewModel::broadcast,
            enabled = state.permanentAcknowledged && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign and broadcast") }
        BusyIndicator(state.busy)
    }
}

@Composable
private fun ResultScreen(state: WalletUiState, viewModel: WalletViewModel) {
    val result = state.result ?: return LoadingScreen("Finishing transaction…")
    val network = state.snapshot?.network ?: state.selectedNetwork
    val context = LocalContext.current
    ScreenColumn {
        Text("Message published", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        LabeledValue("Status", if (result.pending) "Pending" else "Confirmed at block ${result.blockHeight}")
        LabeledValue("Decoded message", result.message)
        LabeledValue("Transaction ID", result.txid, monospace = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copy(context, "Transaction ID", result.txid) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Copy transaction ID")
            }
            network.explorerUrl(result.txid)?.let { url ->
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }) {
                    Text("Explorer")
                }
            }
        }
        Button(onClick = viewModel::goHome, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
private fun SweepScreen(state: WalletUiState, viewModel: WalletViewModel) {
    val snapshot = state.snapshot ?: return LoadingScreen("Loading wallet…")
    ScreenColumn {
        Text("Sweep wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MOVE ALL FUNDS", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Text("Every currently spendable UTXO will be sent to one external address. The fee is deducted from that output and no change returns to this wallet.")
                Text("Sweeping does not erase the encrypted recovery phrase from this phone.")
            }
        }
        LabeledValue("Current wallet balance", "${snapshot.balance.totalSats} sats")
        OutlinedTextField(
            value = state.sweepDestinationAddress,
            onValueChange = viewModel::updateSweepDestination,
            label = { Text("External ${snapshot.network.displayName} destination") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.sweepFeeRateText,
            onValueChange = viewModel::updateSweepFeeRate,
            label = { Text("Fee rate (sat/vB)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("The wallet will synchronize again before constructing the preview.")
        Button(
            onClick = viewModel::buildSweepPreview,
            enabled = state.sweepDestinationAddress.isNotBlank() && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Build sweep preview") }
        BusyIndicator(state.busy)
    }
}

@Composable
private fun SweepPreviewScreen(state: WalletUiState, viewModel: WalletViewModel) {
    val preview = state.sweepPreview ?: return LoadingScreen("Building sweep preview…")
    ScreenColumn {
        Text("Sweep preview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Text(
                "Verify this destination on the receiving wallet. This transaction sends the full spendable balance away from this phone with no change.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
        LabeledValue("Destination", preview.destinationAddress, monospace = true)
        LabeledValue("Inputs", "${preview.inputCount} UTXO(s) · ${preview.inputValueSats} sats")
        LabeledValue("Recipient receives", "${preview.recipientValueSats} sats")
        LabeledValue("Mining fee", "${preview.feeSats} sats at ${"%.2f".format(preview.feeRateSatVb)} sat/vB")
        LabeledValue("Estimated size", "${preview.estimatedVbytes} vB")
        LabeledValue("Change", "0 sats · no wallet change output")
        HorizontalDivider()
        Text("Type SWEEP to confirm", fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = state.sweepConfirmationText,
            onValueChange = viewModel::updateSweepConfirmation,
            label = { Text("Confirmation") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = viewModel::broadcastSweep,
            enabled = state.sweepConfirmationText == "SWEEP" && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign and broadcast sweep") }
        BusyIndicator(state.busy)
    }
}

@Composable
private fun SweepResultScreen(state: WalletUiState, viewModel: WalletViewModel) {
    val result = state.sweepResult ?: return LoadingScreen("Finishing sweep…")
    val network = state.snapshot?.network ?: state.selectedNetwork
    val context = LocalContext.current
    ScreenColumn {
        Text("Sweep broadcast", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        LabeledValue("Status", if (result.pending) "Pending" else "Confirmed at block ${result.blockHeight}")
        LabeledValue("Sent", "${result.amountSats} sats")
        LabeledValue("Fee", "${result.feeSats} sats")
        LabeledValue("Destination", result.destinationAddress, monospace = true)
        LabeledValue("Transaction ID", result.txid, monospace = true)
        Text("Wait for confirmation in the receiving wallet before removing this app or its recovery phrase.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copy(context, "Transaction ID", result.txid) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Copy transaction ID")
            }
            network.explorerUrl(result.txid)?.let { url ->
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }) {
                    Text("Explorer")
                }
            }
        }
        Button(onClick = viewModel::goHome, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
private fun SettingsScreen(state: WalletUiState, viewModel: WalletViewModel) {
    ScreenColumn {
        Text("Safety and privacy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        PrivacyCard()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.biometricUnlockEnabled,
                onCheckedChange = { enabled ->
                    if (!enabled) viewModel.setBiometricUnlockEnabled(false)
                },
            )
            Column(Modifier.weight(1f)) {
                Text("Biometric unlock", fontWeight = FontWeight.Medium)
                Text("Require device authentication when the app starts", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!state.biometricUnlockEnabled) {
            BiometricButton(
                label = "Enable biometric unlock",
                onAuthenticated = { viewModel.setBiometricUnlockEnabled(true) },
            )
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.mainnetEnabled,
                onCheckedChange = viewModel::setMainnetEnabled,
                enabled = !BuildConfig.MAINNET_TRIAL,
            )
            Column {
                Text(if (BuildConfig.MAINNET_TRIAL) "Isolated Mainnet build" else "Enable mainnet", fontWeight = FontWeight.Medium)
                Text(
                    if (BuildConfig.MAINNET_TRIAL) "This installation only permits real-bitcoin Mainnet wallets" else "Allows wallets that spend real bitcoin",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text("Custom Esplora servers are planned. Public endpoints can correlate your wallet addresses.")
    }
}

@Composable
private fun BiometricButton(
    label: String,
    enabled: Boolean = true,
    onAuthenticated: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val available = BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    Button(
        enabled = enabled && available && activity != null,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val prompt = BiometricPrompt(
                checkNotNull(activity),
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onAuthenticated()
                    }
                },
            )
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock OP_RETURN Wallet")
                .setSubtitle("Authenticate using this device")
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(promptInfo)
        },
    ) { Text(if (available) label else "Device authentication unavailable") }
}

@Composable
private fun ReceiveDialog(address: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receive bitcoin") },
        text = {
            SelectionContainer {
                Text(address, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = {
            TextButton(onClick = { copy(context, "Bitcoin address", address) }) { Text("Copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PrivacyCard() {
    Card(Modifier.fillMaxWidth()) {
        Text(
            "Public Esplora servers can correlate wallet addresses. Messages are public by design; recovery words never leave this device through the app.",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun LabeledValue(label: String, value: String, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        SelectionContainer {
            Text(value, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default)
        }
    }
}

@Composable
private fun BusyIndicator(busy: Boolean) {
    if (busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
}

@Composable
private fun LoadingScreen(message: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message)
    }
}

@Composable
private fun ScreenColumn(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

@Composable
private fun SecureScreen(enabled: Boolean) {
    val activity = LocalActivity.current
    DisposableEffect(activity, enabled) {
        if (enabled) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (enabled) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
private fun OpReturnTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

private fun screenTitle(screen: Screen): String = when (screen) {
    Screen.RESTORE -> "Restore"
    Screen.RECOVERY_PHRASE -> "Recovery phrase"
    Screen.VERIFY_RECOVERY -> "Verify backup"
    Screen.COMPOSE_MESSAGE -> "Write message"
    Screen.PREVIEW -> "Preview"
    Screen.RESULT -> "Result"
    Screen.SWEEP -> "Sweep wallet"
    Screen.SWEEP_PREVIEW -> "Sweep preview"
    Screen.SWEEP_RESULT -> "Sweep result"
    Screen.SETTINGS -> "Settings"
    else -> "OP_RETURN Wallet"
}

private fun copy(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}
