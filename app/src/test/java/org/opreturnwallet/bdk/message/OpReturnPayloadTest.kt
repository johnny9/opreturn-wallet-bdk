package org.opreturnwallet.bdk.message

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpReturnPayloadTest {
    @Test
    fun `empty message is rejected`() {
        assertThrows(PayloadValidationException.Empty::class.java) {
            OpReturnPayload.fromText("")
        }
    }

    @Test
    fun `one byte message is accepted`() {
        val payload = OpReturnPayload.fromText("a")
        assertEquals(1, payload.byteCount)
        assertEquals("61", payload.hex)
    }

    @Test
    fun `exactly 80 bytes is accepted`() {
        assertEquals(80, OpReturnPayload.fromText("a".repeat(80)).byteCount)
    }

    @Test
    fun `81 bytes is rejected`() {
        val error = assertThrows(PayloadValidationException.Oversized::class.java) {
            OpReturnPayload.fromText("a".repeat(81))
        }
        assertEquals(81, error.actualBytes)
    }

    @Test
    fun `multibyte utf8 is counted by encoded bytes`() {
        assertEquals(2, OpReturnPayload.fromText("é").byteCount)
        assertEquals(4, OpReturnPayload.fromText("🚀").byteCount)
    }

    @Test
    fun `embedded newline is preserved`() {
        val payload = OpReturnPayload.fromText("line one\nline two")
        assertArrayEquals("line one\nline two".toByteArray(Charsets.UTF_8), payload.utf8Bytes)
    }

    @Test
    fun `hex representation is exact`() {
        assertEquals("48656c6c6f20f09f9a80", OpReturnPayload.fromText("Hello 🚀").hex)
    }

    @Test
    fun `malformed unicode is rejected rather than replaced`() {
        assertThrows(PayloadValidationException.InvalidUnicode::class.java) {
            OpReturnPayload.fromText("\uD800")
        }
    }
}
