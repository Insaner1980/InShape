package com.emmah.inshape.feature.progress

import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.DateRange
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.weekStart
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val MAX_ATTENDANCE_WEEKS = 260L

enum class ExerciseMetric { WEIGHT, REPS, REPS_AT_LOAD, LOAD_AT_REPS, SET_COUNT }
data class ExerciseMetricQuery(
    val exerciseId: String,
    val equipmentId: String,
    val basis: String,
    val range: DateRange,
    val metric: ExerciseMetric = ExerciseMetric.WEIGHT,
    val fixedLoad: BigDecimal? = null,
    val minimumReps: Int = 10
)
data class MetricPoint(val workoutId: String, val date: LocalDate, val value: BigDecimal?)
data class PersonalBest(val point: MetricPoint, val previous: MetricPoint?, val isNewRecord: Boolean)
data class WeekCount(val week: LocalDate, val count: Int)
fun calculateExerciseSeries(records: List<Workout>, query: ExerciseMetricQuery): List<MetricPoint> = records
    .filter { it.qualifiesForAttendance && it.row.journalDate in query.range }
    .sortedWith(compareBy({ it.row.journalDate }, { it.row.createdAt })).mapNotNull { workout ->
        val items = workout.items.filter {
            it.row.exerciseId == query.exerciseId &&
                it.row.equipmentId == query.equipmentId &&
                it.row.basis == query.basis &&
                it.hasPerformedWork
        }
        if (items.isEmpty()) return@mapNotNull null
        val sets = items.flatMap { it.completedSets }
        val value = when (query.metric) {
            ExerciseMetric.WEIGHT -> sets.mapNotNull { it.load?.toBigDecimal() }.maxOrNull()

            ExerciseMetric.REPS -> sets.mapNotNull { it.reps?.toBigDecimal() }.maxOrNull()

            ExerciseMetric.REPS_AT_LOAD -> sets.filter { set ->
                query.fixedLoad != null &&
                    set.load?.toBigDecimal()?.compareTo(query.fixedLoad) == 0
            }.mapNotNull { it.reps?.toBigDecimal() }.maxOrNull()

            ExerciseMetric.LOAD_AT_REPS -> sets.filter {
                (it.reps ?: 0) >= query.minimumReps
            }.mapNotNull { it.load?.toBigDecimal() }.maxOrNull()

            ExerciseMetric.SET_COUNT -> sets.size.toBigDecimal()
        }
        MetricPoint(workout.row.id, LocalDate.parse(workout.row.journalDate), value)
    }
fun calculatePersonalBest(records: List<Workout>, query: ExerciseMetricQuery): PersonalBest? {
    if (query.basis.startsWith("assistance") &&
        query.metric in listOf(ExerciseMetric.WEIGHT, ExerciseMetric.LOAD_AT_REPS)
    ) {
        return null
    }
    val points = calculateExerciseSeries(records, query).filter { it.value != null }
    val best = points.maxByOrNull { it.value!! } ?: return null
    val previous = points.takeWhile {
        it !== best
    }.filter { it.value!! < best.value!! }.maxByOrNull { it.value!! }
    return PersonalBest(
        best,
        previous,
        points.size > 1 && points.last() === best && previous != null
    )
}
fun weeklyAttendance(records: List<Workout>, range: DateRange): List<WeekCount> {
    if (range.start > range.end) return emptyList()
    val lastWeek = weekStart(range.end)
    val weekCount = (ChronoUnit.WEEKS.between(weekStart(range.start), lastWeek) + 1).coerceAtMost(MAX_ATTENDANCE_WEEKS)
    val firstWeek = lastWeek.minusWeeks(weekCount - 1)
    val visibleDates = maxOf(firstWeek, range.start)..range.end
    val counts = records.asSequence().filter { it.qualifiesForAttendance }
        .map { LocalDate.parse(it.row.journalDate) }
        .filter { it in visibleDates }
        .groupingBy(::weekStart).eachCount()
    return List(weekCount.toInt()) { index ->
        val week = firstWeek.plusWeeks(index.toLong())
        WeekCount(week, counts[week] ?: 0)
    }
}

fun averageWeeklyAttendance(records: List<Workout>, range: DateRange, today: LocalDate): Double {
    val currentWeek = weekStart(today).toEpochDay()
    val start = range.start.toEpochDay()
    val end = range.end.toEpochDay()
    val firstFullWeek = start + (8 - range.start.dayOfWeek.value) % 7
    val lastPossibleWeek = minOf(end - 6, currentWeek - 7)
    val fullWeeks = if (lastPossibleWeek >= firstFullWeek) (lastPossibleWeek - firstFullWeek) / 7 + 1 else 0
    val includedDays = if (fullWeeks > 0) {
        firstFullWeek..<(firstFullWeek + fullWeeks * 7)
    } else {
        maxOf(start, currentWeek)..minOf(end, currentWeek + 6)
    }
    val count = records.count {
        it.qualifiesForAttendance && LocalDate.parse(it.row.journalDate).toEpochDay() in includedDays
    }
    return count.toDouble() / fullWeeks.coerceAtLeast(1)
}

data class ObservationPoint(val date: LocalDate, val pain: Int?, val stiffness: Int?)

fun observationSeries(
    history: List<Workout>,
    observations: List<BodyObservation>,
    region: String,
    phase: String,
    range: DateRange
): List<ObservationPoint> = history
    .filter { it.qualifiesForAttendance && it.row.journalDate in range }
    .sortedWith(compareBy({ it.row.journalDate }, { it.row.createdAt }))
    .mapNotNull { workout ->
        val check = workout.checkIns.find { it.phase == phase } ?: return@mapNotNull null
        val observation =
            observations.find { it.checkInId == check.id && it.region == region }
                ?: return@mapNotNull null
        if (observation.pain == null && observation.stiffness == null) {
            null
        } else {
            ObservationPoint(
                LocalDate.parse(workout.row.journalDate),
                observation.pain,
                observation.stiffness
            )
        }
    }
