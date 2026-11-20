package com.emmah.inshape.transfer

import com.emmah.inshape.R
import com.emmah.inshape.quantity
import com.emmah.inshape.ui
import com.emmah.inshape.testContext
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.WorkoutEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
