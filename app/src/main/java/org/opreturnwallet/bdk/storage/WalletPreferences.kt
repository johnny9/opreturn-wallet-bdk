package org.opreturnwallet.bdk.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.opreturnwallet.bdk.security.SensitiveModel
import org.opreturnwallet.bdk.wallet.WalletNetwork

private val Context.walletDataStore by preferencesDataStore(name = "wallet_metadata")

data class WalletMetadata(
    val walletId: String,
    val network: WalletNetwork,
    val fullScanComplete: Boolean,
    val biometricUnlockEnabled: Boolean,
) : SensitiveModel("WalletMetadata")

class WalletPreferences(private val context: Context) {
    private object Keys {
        val walletId = stringPreferencesKey("wallet_id")
        val network = stringPreferencesKey("wallet_network")
        val fullScanComplete = booleanPreferencesKey("full_scan_complete")
        val biometricUnlockEnabled = booleanPreferencesKey("biometric_unlock_enabled")
        val mainnetEnabled = booleanPreferencesKey("mainnet_enabled")
    }

    val metadata: Flow<WalletMetadata?> = context.walletDataStore.data.map { preferences ->
        val id = preferences[Keys.walletId] ?: return@map null
        val networkName = preferences[Keys.network] ?: return@map null
        val network = runCatching { WalletNetwork.valueOf(networkName) }.getOrNull() ?: return@map null
        WalletMetadata(
            walletId = id,
            network = network,
            fullScanComplete = preferences[Keys.fullScanComplete] ?: false,
            biometricUnlockEnabled = preferences[Keys.biometricUnlockEnabled] ?: false,
        )
    }

    val mainnetEnabled: Flow<Boolean> = context.walletDataStore.data.map {
        it[Keys.mainnetEnabled] ?: false
    }

    suspend fun currentMetadata(): WalletMetadata? = metadata.first()

    suspend fun saveMetadata(metadata: WalletMetadata) {
        context.walletDataStore.edit {
            it[Keys.walletId] = metadata.walletId
            it[Keys.network] = metadata.network.name
            it[Keys.fullScanComplete] = metadata.fullScanComplete
            it[Keys.biometricUnlockEnabled] = metadata.biometricUnlockEnabled
        }
    }

    suspend fun setFullScanComplete(complete: Boolean) {
        context.walletDataStore.edit { it[Keys.fullScanComplete] = complete }
    }

    suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        context.walletDataStore.edit { it[Keys.biometricUnlockEnabled] = enabled }
    }

    suspend fun setMainnetEnabled(enabled: Boolean) {
        context.walletDataStore.edit { it[Keys.mainnetEnabled] = enabled }
    }

    suspend fun clearWalletMetadata() {
        context.walletDataStore.edit {
            it.remove(Keys.walletId)
            it.remove(Keys.network)
            it.remove(Keys.fullScanComplete)
            it.remove(Keys.biometricUnlockEnabled)
        }
    }
}
