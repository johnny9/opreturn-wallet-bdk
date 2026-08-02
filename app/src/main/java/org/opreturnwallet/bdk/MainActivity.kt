package org.opreturnwallet.bdk

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import org.opreturnwallet.bdk.ui.OpReturnWalletApp
import org.opreturnwallet.bdk.ui.WalletViewModel

class MainActivity : FragmentActivity() {
    private lateinit var walletViewModel: WalletViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as OpReturnWalletApplication).container
        walletViewModel = ViewModelProvider(
            this,
            WalletViewModel.Factory(
                repository = container.walletRepository,
                preferences = container.preferences,
            ),
        )[WalletViewModel::class.java]
        setContent {
            OpReturnWalletApp(walletViewModel)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) walletViewModel.lockIfEnabled()
    }
}
