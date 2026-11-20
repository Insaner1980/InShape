package com.emmah.inshape.feature.progress

import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.DateRange
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.weekStart
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val MAX_ATTENDANCE_WEEKS = 260L

data class ExerciseMetricQuery(
    val exerciseId: String,
    val equipmentId: String,
    val basis: String,
    val range: DateRange
)
data class MetricPoint(val workoutId: String, val date: LocalDate, val value: BigDecimal?)
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
        val value = when (MeasurementBasis.fromKey(query.basis)) {
            MeasurementBasis.Weight, MeasurementBasis.MachineLevel -> sets.mapNotNull { it.load?.toBigDecimalOrNull() }.maxOrNull()
            MeasurementBasis.Assistance -> sets.mapNotNull { it.load?.toBigDecimalOrNull() }.minOrNull()
            MeasurementBasis.Bodyweight -> sets.mapNotNull { it.reps }.maxOrNull()?.toBigDecimal()
            MeasurementBasis.Time -> items.mapNotNull { item ->
                item.row.durationSeconds?.toBigDecimal() ?: item.completedSets
                    .mapNotNull { it.load?.toBigDecimalOrNull()?.multiply(60.toBigDecimal()) }
                    .maxOrNull()
            }.maxOrNull()
        }
        MetricPoint(workout.row.id, LocalDate.parse(workout.row.journalDate), value)
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

data class ObservationPoint(val date: LocalDate, val phase: String, val observation: BodyObservation)

fun observationSeries(
    history: List<Workout>,
    observations: List<BodyObservation>,
    region: String,
    range: DateRange
): List<ObservationPoint> {
    val observationsByCheckIn = observations.asSequence()
        .filter { it.region == region && (it.pain != null || it.stiffness != null) }
        .associateBy { it.checkInId }
    return history.asSequence()
        .filter { it.qualifiesForAttendance && it.row.journalDate in range }
        .flatMap { it.checkIns.asSequence() }
        .mapNotNull { check ->
            val observation = observationsByCheckIn[check.id] ?: return@mapNotNull null
            val instant = Instant.parse(check.observedAt)
            instant to ObservationPoint(instant.atZone(ZoneId.of(check.zoneId)).toLocalDate(), check.phase, observation)
        }
        .sortedByDescending { it.first }
        .map { it.second }
        .toList()
}
