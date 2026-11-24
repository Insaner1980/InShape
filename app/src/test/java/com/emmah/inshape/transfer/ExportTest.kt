package com.emmah.inshape.transfer

import com.emmah.inshape.R
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.quantity
import com.emmah.inshape.testContext
import com.emmah.inshape.ui
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportTest {
    private val time = "2026-11-13T10:00:00Z"
    private fun fixture(): JournalSnapshot {
        val row =
            WorkoutEntity(
                id = "workout",
                title = "=Formula, \"Näyttö\"",
                state = "COMPLETED",
                activeKey = null,
                journalDate = "2026-11-13",
                zoneId = "UTC",
                note = "Private\nWorkout",
                createdAt = time,
                modifiedAt = time
            )
        val item =
            SessionItem(
                id = "item",
                workoutId = row.id,
                exerciseId = "exercise",
                equipmentId = "equipment",
                name = "+Machine",
                category = "Machine",
                basis = "kg",
                disposition = "SKIPPED",
                note = "Exercise note"
            )
        val quick = item.copy(
            id = "quick",
            name = "Mobility",
            basis = "duration",
            quickCompletedAt = time
        )
        val check =
            CheckIn(
                id = "check",
                workoutId = row.id,
                phase = "AFTER",
                observedAt = time,
                zoneId = "UTC",
                note = "Check note"
            )
        return JournalSnapshot(
            workouts = listOf(row, row.copy(id = "unselected", title = "Not selected")),
            items = listOf(item, quick),
            sets = listOf(
                SessionSet(
                    id = "done",
                    itemId = item.id,
                    load = "27.5",
                    reps = 10,
                    completedAt = time
                ),
                SessionSet(
                    id = "target",
                    itemId = item.id,
                    suggestedLoad = "99",
                    suggestedReps = 20
                )
            ),
            checkIns = listOf(check),
            observations = listOf(
                BodyObservation(
                    check.id,
                    "left_knee",
                    pain = 0,
                    stiffness = null,
                    note = "Body note"
                )
            )
        )
    }
    private fun export(selection: ExportSelection): Map<String, String> {
        val output = ByteArrayOutputStream()
        JournalExports.csv(testContext(), fixture(), selection, output)
        return buildMap {
            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
                while (true) {
                    val entry =
                        zip.nextEntry ?: break
                    put(entry.name, zip.readBytes().decodeToString())
                }
            }
        }
    }

    @Test fun selectedCompletedWorkHasStableLinksNullsAndEscapedUnicodeNotes() {
        val files = export(ExportSelection(setOf("workout"), true, true))
        assertEquals(6, files.size)
        val workouts = files.getValue("workouts.csv")
        assertTrue(workouts.contains(ui(R.string.csv_workout_id)))
        assertTrue(workouts.contains("'="))
        assertTrue(workouts.contains("\"\"Näyttö\"\""))
        assertTrue(workouts.contains("Private\nWorkout"))
        assertFalse(workouts.contains("Not selected"))
        assertTrue(files.getValue("exercises.csv").contains("'+Machine"))
        assertTrue(files.getValue("exercises.csv").contains("COMPLETION_ONLY"))
        val sets = files.getValue("sets.csv")
        assertTrue(sets.contains("27.5"))
        assertFalse(sets.contains("target"))
        assertFalse(sets.contains("99"))
        assertTrue(files.getValue("body_observations.csv").contains("\"0\",\"\""))
        assertTrue(files.getValue("README.txt").contains("UTF-8"))
    }

    @Test fun unselectedSensitiveContentIsAbsentAndEmptySelectionRejected() {
        val files = export(ExportSelection(setOf("workout")))
        assertFalse(
            files.values.any {
                it.contains("Private") || it.contains("Exercise note") ||
                    it.contains("Body note") ||
                    it.contains("Check note")
            }
        )
        assertEquals(1, files.getValue("check_ins.csv").trimEnd().lines().size)
        assertThrows(IllegalArgumentException::class.java) { export(ExportSelection(emptySet())) }
    }

    @Test fun pdfIncludesRecordedSetsTimeAndRequestedCheckIns() {
        val base = fixture()
        val workout = base.workouts.first().copy(
            start = time, end = "2026-11-13T10:45:00Z", zoneId = ZoneId.systemDefault().id,
            durationSeconds = 2700, timingConfirmed = true
        )
        val check = base.checkIns.single().copy(energy = "GOOD", effort = 4, feelings = "Vasen polvi tuntui vakaalta")
        val items = base.items.map {
            if (it.id == "item") it.copy(discomfort = true) else it.copy(disposition = "INCOMPLETE")
        }
        val snapshot = base.copy(
            workouts = listOf(workout),
            items = items,
            sets = base.sets + SessionSet(id = "second-done", itemId = "item", completedAt = time),
            checkIns = listOf(
                check,
                check.copy(id = "before", phase = "BEFORE", feelings = "", note = ""),
                check.copy(id = "next", phase = "NEXT_DAY", observedAt = "2026-11-14T09:00:00Z", feelings = "", note = "")
            )
        )
        val content = pdfContent(snapshot, ExportSelection(setOf(workout.id), includeNotes = true, includeCheckIns = true))
        assertTrue(content.headings.any { workout.title in it })
        assertTrue(content.headings.containsAll(listOf(ui(R.string.before_workout), ui(R.string.after_workout), ui(R.string.next_day))))
        assertTrue(content.text.any { ui(R.string.export_no_load) in it && ui(R.string.export_no_reps) in it })
        assertTrue(content.text.containsAll(listOf(
            ui(R.string.export_skipped_exercise),
            ui(R.string.export_partial_exercise),
            ui(R.string.discomfort_noted),
            ui(R.string.export_feelings, check.feelings),
            ui(R.string.export_check_in_note, check.note),
            "Body note"
        )))
    }

    @Test fun pdfOmitsNotesAndCheckInsWhenTheyAreDeselected() {
        val snapshot = fixture()
        val content = pdfContent(snapshot, ExportSelection(snapshot.workouts.map { it.id }.toSet()))
        assertTrue(content.text.contains(ui(R.string.note_only_workout)))
        assertTrue(content.headings.any { "Not selected" in it })
        assertFalse(content.text.any { listOf("Private", "Exercise note", "Body note", "Check note").any(it::contains) })
        assertFalse(content.headings.contains(ui(R.string.after_workout)))
    }

    private fun pdfContent(snapshot: JournalSnapshot, selection: ExportSelection): CapturedPdfContent =
        CapturedPdfContent().also { PdfExporter.writeContent(selectedWorkouts(snapshot, selection), snapshot.observations, selection, it) }

    private class CapturedPdfContent : PdfContent {
        override val context = testContext()
        val headings = mutableListOf<String>()
        val text = mutableListOf<String>()

        override fun paragraph(text: String, heading: Boolean) {
            this.text += text
            if (heading) headings += text
        }

        override fun addSectionSpacing() = Unit
    }
}
