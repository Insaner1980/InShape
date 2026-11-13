package com.emmah.inshape.transfer

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.emmah.inshape.data.*
import java.io.OutputStream

object PdfExporter {
    fun write(data: JournalSnapshot, selection: ExportSelection, output: OutputStream) {
        val workouts = selectedWorkouts(data, selection)
        val document = PdfDocument()
        var page: PdfDocument.Page? = null; var pageNumber = 0; var y = 74
        val paint = TextPaint().apply { color = Color.rgb(40, 58, 50); textSize = 12f; isAntiAlias = true }
        fun newPage() {
            page?.let { document.finishPage(it); page = null }
            require(++pageNumber <= 1000) { "This PDF is too long. Select fewer workouts and export again." }
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            page!!.canvas.drawColor(Color.WHITE)
            paint.textSize = 18f; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            page!!.canvas.drawText("InShape workout journal", 40f, 38f, paint)
            paint.textSize = 10f; paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            page!!.canvas.drawText("${workouts.size} selected records   Page $pageNumber", 40f, 57f, paint)
            page!!.canvas.drawText("Notes ${if (selection.includeNotes) "included" else "excluded"}   Check-ins ${if (selection.includeCheckIns) "included" else "excluded"}", 40f, 817f, paint)
            y = 76
        }
        fun paragraph(text: String, heading: Boolean = false) {
            if (text.isEmpty()) return
            if (page == null) newPage()
            val paragraphPaint = TextPaint(paint).apply { textSize = if (heading) 15f else 12f; typeface = Typeface.create("sans-serif", if (heading) Typeface.BOLD else Typeface.NORMAL) }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paragraphPaint, 515).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(3f, 1f).build()
            var first = 0
            while (first < layout.lineCount) {
                var last = first
                while (last < layout.lineCount && y + layout.getLineBottom(last) - layout.getLineTop(first) <= 787) last++
                if (last == first) { newPage(); continue }
                val height = layout.getLineBottom(last - 1) - layout.getLineTop(first)
                val canvas = page!!.canvas
                canvas.save(); canvas.clipRect(40, y, 555, y + height); canvas.translate(40f, (y - layout.getLineTop(first)).toFloat()); layout.draw(canvas); canvas.restore()
                y += height + 7; first = last
                if (first < layout.lineCount) newPage()
            }
        }
        try {
            workouts.forEach { workout ->
                paragraph("${workout.row.journalDate}  ${workout.row.title}", true)
                paragraph(if (workout.hasPerformedWork) "${workout.performedCount} exercises recorded" else "Note-only entry, not counted as attendance")
                paragraph(workout.row.durationSeconds?.let { "Recorded duration: ${it / 60} min ${it % 60} s" } ?: "Duration not recorded")
                paragraph(if (workout.row.start != null && workout.row.end != null) "Actual interval: ${workout.row.start} to ${workout.row.end} (${workout.row.zoneId})" else "Actual start/end interval not recorded")
                if (selection.includeNotes && workout.row.note.isNotBlank()) paragraph("Workout note\n${workout.row.note}")
                workout.items.filter { it.hasPerformedWork }.forEach { item ->
                    paragraph(item.row.name, true)
                    paragraph("${item.row.category}, ${item.row.equipmentName.ifBlank { "No machine name recorded" }}, ${item.row.basis}")
                    if (item.row.disposition != "DONE") paragraph("${item.row.disposition.lowercase().replace('_', ' ')}; recorded work retained")
                    if (item.completedSets.isEmpty()) paragraph("Completion only")
                    else item.completedSets.forEachIndexed { index, set -> paragraph("Set ${index + 1}: ${set.load?.let { "$it ${item.row.basis}" } ?: "load not recorded"}, ${set.reps?.let { "$it reps" } ?: "reps not recorded"}") }
                    item.row.durationSeconds?.let { paragraph("Exercise duration: ${it / 60} min ${it % 60} s") }
                    if (selection.includeNotes && item.row.note.isNotBlank()) paragraph("Exercise note\n${item.row.note}")
                    if (item.row.discomfort) paragraph("Discomfort noted")
                }
                if (selection.includeCheckIns) workout.checkIns.sortedBy { it.observedAt }.forEach { check ->
                    paragraph("${check.phase.lowercase().replace('_', ' ')} check-in", true)
                    paragraph("Observed ${check.observedAt} (${check.zoneId})")
                    paragraph("Energy: ${check.energy?.lowercase() ?: "not recorded"}. Effort: ${check.effort?.let { "$it of 10" } ?: "not recorded"}.")
                    if (check.feelings.isNotBlank()) paragraph("Feelings: ${check.feelings}")
                    if (selection.includeNotes && check.note.isNotBlank()) paragraph("Check-in note\n${check.note}")
                    data.observations.filter { it.checkInId == check.id }.forEach { observation ->
                        paragraph("${observation.region.replace('_', ' ')}: pain ${observation.pain?.toString() ?: "not recorded"}, stiffness ${observation.stiffness?.toString() ?: "not recorded"}")
                        if (selection.includeNotes && observation.note.isNotBlank()) paragraph(observation.note)
                    }
                }
                y += 12
            }
            page?.let { document.finishPage(it); page = null }
            document.writeTo(output)
        } finally { page?.let(document::finishPage); document.close() }
    }
}

internal fun selectedWorkouts(data: JournalSnapshot, selection: ExportSelection): List<Workout> {
    require(selection.workoutIds.isNotEmpty()) { "Select at least one completed record" }
    val workouts = data.history.filter { it.row.id in selection.workoutIds }.sortedBy { it.row.journalDate }
    require(workouts.size == selection.workoutIds.size) { "A selected record changed or is no longer completed. Review the selection." }
    return workouts
}
