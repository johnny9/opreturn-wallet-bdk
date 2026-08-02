package org.opreturnwallet.bdk.message

import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

const val CONSERVATIVE_OP_RETURN_MAX_BYTES = 80

data class OpReturnPayload(
    val text: String,
    val utf8Bytes: ByteArray,
    val hex: String,
    val byteCount: Int,
) {
    companion object {
        fun fromText(
            text: String,
            maximumBytes: Int = CONSERVATIVE_OP_RETURN_MAX_BYTES,
        ): OpReturnPayload {
            require(maximumBytes in 1..CONSERVATIVE_OP_RETURN_MAX_BYTES) {
                "Maximum payload must be between 1 and $CONSERVATIVE_OP_RETURN_MAX_BYTES bytes"
            }
            val bytes = encodeUtf8Strict(text)
            if (bytes.isEmpty()) throw PayloadValidationException.Empty
            if (bytes.size > maximumBytes) {
                throw PayloadValidationException.Oversized(bytes.size, maximumBytes)
            }
            return OpReturnPayload(
                text = text,
                utf8Bytes = bytes,
                hex = bytes.toHex(),
                byteCount = bytes.size,
            )
        }

        fun byteCount(text: String): Int = encodeUtf8Strict(text).size

        private fun encodeUtf8Strict(text: String): ByteArray {
            val encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return try {
                val encoded = encoder.encode(java.nio.CharBuffer.wrap(text))
                ByteArray(encoded.remaining()).also(encoded::get)
            } catch (_: Exception) {
                throw PayloadValidationException.InvalidUnicode
            }
        }
    }
}

sealed class PayloadValidationException(message: String) : IllegalArgumentException(message) {
    data object Empty : PayloadValidationException("Message must not be empty")

    data object InvalidUnicode : PayloadValidationException("Message contains malformed Unicode")

    data class Oversized(val actualBytes: Int, val maximumBytes: Int) :
        PayloadValidationException("Message is $actualBytes bytes; maximum is $maximumBytes")
}

fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
