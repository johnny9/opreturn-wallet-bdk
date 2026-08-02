package org.opreturnwallet.bdk.message

/** Conservative parser for the single-push script produced by BDK TxBuilder.addData. */
object OpReturnScript {
    private const val OP_RETURN: Int = 0x6a
    private const val OP_PUSHDATA1: Int = 0x4c

    fun decode(script: ByteArray): ByteArray? {
        if (script.isEmpty() || script[0].toInt() and 0xff != OP_RETURN) return null
        if (script.size == 1) return ByteArray(0)

        val opcode = script[1].toInt() and 0xff
        val payloadStart: Int
        val payloadLength: Int
        when {
            opcode in 1..75 -> {
                payloadStart = 2
                payloadLength = opcode
            }
            opcode == OP_PUSHDATA1 && script.size >= 3 -> {
                payloadStart = 3
                payloadLength = script[2].toInt() and 0xff
            }
            else -> return null
        }

        if (payloadStart + payloadLength != script.size) return null
        return script.copyOfRange(payloadStart, script.size)
    }

    fun isOpReturn(script: ByteArray): Boolean = script.firstOrNull()?.toInt()?.and(0xff) == OP_RETURN
}
