package org.opreturnwallet.bdk.transaction

import org.bitcoindevkit.OutPoint

sealed interface WriteMode {
    data object StandardMessage : WriteMode

    data class AnchorToSelf(val amountSats: ULong = DEFAULT_ANCHOR_SATS) : WriteMode

    data class AnchorToRecipient(
        val address: String,
        val amountSats: ULong = DEFAULT_ANCHOR_SATS,
    ) : WriteMode

    data class DebugConsumeSelectedUtxo(
        val outPoint: OutPoint,
        val inputValueSats: ULong,
    ) : WriteMode
}

const val DEFAULT_ANCHOR_SATS = 1_000uL
