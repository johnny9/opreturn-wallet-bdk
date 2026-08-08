package org.opreturnwallet.bdk.transaction

import org.bitcoindevkit.OutPoint
import org.opreturnwallet.bdk.security.SensitiveModel

sealed interface WriteMode {
    data object StandardMessage : WriteMode

    data class AnchorToSelf(val amountSats: ULong = DEFAULT_ANCHOR_SATS) :
        SensitiveModel("AnchorToSelf"), WriteMode

    data class AnchorToRecipient(
        val address: String,
        val amountSats: ULong = DEFAULT_ANCHOR_SATS,
    ) : SensitiveModel("AnchorToRecipient"), WriteMode

    data class DebugConsumeSelectedUtxo(
        val outPoint: OutPoint,
        val inputValueSats: ULong,
    ) : SensitiveModel("DebugConsumeSelectedUtxo"), WriteMode
}

const val DEFAULT_ANCHOR_SATS = 1_000uL
