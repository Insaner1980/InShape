package com.emmah.inshape.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.AppContainer
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.SaveState
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalDatabase
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.library.importExercisePhotos
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoStoreTest {
    @Test fun importedBytesSurviveSourceDeletionAndInvalidReplacement() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(
            context,
            JournalDatabase::class.java
        ).allowMainThreadQueries().build()
        val directory = File(context.cacheDir, "photo-test-${newId()}").apply { mkdirs() }
        try {
            val exercise = Exercise(name = "Machine")
            db.journal().put(exercise)
            val input = File(directory, "source.png")
            val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
            input.outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
            val store = PhotoStore(context, db, File(directory, "owned"))
            val asset = store.importPhoto(exercise.id, Uri.fromFile(input))
            assertTrue(input.delete())
            assertTrue(store.file(asset).isFile)
            assertEquals(asset.byteLength, store.file(asset).length())
            val invalid = File(directory, "invalid.txt").apply { writeText("not an image") }
            try {
                store.replacePhoto(asset.id, Uri.fromFile(invalid))
                fail("Invalid image accepted")
            } catch (
                _: IllegalArgumentException
            ) {
            }
            assertEquals(
                asset.id,
                db
                    .journal()
                    .photos()
                    .single()
                    .id
            )
            assertTrue(store.file(asset).exists())
            assertEquals(1, File(directory, "owned").listFiles()!!.size)
            store.removePhoto(asset.id)
            assertTrue(db.journal().photos().isEmpty())
            assertFalse(store.file(asset).exists())
        } finally {
            db.close()
            directory.deleteRecursively()
        }
    }

    @Test fun retryingASelectedBatchKeepsEachPhotoOnce() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "photo-retry-${newId()}"
        val container = AppContainer(context, databaseName = "$name.db")
        val modelStore = ViewModelStore()
        val vm = withContext(Dispatchers.Main) { JournalViewModel(container).also { modelStore.put("journal", it) } }
        val directory = File(context.cacheDir, name).apply { mkdirs() }
        try {
            container.storageReady.await()
            val exercise = Exercise(name = "Machine")
            container.repository.saveExercise(exercise)
            val first = File(directory, "first.png")
            val second = File(directory, "second.png")
            val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
            first.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()

            vm.importExercisePhotos(exercise.id, listOf(Uri.fromFile(first), Uri.fromFile(second)))
            val failure = withTimeout(5_000) { vm.saveState.filterIsInstance<SaveState.Failed>().first() }
            assertTrue(failure.retryable)
            val importedFirst = container.database.journal().photos().single()

            first.copyTo(second)
            vm.retry()
            withTimeout(5_000) { vm.saveState.first { it == SaveState.Saved } }
            val photos = container.database.journal().photos()
            assertEquals(listOf(0, 1), photos.map { it.position })
            assertEquals(importedFirst, photos.first())
            assertEquals(2, container.photos.root.listFiles()!!.size)
        } finally {
            withContext(Dispatchers.Main) { modelStore.clear() }
            container.close()
            context.deleteDatabase("$name.db")
            container.storageRoot.deleteRecursively()
            directory.deleteRecursively()
        }
    }
}
