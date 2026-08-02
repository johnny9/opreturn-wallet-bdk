package org.opreturnwallet.bdk.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedSeedStore(context: Context) {
    private val secretsDirectory = File(context.filesDir, "secrets")
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    init {
        check(secretsDirectory.exists() || secretsDirectory.mkdirs()) {
            "Unable to create secure seed directory"
        }
    }

    fun write(walletId: String, mnemonic: String) {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(walletId))
        val encrypted = cipher.doFinal(mnemonic.toByteArray(Charsets.UTF_8))
        val atomicFile = AtomicFile(secretFile(walletId))
        val output = atomicFile.startWrite()
        try {
            val data = DataOutputStream(output)
            data.writeInt(FILE_VERSION)
            data.writeInt(cipher.iv.size)
            data.write(cipher.iv)
            data.writeInt(encrypted.size)
            data.write(encrypted)
            data.flush()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    fun read(walletId: String): String {
        val atomicFile = AtomicFile(secretFile(walletId))
        require(atomicFile.baseFile.exists()) { "Encrypted wallet seed is missing" }
        val (iv, ciphertext) = DataInputStream(atomicFile.openRead()).use { data ->
            require(data.readInt() == FILE_VERSION) { "Unsupported encrypted seed format" }
            val ivSize = data.readInt()
            require(ivSize in 12..32) { "Invalid encrypted seed IV" }
            val iv = ByteArray(ivSize).also(data::readFully)
            val ciphertextSize = data.readInt()
            require(ciphertextSize in 1..MAX_CIPHERTEXT_BYTES) { "Invalid encrypted seed payload" }
            val ciphertext = ByteArray(ciphertextSize).also(data::readFully)
            require(data.read() == -1) { "Unexpected encrypted seed data" }
            iv to ciphertext
        }

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey(walletId), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    fun delete(walletId: String) {
        AtomicFile(secretFile(walletId)).delete()
        keyStore.deleteEntry(alias(walletId))
    }

    private fun getOrCreateKey(walletId: String): SecretKey {
        return (keyStore.getKey(alias(walletId), null) as? SecretKey) ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias(walletId),
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generator.generateKey()
        }
    }

    private fun getExistingKey(walletId: String): SecretKey =
        keyStore.getKey(alias(walletId), null) as? SecretKey
            ?: error("Android Keystore key is missing")

    private fun secretFile(walletId: String) = File(secretsDirectory, "$walletId.seed")

    private fun alias(walletId: String) = "opreturn-wallet-$walletId"

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val FILE_VERSION = 1
        const val MAX_CIPHERTEXT_BYTES = 4096
    }
}
