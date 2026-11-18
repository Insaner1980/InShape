package com.emmah.inshape.transfer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.emmah.inshape.AppContainer
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.WorkoutTiming
import com.emmah.inshape.data.newId
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportFlowTest {
    @SdkSuppress(minSdkVersion = 35)
    @Test
    fun exportedPdfIncludesPrivateDetailsOnlyWhenSelected() {
        val timestamp = "2026-11-13T10:00:00Z"
        val workout = WorkoutEntity(
            title = "Strength training",
            state = "COMPLETED",
            activeKey = null,
            journalDate = "2026-11-13",
            start = timestamp,
            end = "2026-11-13T10:42:15Z",
            zoneId = "UTC",
            durationSeconds = 2535,
            note = "WorkoutMemo",
            createdAt = timestamp,
            modifiedAt = timestamp
        )
        val recovery = workout.copy(id = newId(), title = "Recovery day", end = null, durationSeconds = null, note = "")
        val exercise = SessionItem(
            workoutId = workout.id,
            exerciseId = "exercise",
            equipmentId = "machine",
            name = "Cable row",
            equipmentName = "Cable machine",
            category = "Machine",
            basis = "kg",
            disposition = "DEFERRED",
            durationSeconds = 45,
            discomfort = true,
            note = "ExerciseMemo"
        )
        val checkIn = CheckIn(
            workoutId = workout.id,
            phase = "AFTER",
            observedAt = "2026-11-13T10:43:00Z",
            zoneId = "UTC",
            energy = "GOOD",
            effort = 8,
            feelings = "EnergyMemo",
            note = "CheckInMemo"
        )
        val before = CheckIn(workoutId = workout.id, phase = "BEFORE", observedAt = timestamp, zoneId = "UTC")
        val data = JournalSnapshot(
            workouts = listOf(workout, recovery, recovery.copy(id = newId(), title = "Unselected session")),
            items = listOf(
                exercise,
                exercise.copy(id = newId(), name = "Stretch", disposition = "DONE", note = "", quickCompletedAt = timestamp)
            ),
            sets = listOf(
                SessionSet(itemId = exercise.id, load = "22.5", reps = 8, completedAt = timestamp),
                SessionSet(itemId = exercise.id, position = 1, reps = 7, completedAt = timestamp),
                SessionSet(itemId = exercise.id, position = 2, load = "20", completedAt = timestamp)
            ),
            checkIns = listOf(checkIn, before),
            observations = listOf(
                BodyObservation(checkIn.id, "left_knee", pain = 2, note = "ObservationMemo"),
                BodyObservation(before.id, "lower_back", stiffness = 5)
            )
        )
        val ids = setOf(workout.id, recovery.id)
        val privateNotes = listOf("WorkoutMemo", "ExerciseMemo", "CheckInMemo", "ObservationMemo")
        val checkInDetails = listOf("EnergyMemo", "before check-in", "after check-in")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("journal-export-", ".pdf", context.cacheDir)
        try {
            fun verify(selection: ExportSelection, included: List<String>, excluded: List<String>) {
                file.outputStream().use { PdfExporter.write(data, selection, it) }
                PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                    val queries = included + excluded
                    val found = buildSet {
                        repeat(renderer.pageCount) { index ->
                            renderer.openPage(index).use { page ->
                                addAll(queries.filter { page.searchText(it).isNotEmpty() })
                            }
                        }
                    }
                    assertEquals(included.toSet(), found)
                }
            }
            verify(
                ExportSelection(ids, includeNotes = true, includeCheckIns = true),
                privateNotes + checkInDetails + listOf("42 min 15 s", "22.5 kg", "Note-only entry", "reps not recorded"),
                listOf("Unselected session")
            )
            verify(ExportSelection(ids, includeCheckIns = true), checkInDetails, privateNotes)
            verify(ExportSelection(ids), listOf("Strength training", "Recovery day"), privateNotes + checkInDetails)
        } finally {
            file.delete()
        }
    }

    @Test fun multipageUnicodePdfAndCsvCanBeReadThroughNormalContentUris() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "export-test-${newId()}.db"
        val container = AppContainer(context, databaseName = name)
        val evidence = File(context.filesDir, "verification").apply { mkdirs() }
        var pdf: PreparedExport? = null
        var csv: PreparedExport? = null
        try {
            container.storageReady.await()
            val exercise =
                Exercise(
                    name = "Näyttö with a very long exercise name that must wrap in the exported report"
                )
            container.repository.saveExercise(exercise)
            val id = container.repository.createPastWorkout("2026-11-13", "Long Unicode report")
            val item = container.repository.addExercise(id, exercise.id)
            container.repository.markItemDone(item, 300)
            container.repository.saveWorkoutNote(
                id,
                (1..90).joinToString("\n") {
                    "Paragraph $it: Ää Öö, commas, quotes \"like this\" and a long line that should wrap " +
                        "without clipping or losing its last word."
                }
            )
            container.repository.finishWorkout(
                id,
                WorkoutTiming(acknowledged = true, clearTimes = true)
            )
            val selection =
                ExportSelection(setOf(id), includeNotes = true, includeCheckIns = true)
            pdf = container.exports.prepare("PDF", selection)
            csv = container.exports.prepare("CSV", selection)
            val uri = container.exports.sharedUri(pdf)
            context.contentResolver.openInputStream(uri)!!.use {
                val header = ByteArray(5)
                java.io.DataInputStream(it).readFully(header)
                assertEquals("%PDF-", header.decodeToString())
            }
            PdfRenderer(
                ParcelFileDescriptor.open(pdf.file, ParcelFileDescriptor.MODE_READ_ONLY)
            ).use { renderer ->
                assertTrue(renderer.pageCount > 1)
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(
                        page.width * 2,
                        page.height * 2,
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(evidence, "export-pdf-page.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
            container.exports.save(pdf, Uri.fromFile(File(evidence, "sample-export.pdf")))
            container.exports.save(csv, Uri.fromFile(File(evidence, "sample-export.zip")))
            container.exports.release(csv)
            assertThrows(IllegalStateException::class.java) { container.exports.sharedUri(csv) }
            assertFalse(csv.shared)
        } finally {
            pdf?.shared = false
            container.exports.release(pdf)
            container.exports.release(csv)
            container.close()
            context.deleteDatabase(name)
            container.storageRoot.deleteRecursively()
        }
    }
}
