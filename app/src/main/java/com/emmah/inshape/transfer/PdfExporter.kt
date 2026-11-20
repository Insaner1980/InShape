package com.emmah.inshape.transfer

import android.graphics.Paint
import com.emmah.inshape.ui.theme.longDate
import com.emmah.inshape.ui.requireUi
import com.emmah.inshape.feature.library.exerciseDisplayName
import com.emmah.inshape.feature.library.categoryLabel
import android.content.Context
import com.emmah.inshape.R
import com.emmah.inshape.feature.workout.setSummary
import com.emmah.inshape.data.SetTarget
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.feature.workout.workoutTimeRange
import com.emmah.inshape.ui.theme.displayedDurationSeconds
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.feature.workout.itemSummary
import java.time.ZoneId
import java.time.Instant
import java.time.LocalDate
import com.emmah.inshape.feature.checkin.effortLevelOf
import com.emmah.inshape.ui.theme.workoutTime
import com.emmah.inshape.ui.theme.energyText
import com.emmah.inshape.ui.theme.dateTime
import androidx.compose.ui.graphics.toArgb
import com.emmah.inshape.ui.theme.LightColors
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withClip
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.ui.theme.observationText
import com.emmah.inshape.ui.theme.shortDate
import java.io.OutputStream

private const val PDF_FONT_FAMILY = "sans-serif"

object PdfExporter {
    fun write(context: Context, data: JournalSnapshot, selection: ExportSelection, output: OutputStream) {
        val workouts = selectedWorkouts(data, selection)
        val observationsByCheckIn = if (selection.includeCheckIns) data.observations.groupBy { it.checkInId } else emptyMap()
        PdfPages(context, workouts.size).use { pages ->
            workouts.forEach { workout ->
                pages.writeWorkout(workout, selection.includeNotes)
                if (selection.includeCheckIns) {
                    workout.checkIns.sortedBy { it.observedAt }.forEach { check ->
                        pages.writeCheckIn(check, workout.row.journalDate, observationsByCheckIn[check.id].orEmpty(), selection.includeNotes)
                    }
                }
                pages.addSectionSpacing()
            }
            pages.writeTo(output)
        }
    }

    private fun PdfPages.writeWorkout(workout: Workout, includeNotes: Boolean) {
        val row = workout.row
        paragraph("${LocalDate.parse(row.journalDate).longDate()} ${row.title}", heading = true)
        paragraph(
            if (workout.hasPerformedWork) {
                context.getString(R.string.export_recorded_exercises, workout.performedCount)
            } else {
                context.getString(R.string.note_only_workout)
            }
        )
        row.displayedDurationSeconds()?.let { paragraph(context.getString(R.string.export_recorded_duration, formatDuration(it))) }
        if (row.start != null && row.end != null) {
            val zone = ZoneId.of(row.zoneId)
            val range = workoutTimeRange(Instant.parse(row.start), Instant.parse(row.end), zone)
            paragraph(
                if (zone == ZoneId.systemDefault()) context.getString(R.string.export_actual_interval, range)
                else context.getString(R.string.export_actual_interval_with_zone, range, row.zoneId)
            )
        }
        if (includeNotes && row.note.isNotBlank()) {
            addSectionSpacing()
            paragraph(context.getString(R.string.export_workout_note), heading = true)
            paragraph(row.note)
        }
        workout.items.filter { it.hasPerformedWork }.forEach { writeExercise(it, includeNotes) }
    }

    private fun PdfPages.writeExercise(item: WorkoutItem, includeNotes: Boolean) {
        val row = item.row
        paragraph(row.name.exerciseDisplayName(context), heading = true)
        paragraph("${row.category.categoryLabel(context)}, ${context.getString(MeasurementBasis.fromKey(row.basis).optionLabel)}")
        if (row.disposition != "DONE") {
            paragraph(context.getString(if (row.disposition == "SKIPPED") R.string.export_skipped_exercise else R.string.export_partial_exercise))
        }
        paragraph(itemSummary(context, item))
        val sets = item.completedSets
        if (sets.map { it.load to it.reps }.distinct().size > 1) {
            val basis = MeasurementBasis.fromKey(row.basis)
            sets.forEachIndexed { index, recorded ->
                val result = setSummary(context, basis, listOf(SetTarget(recorded.load, recorded.reps)))
                val missing = listOfNotNull(
                    context.getString(R.string.export_no_load).takeIf { recorded.load == null && basis != MeasurementBasis.Bodyweight },
                    context.getString(R.string.export_no_reps).takeIf { recorded.reps == null }
                )
                paragraph(context.getString(R.string.export_set, index + 1, (listOf(result) + missing).joinToString(", ")))
            }
        }
        if (includeNotes && row.note.isNotBlank()) paragraph(context.getString(R.string.export_exercise_note, row.note))
        if (row.discomfort) paragraph(context.getString(R.string.discomfort_noted))
    }

