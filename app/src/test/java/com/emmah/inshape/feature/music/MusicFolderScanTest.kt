package com.emmah.inshape.feature.music

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.MatrixCursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.emmah.inshape.R
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaMetadataRetriever.addMetadata
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicFolderScanTest {
    @Test
    fun nestedFolderScanPreservesPlaylistIdentityAcrossFileReplacementAndRestoration() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val resolver = app.contentResolver
        val tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root")
        val other = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "incomplete")
        val provider = Robolectric.buildContentProvider(MusicDocumentsProvider::class.java).create(AUTHORITY).get()
        val file = File.createTempFile("music-scan-", ".wav", app.cacheDir)
        val original = ByteArray(70_000) { (it % 251).toByte() }
        file.writeBytes(original)
        provider.file = file
        val databaseName = "music-folder-traversal.db"
        val job = Job()
        val store = MusicStore(app, databaseName, CoroutineScope(job + Dispatchers.IO))
        try {
            val content = shadowOf(resolver)
            for (id in listOf("tagged", "plain.mp3")) {
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                content.registerInputStreamSupplier(uri) { file.inputStream() }
            }
            val badHash = DocumentsContract.buildDocumentUriUsingTree(tree, "badHash")
            content.registerInputStreamSupplier(badHash) { throw IOException("Unable to read this file") }
            store.scanAndWait(tree)
            val scanned = store.catalogMatching { it.songs.size == 7 }
            assertNull(store.message.value)
            assertFalse(scanned.accessLost)
            assertEquals(setOf(tree), resolver.persistedUriPermissions.map { it.uri }.toSet())
            val tagged = scanned.songs.single { it.title == "Tagged song" }
            assertTrue(tagged.available)
            assertNotNull(tagged.contentFingerprint)
            assertEquals(91_500L, tagged.durationMs)
            assertEquals("Tagged artist", tagged.artist)
            val untagged = scanned.songs.single { it.title == "plain" }
            assertTrue(untagged.available)
            assertNull(untagged.durationMs)
            assertEquals(app.getString(R.string.unknown_artist), untagged.artist)
            assertEquals(
                setOf("Virtual", "Partial", "Missing", "Blocked", "Bad fingerprint"),
                scanned.songs.filterNot { it.available }.map { it.title }.toSet()
            )

            val playlist = store.create("Saved song", listOf(tagged.id))
            store.setEnergy(tagged.id, "steady")
            file.writeBytes(byteArrayOf(1, 2, 3))
            store.scanAndWait()
            val replaced = store.catalogMatching { catalog ->
                catalog.songs.any { it.title == tagged.title && it.available && it.id != tagged.id }
            }
            val replacement = replaced.songs.single { it.title == tagged.title && it.available }
            assertNotEquals(tagged.contentFingerprint, replacement.contentFingerprint)
            assertNull(replacement.energy)
            assertEquals(playlist, replaced.playlists.single().id)
            assertFalse(replaced.playlists.single().tracks.single().available)
            assertEquals("steady", replaced.playlists.single().tracks.single().energy)

            store.scanAndWait(other)
            assertEquals(app.getString(R.string.music_folder_unavailable), store.message.value)
            assertEquals(setOf(tree), resolver.persistedUriPermissions.map { it.uri }.toSet())
            assertEquals(tree.toString(), store.catalogMatching { it.source != null }.source?.tree)

            file.writeBytes(original)
            store.scanAndWait()
            assertNull(store.message.value)
            val restored = store.catalogMatching { catalog -> catalog.playlists.singleOrNull()?.tracks?.singleOrNull()?.available == true }
            assertEquals(tagged.id, restored.playlists.single().tracks.single().id)
            assertEquals("steady", restored.songs.single { it.id == tagged.id }.energy)
        } finally {
            job.cancelAndJoin()
            store.close()
            resolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            app.deleteDatabase(databaseName)
            file.delete()
        }
    }

    private suspend fun MusicStore.scanAndWait(tree: Uri? = null) = withTimeout(10_000) {
        scan(tree)
        scanning.first { !it }
    }

    private suspend fun MusicStore.catalogMatching(matches: (MusicCatalog) -> Boolean) =
        withTimeout(10_000) { catalog.first(matches) }

    class MusicDocumentsProvider : ContentProvider() {
        lateinit var file: File

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ) = MatrixCursor(checkNotNull(projection)).apply {
            when (DocumentsContract.getDocumentId(uri)) {
                "root" -> {
                    addRow(arrayOf<Any?>("nested", "Nested", Document.MIME_TYPE_DIR, 0))
                    addRow(arrayOf<Any?>("text", "Notes.txt", "text/plain", 0))
                    addRow(arrayOf<Any?>("tagged", "Fallback.WAV", "application/octet-stream", 0))
                    addRow(arrayOf<Any?>("virtual", "Virtual.ogg", "audio/ogg", Document.FLAG_VIRTUAL_DOCUMENT))
                    addRow(arrayOf<Any?>("partial", "Partial.ogg", "audio/ogg", Document.FLAG_PARTIAL))
                }

                "nested" -> {
                    addRow(arrayOf<Any?>("root", "Cycle", Document.MIME_TYPE_DIR, 0))
                    addRow(arrayOf<Any?>("tagged", "Duplicate.WAV", "audio/wav", 0))
                    addRow(arrayOf<Any?>("plain.mp3", null, "audio/mpeg", 0))
                    addRow(arrayOf<Any?>("missing", "Missing.mp3", "audio/mpeg", 0))
                    addRow(arrayOf<Any?>("blocked", "Blocked.mp3", "audio/mpeg", 0))
                    addRow(arrayOf<Any?>("badHash", "Bad fingerprint.mp3", "audio/mpeg", 0))
                }

                "incomplete" -> extras = Bundle().apply { putString(DocumentsContract.EXTRA_ERROR, "Provider could not list folder") }
            }
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val document = DocumentsContract.getDocumentId(uri)
            if (document == "missing") throw FileNotFoundException(document)
            if (document == "blocked") throw SecurityException(document)
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            if (document == "tagged") {
                val source = DataSource.toDataSource(descriptor.fileDescriptor)
                addMetadata(source, MediaMetadataRetriever.METADATA_KEY_TITLE, "Tagged song")
                addMetadata(source, MediaMetadataRetriever.METADATA_KEY_ARTIST, "Tagged artist")
                addMetadata(source, MediaMetadataRetriever.METADATA_KEY_DURATION, "91500")
            }
            return descriptor
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private companion object {
        const val AUTHORITY = "com.emmah.inshape.music-scan-test"
    }
}
