package com.emmah.inshape.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoStoreTest {
    @Test fun importedBytesSurviveSourceDeletionAndInvalidReplacement() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).allowMainThreadQueries().build()
        val directory = File(context.cacheDir, "photo-test-${newId()}").apply { mkdirs() }
        try {
            val exercise = Exercise(name = "Machine"); db.journal().put(exercise)
            val input = File(directory, "source.png")
            val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
            input.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle()
            val store = PhotoStore(context, db, File(directory, "owned"))
            val asset = store.importPhoto(exercise.id, Uri.fromFile(input))
            assertTrue(input.delete())
            assertTrue(store.file(asset).isFile)
            assertEquals(asset.byteLength, store.file(asset).length())
            val invalid = File(directory, "invalid.txt").apply { writeText("not an image") }
            try { store.replacePhoto(asset.id, Uri.fromFile(invalid)); fail("Invalid image accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(asset.id, db.journal().photos().single().id)
            assertTrue(store.file(asset).exists())
            assertEquals(1, File(directory, "owned").listFiles()!!.size)
            store.removePhoto(asset.id)
            assertTrue(db.journal().photos().isEmpty())
            assertFalse(store.file(asset).exists())
        } finally { db.close(); directory.deleteRecursively() }
    }
}
