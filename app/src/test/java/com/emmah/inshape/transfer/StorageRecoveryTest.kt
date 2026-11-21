package com.emmah.inshape.transfer

import com.emmah.inshape.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.emmah.inshape.AppContainer
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.PhotoAsset
import com.emmah.inshape.data.newId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [StorageRecoveryTest.ExportProviderShadow::class])
class StorageRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "storage-${newId()}.db"
    private lateinit var container: AppContainer
    private val exercise = Exercise(name = "Storage test")

    @Before fun setup() {
        runBlocking {
            container = AppContainer(context, databaseName = databaseName, integrationsEnabled = false)
            container.storageReady.await()
            container.storageRoot.mkdirs()
            container.repository.saveExercise(exercise)
        }
    }

    @After fun cleanup() {
        container.close()
        context.deleteDatabase(databaseName)
        container.storageRoot.deleteRecursively()
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun importedPngThumbnailsRespectEveryExifOrientation() = runBlocking {
        val source = File(container.storageRoot, "oriented.png")
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        val corners = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                bitmap.setPixel(x, y, corners[(if (y < 10) 0 else 2) + if (x < 20) 0 else 1])
            }
        }
        val expected = listOf(
            listOf(0, 1, 2, 3),
            listOf(1, 0, 3, 2),
            listOf(3, 2, 1, 0),
            listOf(2, 3, 0, 1),
            listOf(0, 2, 1, 3),
            listOf(2, 0, 3, 1),
            listOf(3, 1, 2, 0),
            listOf(1, 3, 0, 2)
        )
        try {
            expected.forEachIndexed { index, order ->
                val orientation = index + 1
                source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                ExifInterface(source).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes()
                }
                val uri = Uri.parse("content://storage/oriented")
                shadowOf(context.contentResolver).registerInputStream(uri, source.inputStream())
                val asset = container.photos.importPhoto(exercise.id, uri)
                val thumbnail = requireNotNull(container.photos.thumbnail(asset))
                try {
                    assertEquals(if (orientation >= 5) 20 else 40, thumbnail.width)
                    assertEquals(if (orientation >= 5) 40 else 20, thumbnail.height)
                    val actual = listOf(
                        thumbnail.getPixel(0, 0),
                        thumbnail.getPixel(thumbnail.width - 1, 0),
                        thumbnail.getPixel(0, thumbnail.height - 1),
                        thumbnail.getPixel(thumbnail.width - 1, thumbnail.height - 1)
                    )
                    assertEquals("EXIF orientation $orientation", order.map(corners::get), actual)
                } finally {
                    thumbnail.recycle()
                }
                container.photos.removePhoto(asset.id)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun importReplacementAndInvalidInputLeaveOnlyTheCommittedPhoto() = runBlocking {
        val source = File(container.storageRoot, "source.png")
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        val sourceUri = Uri.parse("content://storage/source")
        shadowOf(context.contentResolver).registerInputStream(sourceUri, source.inputStream())
        val first = container.photos.importPhoto(exercise.id, sourceUri)
        shadowOf(context.contentResolver).registerInputStream(sourceUri, source.inputStream())
        val replacement = container.photos.replacePhoto(first.id, sourceUri)
        assertEquals(first.id, replacement.id)
        assertFalse(container.photos.file(first).exists())
        assertTrue(container.photos.file(replacement).isFile)
        val invalid = File(container.storageRoot, "invalid.txt").apply { writeText("not an image") }
        val invalidUri = Uri.parse("content://storage/invalid")
        shadowOf(context.contentResolver).registerInputStream(invalidUri, invalid.inputStream())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { container.photos.replacePhoto(first.id, invalidUri) }
        }
        assertEquals(listOf(replacement.path), container.photos.root.list()!!.toList())
        container.photos.removePhoto(replacement.id)
        assertFalse(container.photos.file(replacement).exists())
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun verifiedBackupRestoresPhotosAndRejectsAStaleConfirmation() = runBlocking {
        val image = ByteArrayOutputStream()
        val bitmap = Bitmap.createBitmap(640, 24, Bitmap.Config.ARGB_8888)
        try {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, image))
        } finally {
            bitmap.recycle()
        }
        val resolver = shadowOf(context.contentResolver)
        val source = Uri.parse("content://storage/backup-photo")
        resolver.registerInputStream(source, ByteArrayInputStream(image.toByteArray()))
        val original = container.photos.importPhoto(exercise.id, source)
        val originalFile = container.photos.file(original)
        val expectedBytes = originalFile.readBytes()

        val archive = ByteArrayOutputStream()
        val destination = Uri.parse("content://storage/journal-backup")
        resolver.registerOutputStream(destination, archive)
        container.backups.createBackup(destination)
        val lastBackupAt = requireNotNull(container.repository.snapshot().preferences.lastBackupAt)
        container.repository.saveExercise(exercise.copy(name = "Locally edited"))

        val stagingRoot = File(container.storageRoot, "transfer-staging")
        resolver.registerInputStream(destination, ByteArrayInputStream(archive.toByteArray()))
        container.backups.validateBackup(destination).use { backup ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { container.backups.replaceJournal(backup, "expired confirmation") }
            }
            assertEquals("Locally edited", container.repository.snapshot().exercises.single().name)
            assertTrue(originalFile.isFile)
            container.backups.replaceJournal(backup, backup.identity)
        }

        val restored = container.repository.snapshot()
        val restoredPhoto = restored.photos.single()
        assertEquals(exercise, restored.exercises.single())
        assertEquals(original.id, restoredPhoto.id)
        assertTrue(expectedBytes.contentEquals(container.photos.file(restoredPhoto).readBytes()))
        assertEquals(lastBackupAt, restored.preferences.lastBackupAt)
        assertFalse(originalFile.exists())
        assertTrue(stagingRoot.list().orEmpty().isEmpty())
    }

    @Test fun failedPhotoDeletionIsReportedAndStartupRecoveryRetriesIt() = runBlocking {
        val blocked = File(container.photos.root, "blocked.png").apply { mkdirs() }
        File(blocked, "unexpected-child").writeText("orphan")
        val asset = PhotoAsset(exerciseId = exercise.id, path = blocked.name, mediaType = "image/png", byteLength = 1, checksum = "unused")
        container.database.journal().put(asset)
        container.photos.removePhoto(asset.id)
        assertTrue(blocked.exists())
        assertTrue(ShadowLog.getLogsForTag("PhotoStore").any { it.msg.contains("startup recovery") })
        container.backups.recoverOwnedStaging()
        assertFalse(blocked.exists())
        val missing = asset.copy(id = newId(), path = "already-missing.png")
        container.database.journal().put(missing)
        container.photos.removePhoto(missing.id)
        assertTrue(container.repository.snapshot().photos.isEmpty())
    }

    @Test fun recoveryRetainsReferencedPhotosAndRetriesLockedOrphans() = runBlocking {
        val root = container.photos.root.apply { mkdirs() }
        val retained = File(root, "kept/retained.png").apply {
            parentFile!!.mkdirs()
            writeText("retained")
        }
        container.database.journal().put(
            PhotoAsset(
                exerciseId = exercise.id,
                path = retained.relativeTo(root).invariantSeparatorsPath,
                mediaType = "image/png",
                byteLength = retained.length(),
                checksum = "unused"
            )
        )
        val orphan = File(root, "orphan.png").apply { writeText("orphan") }
        val empty = File(root, "empty").apply { mkdir() }
        RandomAccessFile(orphan, "rw").use {
            container.backups.recoverOwnedStaging()
            assertTrue(retained.isFile)
            assertFalse(empty.exists())
            if (System.getProperty("os.name").orEmpty().startsWith("Windows")) {
                assertTrue(orphan.exists())
                assertTrue(ShadowLog.getLogsForTag("BackupService").any { it.msg?.contains("startup recovery") == true })
            }
        }
        container.backups.recoverOwnedStaging()
        assertFalse(orphan.exists())
        assertEquals("retained", retained.readText())
    }

    // Android's FileProvider assumes Unix paths; the real provider is covered by ExportFlowTest.
    @Implements(FileProvider::class, isInAndroidSdk = false)
    @Suppress("UtilityClassWithPublicConstructor") // Robolectric instantiates shadow classes.
    class ExportProviderShadow {
        companion object {
            @JvmStatic
            @Implementation
            @Suppress("UnusedParameter") // Must match FileProvider's Android API signature.
            fun getUriForFile(context: Context, authority: String, file: File): Uri = Uri.parse("content://$authority/exports/${file.name}")
        }
    }

    @Test fun aRecreatedTransferSavesOnlyTheExportSelectedByThePicker() = runBlocking {
        val selected = File(context.cacheDir, "exports/${newId()}").apply { mkdirs() }
        val other = File(context.cacheDir, "exports/${newId()}").apply { mkdirs() }
        val selectedFile = File(selected, "InShape.pdf").apply { writeText("selected") }
        val otherFile = File(other, "InShape.pdf").apply { writeText("other export") }
        val saved = ByteArrayOutputStream()
        val destination = Uri.parse("content://storage/picker-destination")
        shadowOf(context.contentResolver).registerOutputStream(destination, saved)
        val transfers = TransferController(container, this)
        try {
            transfers.saveExport(destination, selected.name, "PDF")
            val success = transfers.state.first { !it.busy }
            assertFalse(success.isError)
            assertEquals("selected", saved.toString("UTF-8"))
            assertFalse(selectedFile.exists())
            assertEquals("other export", otherFile.readText())

            transfers.saveExport(destination, selected.name, "PDF")
            val missing = transfers.state.first { !it.busy }
            assertTrue(missing.isError)
            assertEquals(context.getString(R.string.export_missing), missing.message)
            assertEquals("selected", saved.toString("UTF-8"))
        } finally {
            transfers.close()
            selected.deleteRecursively()
            other.deleteRecursively()
        }
    }

    @Test fun sharingRefreshesExpiryAndRejectsAnExportThatWasRemoved() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = WorkManager.getInstance(context)
        val folder = File(context.cacheDir, "exports/${newId()}").apply { mkdirs() }
        val file = File(folder, "InShape.pdf").apply { writeText("test export") }
        val prepared = PreparedExport(file, "PDF", 1)
        try {
            assertTrue(folder.setLastModified(1))
            assertEquals("content", container.exports.sharedUri(prepared).scheme)
            assertTrue(prepared.shared)
            assertTrue(folder.lastModified() > 1)
            val workName = "${ExportService.CACHE_EXPIRY_WORK}:${prepared.id}"
            assertEquals(1, manager.getWorkInfosForUniqueWork(workName).get().size)
            assertTrue(ExportService.cleanup(context).contains(folder))
            assertTrue(file.isFile)
            assertTrue(folder.setLastModified(1))
            ExportService.cleanup(context)
            assertFalse(file.exists())
            prepared.shared = false
            assertThrows(IllegalStateException::class.java) { container.exports.sharedUri(prepared) }
            assertFalse(prepared.shared)
        } finally {
            manager.cancelAllWork().result.get()
            WorkManagerTestInitHelper.closeWorkDatabase()
            folder.deleteRecursively()
        }
    }
}
