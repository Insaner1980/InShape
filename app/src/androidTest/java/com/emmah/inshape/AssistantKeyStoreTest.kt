package com.emmah.inshape

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.feature.assistant.AssistantKeyStore
import com.emmah.inshape.feature.assistant.KeyStatus
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantKeyStoreTest {
    @Test fun reopenReplaceAndRemoveWithAnIsolatedDeviceKey() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val directory = File(context.noBackupFilesDir, "key-test-$id").apply { mkdir() }
        val alias = "inshape.test.$id"
        val first = AssistantKeyStore(context, directory, alias)
        val stored = File(directory, "openai-credential.bin")
        try {
            assertEquals(KeyStatus.MISSING, first.status())
            first.save("synthetic-key-marker")
            assertEquals(KeyStatus.SAVED, first.status())
            val original = stored.readBytes()
            assertFalse(original.decodeToString().contains("synthetic-key-marker"))
            val reopened = AssistantKeyStore(context, directory, alias)
            assertEquals("synthetic-key-marker", reopened.read())
            runCatching { reopened.save("invalid key with spaces") }
            assertArrayEquals(original, stored.readBytes())
            reopened.save("synthetic-key-marker")
            assertFalse(original.copyOfRange(1, 13).contentEquals(stored.readBytes().copyOfRange(1, 13)))
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(alias)
            }
            assertEquals(KeyStatus.UNREADABLE, reopened.status())
            reopened.remove()
            assertEquals(KeyStatus.MISSING, first.status())
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            first.remove()
            directory.delete()
        }
    }
}
