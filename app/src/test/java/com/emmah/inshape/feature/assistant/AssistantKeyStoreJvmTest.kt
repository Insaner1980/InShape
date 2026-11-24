package com.emmah.inshape.feature.assistant

import android.app.KeyguardManager
import android.content.Context
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [AssistantKeyStoreJvmTest.AtomicRename::class])
@ConscryptMode(ConscryptMode.Mode.OFF)
class AssistantKeyStoreJvmTest {
    @get:Rule val files = TemporaryFolder()
    private val alias = "fixture-credential"
    private val previousAlias = "$alias.authenticated"

    @Test fun savedKeyCanBeReopenedAndReplacedWithoutDeviceAuthentication() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keyguard = shadowOf(requireNotNull(context.getSystemService(KeyguardManager::class.java)))
        keyguard.setIsDeviceSecure(false)
        keyguard.setIsDeviceLocked(true)
        val directory = files.newFolder()
        val keys = fixtureKeys()
        val store = AssistantKeyStore(context, directory, alias, openKeyStore = { keys })
        assertEquals(KeyStatus.MISSING, store.status())
        val record = File(directory, "openai-credential.bin")
        store.save("first-fixture-value")
        val original = record.readBytes()
        assertEquals(1, original.first().toInt())
        assertFalse(original.decodeToString().contains("first-fixture-value"))
        val reopened = AssistantKeyStore(context, directory, alias, openKeyStore = { keys })
        assertEquals(KeyStatus.SAVED, reopened.status())
        assertEquals("first-fixture-value", reopened.read())
        assertEquals(IllegalArgumentException::class.java, runCatching { reopened.save("invalid key") }.exceptionOrNull()?.javaClass)
        assertArrayEquals(original, record.readBytes())
        reopened.save("replacement-fixture-value")
        assertEquals("replacement-fixture-value", store.read())
        reopened.remove()
        assertEquals(KeyStatus.MISSING, store.status())
        assertFalse(keys.containsAlias(alias))
    }

    @Test fun readablePreviousRecordIsConvertedToTheOriginalEncryptedFormat() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = files.newFolder()
        val keys = fixtureKeys()
        val previousKey = KeyGenerator.getInstance("AES").generateKey()
        keys.setKeyEntry(previousAlias, previousKey, charArrayOf(), null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, previousKey) }
        val record = File(directory, "openai-credential.bin")
        record.writeBytes(byteArrayOf(2) + cipher.iv + cipher.doFinal("previous-fixture-value".encodeToByteArray()))
        val store = AssistantKeyStore(context, directory, alias, openKeyStore = { keys })

        assertEquals("previous-fixture-value", store.read())
        assertEquals(1, record.readBytes().first().toInt())
        assertFalse(keys.containsAlias(previousAlias))
        assertEquals("previous-fixture-value", store.read())
    }

    @Test fun unreadablePreviousRecordStaysInPlaceUntilTheKeyIsReentered() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = files.newFolder()
        val record = File(directory, "openai-credential.bin")
        val originalBytes = ByteArray(30).apply { this[0] = 2 }
        record.writeBytes(originalBytes)
        val keys = fixtureKeys()
        val store = AssistantKeyStore(context, directory, alias, openKeyStore = { keys })
        assertEquals(KeyStatus.UNREADABLE, store.status())
        assertEquals(AssistantFailureKind.UNREADABLE_KEY, assertThrows(AssistantFailure::class.java) { store.read() }.kind)
        assertArrayEquals(originalBytes, record.readBytes())
        store.save("replacement-fixture-value")
        assertEquals(1, record.readBytes().first().toInt())
        assertEquals("replacement-fixture-value", store.read())
    }

    private fun fixtureKeys(): KeyStore = KeyStore.getInstance("PKCS12").apply {
        load(null)
        setKeyEntry(alias, KeyGenerator.getInstance("AES").generateKey(), charArrayOf(), null)
    }

    @Implements(AtomicFile::class)
    class AtomicRename {
        companion object {
            // Android's rename replaces the existing file. File.renameTo does not do that on Windows.
            @JvmStatic
            @Implementation
            fun rename(source: File, target: File) {
                Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            }
        }
    }
}
