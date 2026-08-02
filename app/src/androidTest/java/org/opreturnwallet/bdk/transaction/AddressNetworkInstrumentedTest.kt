package org.opreturnwallet.bdk.transaction

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.bitcoindevkit.Address
import org.bitcoindevkit.Network
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressNetworkInstrumentedTest {
    @Test
    fun mainnetAddressIsRejectedOnSignet() {
        assertThrows(Exception::class.java) {
            Address("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", Network.SIGNET)
        }
    }
}
