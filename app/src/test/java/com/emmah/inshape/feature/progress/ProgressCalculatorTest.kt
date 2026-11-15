package com.emmah.inshape.feature.progress

import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.DateRange
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.WorkoutItem
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressCalculatorTest {
    private val range = DateRange(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"))
    private fun workout(id: String, date: String, sets: List<SessionSet>, equipment: String = "machine", quick: Boolean = false): Workout =
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
                        basis = "kg",
                        quickCompletedAt = if (quick) "2026-11-13T08:00:00Z" else null
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

    @Test fun minimumRepsIgnoresSuggestionsAndDifferentMachines() {
        val records =
            listOf(
                workout(
                    "one",
                    "2026-11-09",
                    listOf(set("1", "27.5", 9), set("2", "25", 12), set("3", "90", 20, false))
                ),
                workout("other", "2026-11-11", listOf(set("4", "100", 20)), "other-machine")
            )
        val query =
            ExerciseMetricQuery(
                "exercise",
                "machine",
                "kg",
                range,
                ExerciseMetric.LOAD_AT_REPS,
                minimumReps = 10
            )
        val points = calculateExerciseSeries(records, query)
        assertEquals(listOf(BigDecimal("25")), points.map { it.value })
        assertEquals("one", calculatePersonalBest(records, query)!!.point.workoutId)
    }

    @Test fun equalDecimalsMatchAndMissingLoadBreaksSeries() {
        val records =
            listOf(
                workout("one", "2026-11-09", listOf(set("1", "25.0", 12))),
                workout("two", "2026-11-11", emptyList(), quick = true)
            )
        val query =
            ExerciseMetricQuery(
                "exercise",
                "machine",
                "kg",
                range,
                ExerciseMetric.REPS_AT_LOAD,
                fixedLoad = BigDecimal("25")
            )
        assertEquals(
            listOf(BigDecimal("12"), null),
            calculateExerciseSeries(records, query).map {
                it.value
            }
        )
        assertEquals(
            BigDecimal("0"),
            calculateExerciseSeries(
                records,
                query.copy(metric = ExerciseMetric.SET_COUNT)
            ).last().value
        )
    }

    @Test fun personalBestChangesAfterCorrectionAndTieIsNotNew() {
        val records =
            listOf(
                workout("one", "2026-11-09", listOf(set("1", "25", 10))),
                workout("two", "2026-11-11", listOf(set("2", "27.5", 10))),
                workout("three", "2026-11-13", listOf(set("3", "27.5", 10)))
            )
        val query = ExerciseMetricQuery("exercise", "machine", "kg", range)
        val best = calculatePersonalBest(records, query)!!
        assertEquals(BigDecimal("27.5"), best.point.value)
        assertEquals(BigDecimal("25"), best.previous!!.value)
        assertFalse(best.isNewRecord)
        assertEquals(BigDecimal("25"), calculatePersonalBest(records.take(1), query)!!.point.value)
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

    @Test fun observationsPreserveZeroAndMissingValuesAndFilterPhaseRegionRangeAndUnperformedWork() {
        fun recorded(id: String, date: String, phase: String = "AFTER", performed: Boolean = true): Workout {
            val record =
                workout(id, date, if (performed) listOf(set(id, "20", 10)) else emptyList())
            return record.copy(
                checkIns = listOf(CheckIn(id, record.row.id, phase, "2026-11-13T10:00:00Z", "UTC"))
            )
        }
        val history =
            listOf(
                recorded("late", "2026-11-12"),
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
                BodyObservation("outside", "lower_back", pain = 8),
                BodyObservation("note", "lower_back", pain = 8),
                BodyObservation("missing", "lower_back"),
                BodyObservation("late", "neck", pain = 9)
            )
        val points = observationSeries(history, observations, "lower_back", "AFTER", range)
        assertEquals(listOf("2026-11-02", "2026-11-12"), points.map { it.date.toString() })
        assertEquals(listOf(0, null), points.map { it.pain })
        assertEquals(listOf(null, 3), points.map { it.stiffness })
        assertEquals(
            listOf(8),
            observationSeries(history, observations, "lower_back", "BEFORE", range).map {
                it.pain
            }
        )
        assertTrue(
            observationSeries(history, observations, "lower_back", "NEXT_DAY", range).isEmpty()
        )
        assertTrue(
            observationSeries(emptyList(), observations, "lower_back", "AFTER", range).isEmpty()
        )
    }
}