    private fun PdfPages.writeCheckIn(
        check: CheckIn,
        workoutDate: String,
        observations: List<BodyObservation>,
        includeNotes: Boolean
    ) {
        paragraph(
            when (check.phase) {
                "BEFORE" -> context.getString(R.string.before_workout)
                "AFTER" -> context.getString(R.string.after_workout)
                else -> context.getString(R.string.next_day)
            },
            heading = true
        )
        val instant = Instant.parse(check.observedAt)
        val zone = ZoneId.of(check.zoneId)
        paragraph(
            if (instant.atZone(zone).toLocalDate().toString() == workoutDate) instant.workoutTime(zone, true)
            else dateTime(instant, zone, true)
        )
        listOfNotNull(effortLevelOf(check.effort)?.let { context.getString(it.label) }, energyText(context, check.energy))
            .takeIf { it.isNotEmpty() }?.let { paragraph(it.joinToString(", ")) }
        if (check.feelings.isNotBlank()) paragraph(context.getString(R.string.export_feelings, check.feelings))
        if (includeNotes && check.note.isNotBlank()) paragraph(context.getString(R.string.export_check_in_note, check.note))
        observations.forEach { observation ->
            paragraph(observationText(context, observation))
            if (includeNotes && observation.note.isNotBlank()) paragraph(observation.note)
        }
    }
}

private class PdfPages(val context: Context, private val workoutCount: Int) : AutoCloseable {
    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var y = 74
    private val paint = TextPaint().apply {
        color = LightColors.text.toArgb()
        textSize = 12f
        isAntiAlias = true
    }

    private fun newPage() {
        finishPage()
        requireUi(++pageNumber <= 1000, R.string.export_pdf_too_long)
        val nextPage = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        page = nextPage
        val canvas = nextPage.canvas
        canvas.drawColor(LightColors.background.toArgb())
        paint.textSize = 18f
        paint.typeface = Typeface.create(PDF_FONT_FAMILY, Typeface.BOLD)
        canvas.drawText(context.getString(R.string.export_title), 40f, 38f, paint)
        paint.textSize = 10f
        paint.typeface = Typeface.create(PDF_FONT_FAMILY, Typeface.NORMAL)
        canvas.drawText(context.getString(R.string.export_workout_count, workoutCount), 40f, 57f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(context.getString(R.string.export_page, pageNumber), 555f, 57f, paint)
        paint.textAlign = Paint.Align.LEFT
        y = 76
    }

    fun paragraph(text: String, heading: Boolean = false) {
        if (text.isEmpty()) return
        if (page == null) newPage()
        val layout = paragraphLayout(text, heading)
        var first = 0
        while (first < layout.lineCount) {
            var last = first
            while (last < layout.lineCount && y + layout.getLineBottom(last) - layout.getLineTop(first) <= 787) {
                last++
            }
            if (last == first) {
                newPage()
                continue
            }
            val height = layout.getLineBottom(last - 1) - layout.getLineTop(first)
            page!!.canvas.withClip(40, y, 555, y + height) {
                translate(40f, (y - layout.getLineTop(first)).toFloat())
                layout.draw(this)
            }
            y += height + 7
            first = last
            if (first < layout.lineCount) newPage()
        }
    }

    private fun paragraphLayout(text: String, heading: Boolean): StaticLayout {
        val paragraphPaint = TextPaint(paint).apply {
            textSize = if (heading) 15f else 12f
            typeface = Typeface.create(PDF_FONT_FAMILY, if (heading) Typeface.BOLD else Typeface.NORMAL)
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paragraphPaint, 515)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(3f, 1f)
            .build()
    }

    fun addSectionSpacing() {
        y += 12
    }

    fun writeTo(output: OutputStream) {
        finishPage()
        document.writeTo(output)
    }

    override fun close() {
        finishPage()
        document.close()
    }

    private fun finishPage() {
        page?.let {
            document.finishPage(it)
            page = null
        }
    }
}

internal fun selectedWorkouts(data: JournalSnapshot, selection: ExportSelection): List<Workout> {
    requireUi(selection.workoutIds.isNotEmpty(), R.string.export_empty_selection)
    val workouts = data.history.filter {
        it.row.id in selection.workoutIds
    }.sortedBy { it.row.journalDate }
    requireUi(workouts.size == selection.workoutIds.size, R.string.export_selection_changed)
    return workouts
}
