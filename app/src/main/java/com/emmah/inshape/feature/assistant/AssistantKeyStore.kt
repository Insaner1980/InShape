package com.emmah.inshape.feature.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineDispatcher
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
    private val alias: String = "inshape.assistant.openai.v1",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val openKeyStore: () -> KeyStore = { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }
) : AssistantCredentials {
    private val record = AtomicFile(File(directory, "openai-credential.bin"))
    private val authenticatedAlias = "$alias.authenticated"
    private val lock = Any()

    override suspend fun status(): KeyStatus = withContext(ioDispatcher) {
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
            val store = openKeyStore()
            when (blob.get().toInt()) {
                1 -> decodeCredential(decrypt(blob, store.credentialKey(alias)))
                2 -> migrateCredential(store, blob)
                else -> error("Invalid credential version")
            }
        } catch (_: Exception) {
            throw AssistantFailure(AssistantFailureKind.UNREADABLE_KEY)
        }
    }

    override suspend fun save(value: String) = withContext(ioDispatcher) {
        synchronized(lock) {
            val text = value.trim()
            require(text.length in 1..1024 && text.all { it in '!'..'~' }) { "Enter a valid API key." }
            val store = openKeyStore()
            val raw = text.encodeToByteArray()
            try {
                writeCredential(store, raw)
            } finally {
                raw.fill(0)
            }
        }
    }

    override suspend fun remove() = withContext(ioDispatcher) {
        synchronized(lock) {
            record.delete()
            val store = openKeyStore()
            store.deleteEntry(alias)
            store.deleteEntry(authenticatedAlias)
        }
    }

    private fun migrateCredential(store: KeyStore, blob: ByteBuffer): String {
        val raw = decrypt(blob, store.credentialKey(authenticatedAlias))
        try {
            val credential = raw.decodeToString(throwOnInvalidSequence = true)
            writeCredential(store, raw)
            return credential
        } finally {
            raw.fill(0)
        }
    }

    private fun decrypt(blob: ByteBuffer, key: SecretKey): ByteArray {
        val iv = ByteArray(12).also { blob.get(it) }
        val ciphertext = ByteArray(blob.remaining()).also { blob.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun decodeCredential(raw: ByteArray): String = try {
        raw.decodeToString(throwOnInvalidSequence = true)
    } finally {
        raw.fill(0)
    }

    private fun KeyStore.credentialKey(name: String): SecretKey = getKey(name, null) as? SecretKey ?: error("Missing device key")

    private fun generateKey(): SecretKey = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        "AndroidKeyStore"
    ).apply {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        init(spec)
    }.generateKey()

    private fun writeCredential(store: KeyStore, raw: ByteArray) {
        val key = store.getKey(alias, null) as? SecretKey ?: generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(raw)
        require(cipher.iv.size == 12)
        val encoded = ByteBuffer.allocate(1 + cipher.iv.size + ciphertext.size)
            .put(1.toByte()).put(cipher.iv).put(ciphertext).array()
        val stream = record.startWrite()
        var written = false
        try {
            stream.write(encoded)
            record.finishWrite(stream)
            written = true
        } finally {
            if (!written) record.failWrite(stream)
        }
        check(record.readFully().contentEquals(encoded))
        try {
            if (store.containsAlias(authenticatedAlias)) store.deleteEntry(authenticatedAlias)
        } catch (_: KeyStoreException) {
            // The previous key does not protect the record after this write.
        }
    }
}
