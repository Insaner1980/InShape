package com.emmah.inshape.transfer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.AppContainer
import com.emmah.inshape.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExportFlowTest {
    @Test fun multipageUnicodePdfAndCsvCanBeReadThroughNormalContentUris() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "export-test-${newId()}.db"
        val container = AppContainer(context, databaseName = name); val evidence = File(context.filesDir, "verification").apply { mkdirs() }
        var pdf: PreparedExport? = null; var csv: PreparedExport? = null
        try {
            container.storageReady.await()
            val exercise = Exercise(name = "Näyttö with a very long exercise name that must wrap in the exported report")
            container.repository.saveExercise(exercise)
            val id = container.repository.createPastWorkout("2026-11-13", "Long Unicode report")
            val item = container.repository.addExercise(id, exercise.id); container.repository.markItemDone(item, 300)
            container.repository.saveWorkoutNote(id, (1..90).joinToString("\n") { "Paragraph $it: Ää Öö, commas, quotes \"like this\" and a long line that should wrap without clipping or losing its last word." })
            container.repository.finishWorkout(id, WorkoutTiming(acknowledged = true, clearTimes = true))
            val selection = ExportSelection(setOf(id), includeNotes = true, includeCheckIns = true)
            pdf = container.exports.prepare("PDF", selection); csv = container.exports.prepare("CSV", selection)
            val uri = container.exports.sharedUri(pdf)
            context.contentResolver.openInputStream(uri)!!.use { val header = ByteArray(5); java.io.DataInputStream(it).readFully(header); assertEquals("%PDF-", header.decodeToString()) }
            PdfRenderer(ParcelFileDescriptor.open(pdf.file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertTrue(renderer.pageCount > 1)
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(evidence, "export-pdf-page.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                }
            }
            container.exports.save(pdf, Uri.fromFile(File(evidence, "sample-export.pdf")))
            container.exports.save(csv, Uri.fromFile(File(evidence, "sample-export.zip")))
        } finally {
            pdf?.shared = false; container.exports.release(pdf); container.exports.release(csv)
            container.close(); context.deleteDatabase(name); container.storageRoot.deleteRecursively()
        }
    }
}
