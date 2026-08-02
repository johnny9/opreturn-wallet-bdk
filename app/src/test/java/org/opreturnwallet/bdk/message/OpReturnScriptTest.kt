package org.opreturnwallet.bdk.message

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpReturnScriptTest {
    @Test
    fun `decodes direct push`() {
        assertArrayEquals("hello".toByteArray(), OpReturnScript.decode(byteArrayOf(0x6a, 0x05) + "hello".toByteArray()))
    }

    @Test
    fun `decodes pushdata1 used by 80 byte payload`() {
        val payload = ByteArray(80) { 0x61 }
        assertArrayEquals(payload, OpReturnScript.decode(byteArrayOf(0x6a, 0x4c, 0x50) + payload))
    }

    @Test
    fun `rejects a trailing second push`() {
        assertNull(OpReturnScript.decode(byteArrayOf(0x6a, 0x01, 0x61, 0x01, 0x62)))
    }
}
