package com.emmah.inshape.feature.progress

import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.DateRange
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.WorkoutItem
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressCalculatorTest {
    private val range = DateRange(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"))
    private fun workout(
        id: String,
        date: String,
        sets: List<SessionSet>,
        equipment: String = "machine",
        quick: Boolean = false,
        basis: MeasurementBasis = MeasurementBasis.Weight,
        duration: Long? = null
    ): Workout =
        Workout(
            WorkoutEntity(
                id = id,
                state = "COMPLETED",
                activeKey = null,
                journalDate = date,
                zoneId = "Europe/Helsinki",
                createdAt = "2026-11-13T08:00:00Z",
                modifiedAt = "2026-11-13T08:00:00Z"
            ),
            listOf(
                WorkoutItem(
                    SessionItem(
                        id = "item-$id",
                        workoutId = id,
                        exerciseId = "exercise",
                        equipmentId = equipment,
                        name = "Pulldown",
                        category = "Machine",
                        basis = basis.key,
                        quickCompletedAt = if (quick) "2026-11-13T08:00:00Z" else null,
                        durationSeconds = duration
                    ),
                    sets
                )
            ),
            emptyList()
        )
    private fun set(id: String, load: String?, reps: Int?, completed: Boolean = true) = SessionSet(
        id = id,
        itemId = "item",
        load = load,
        reps = reps,
        completedAt = if (completed) "2026-11-13T08:00:00Z" else null
    )

    @Test fun completedSetsUseRecordedBasisAndComparableEquipment() {
        val sets = listOf(set("1", "27.5", 9), set("2", "25", 12), set("3", "90", 20, completed = false))
        val levels = listOf(set("4", "4", 12), set("5", "6", 8), set("6", "9", 20, completed = false))
        val expected = mapOf(
            MeasurementBasis.Weight to "27.5",
            MeasurementBasis.MachineLevel to "6",
            MeasurementBasis.Assistance to "25",
            MeasurementBasis.Bodyweight to "12"
        )
        val records = expected.keys.map { basis ->
            workout(basis.key, "2026-11-09", if (basis == MeasurementBasis.MachineLevel) levels else sets, basis = basis)
        } + workout("other-machine", "2026-11-11", listOf(set("7", "100", 20)), equipment = "other")

        expected.forEach { (basis, value) ->
            val points = calculateExerciseSeries(records, ExerciseMetricQuery("exercise", "machine", basis.key, range))
            assertEquals(listOf(basis.key), points.map { it.workoutId })
            assertEquals(listOf(BigDecimal(value)), points.map { it.value })
        }
    }

    @Test fun timedExercisesPreserveLongestDurationAndMissingValues() {
        val basis = MeasurementBasis.Time
        val records = listOf(
            workout("timed", "2026-11-09", listOf(set("1", "8", null)), basis = basis, duration = 300),
            workout("unknown", "2026-11-11", emptyList(), quick = true, basis = basis),
            workout(
                "set-time", "2026-11-13",
                listOf(set("2", "4", null), set("3", "6.25", null), set("4", "8", null, completed = false)),
                basis = basis
            )
        )
        val points = calculateExerciseSeries(records, ExerciseMetricQuery("exercise", "machine", basis.key, range))
        assertEquals(listOf("300", null, "375"), points.map { it.value?.stripTrailingZeros()?.toPlainString() })
    }

    @Test fun attendanceCountsTwoSessionsOnOneDayButNotNoteOnly() {
        val completed = workout("one", "2026-11-09", listOf(set("1", "0", 10)))
        val records =
            listOf(
                completed,
                completed.copy(row = completed.row.copy(id = "two")),
                workout("note", "2026-11-10", emptyList())
            )
        assertEquals(
            2,
            weeklyAttendance(records, range).first {
                it.week.toString() == "2026-11-09"
            }.count
        )
    }

    @Test fun averageUsesCompletedWeeksIncludingZeroWeeksAndFallsBackToCurrentWeek() {
        val today = LocalDate.parse("2026-11-13")
        val records = listOf(
            workout("previous", "2026-11-02", emptyList(), quick = true),
            workout("monday", "2026-11-09", emptyList(), quick = true),
            workout("friday", "2026-11-13", emptyList(), quick = true)
        )
        assertEquals(
            1.0,
            averageWeeklyAttendance(records, DateRange(today.minusDays(11), today), today),
            0.0
        )
        assertEquals(
            2.0,
            averageWeeklyAttendance(records, DateRange(today.minusDays(4), today), today),
            0.0
        )
        assertEquals(
            0.5,
            averageWeeklyAttendance(
                records,
                DateRange(today.minusDays(18), today),
                today
            ),
            0.0
        )
        assertEquals(0.0, averageWeeklyAttendance(emptyList(), DateRange(today, today), today), 0.0)
    }

    @Test fun longPeriodsBoundWeeklyChartWithoutLosingRecentCounts() {
        val today = LocalDate.parse("2026-11-13")
        val longRange = DateRange(LocalDate.parse("1990-01-01"), today)
        val records = listOf(
            workout("early", "1990-01-01", emptyList(), quick = true),
            workout("recent", today.toString(), emptyList(), quick = true)
        )

        val weeks = weeklyAttendance(records, longRange)

        assertTrue(weeks.size <= 260)
        assertEquals(WeekCount(LocalDate.parse("2026-11-09"), 1), weeks.last())
        assertEquals(1.0 / 1923, averageWeeklyAttendance(records, longRange, today), 0.0)
        assertEquals(weeks, weeklyAttendance(records, DateRange(LocalDate.MIN, today)))
    }

    @Test fun observationsIncludeEveryPhaseAndKeepNewestRecordedLocalDateFirst() {
        fun recorded(id: String, date: String, phase: String = "AFTER", performed: Boolean = true): Workout {
            val record =
                workout(id, date, if (performed) listOf(set(id, "20", 10)) else emptyList())
            return record.copy(
                checkIns = listOf(CheckIn(id, record.row.id, phase, "${date}T10:00:00Z", "UTC"))
            )
        }
        val history =
            listOf(
                recorded("late", "2026-11-12").let { workout ->
                    workout.copy(checkIns = workout.checkIns + CheckIn(
                        "next", workout.row.id, "NEXT_DAY", "2026-11-12T22:30:00Z", "Europe/Helsinki"
                    ))
                },
                recorded("early", "2026-11-02"),
                recorded("before", "2026-11-05", "BEFORE"),
                recorded("outside", "2026-10-30"),
                recorded("note", "2026-11-09", performed = false),
                recorded("missing", "2026-11-10")
            )
        val observations =
            listOf(
                BodyObservation("late", "lower_back", pain = null, stiffness = 3),
                BodyObservation("early", "lower_back", pain = 0),
                BodyObservation("before", "lower_back", pain = 8),
                BodyObservation("next", "lower_back", pain = 2, stiffness = 5),
                BodyObservation("outside", "lower_back", pain = 8),
                BodyObservation("note", "lower_back", pain = 8),
                BodyObservation("missing", "lower_back"),
                BodyObservation("late", "neck", pain = 9)
            )
        val points = observationSeries(history, observations, "lower_back", range)
        assertEquals(listOf("2026-11-13", "2026-11-12", "2026-11-05", "2026-11-02"), points.map { it.date.toString() })
        assertEquals(listOf("NEXT_DAY", "AFTER", "BEFORE", "AFTER"), points.map { it.phase })
        assertEquals(listOf(2, null, 8, 0), points.map { it.observation.pain })
        assertEquals(listOf(5, 3, null, null), points.map { it.observation.stiffness })
        assertTrue(observationSeries(emptyList(), observations, "lower_back", range).isEmpty())
    }
}
