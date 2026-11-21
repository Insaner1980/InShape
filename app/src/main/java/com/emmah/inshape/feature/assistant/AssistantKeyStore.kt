package com.emmah.inshape.feature.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class KeyStatus { MISSING, SAVED, UNREADABLE }

interface AssistantCredentials {
    suspend fun status(): KeyStatus
    suspend fun save(value: String)
    suspend fun remove()
}

internal class AssistantKeyStore(
    context: Context,
    directory: File = context.noBackupFilesDir,
    private val alias: String = "inshape.assistant.openai.v1"
) : AssistantCredentials {
    private val record = AtomicFile(File(directory, "openai-credential.bin"))
    private val lock = Any()

    override suspend fun status(): KeyStatus = withContext(Dispatchers.IO) {
        try {
            if (read() == null) KeyStatus.MISSING else KeyStatus.SAVED
        } catch (_: AssistantFailure) {
            KeyStatus.UNREADABLE
        }
    }

    internal fun read(): String? = synchronized(lock) {
        if (!record.baseFile.exists()) return null
        try {
            require(record.baseFile.length() in 30L..4200L)
            val blob = ByteBuffer.wrap(record.readFully())
            require(blob.get().toInt() == 1)
            val iv = ByteArray(12).also { blob.get(it) }
            val ciphertext = ByteArray(blob.remaining()).also { blob.get(it) }
            val secret = keyStore().getKey(alias, null) as? SecretKey ?: error("Missing device key")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).decodeToString(throwOnInvalidSequence = true)
        } catch (_: Exception) {
            throw AssistantFailure(AssistantFailureKind.UNREADABLE_KEY)
        }
    }

    override suspend fun save(value: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val text = value.trim()
            require(text.length in 1..1024 && text.all { it in '!'..'~' }) { "Enter a valid API key." }
            val store = keyStore()
            val key = store.getKey(alias, null) as? SecretKey ?: KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            ).apply {
                init(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }.generateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val raw = text.encodeToByteArray()
            val ciphertext = try {
                cipher.doFinal(raw)
            } finally {
                raw.fill(0)
            }
            require(cipher.iv.size == 12)
            val stream = record.startWrite()
            try {
                stream.write(1)
                stream.write(cipher.iv)
                stream.write(ciphertext)
                record.finishWrite(stream)
            } catch (failure: Exception) {
                record.failWrite(stream)
                throw failure
            }
        }
    }

    override suspend fun remove() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            record.delete()
            keyStore().deleteEntry(alias)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
