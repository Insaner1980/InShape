package com.emmah.inshape.transfer

import android.content.Context
import com.emmah.inshape.R
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.feature.library.categoryLabel
import com.emmah.inshape.feature.library.exerciseDisplayName
import com.emmah.inshape.feature.workout.itemSummary
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportSelection(val workoutIds: Set<String>, val includeNotes: Boolean = false, val includeCheckIns: Boolean = false)
object JournalExports {
    // Keep the exported table schemas together; this is a flat format declaration.
    @Suppress("LongMethod")
    fun csv(context: Context, data: JournalSnapshot, selection: ExportSelection, output: OutputStream) {
        val workouts = selectedWorkouts(data, selection)
        val ids = workouts.map { it.row.id }.toSet()
        val items = workouts.flatMap { it.items }.filter { it.hasPerformedWork }
        val checkIns = if (selection.includeCheckIns) {
            data.checkIns.filter {
                it.workoutId in ids
            }
        } else {
            emptyList()
        }
        val checkIds = checkIns.map { it.id }.toSet()
        ZipOutputStream(object : FilterOutputStream(output) {
            override fun close() {
                flush()
            }
        }).use { zip ->
            fun table(name: String, header: List<String>, rows: Sequence<List<Any?>>) {
                zip.putNextEntry(ZipEntry(name))
                fun row(values: List<Any?>) {
                    zip.write(
                        (
                            values.joinToString(",") { cell(it) } +
                                "\r\n"
                            ).toByteArray(Charsets.UTF_8)
                    )
                }
                row(header)
                rows.forEach(::row)
                zip.closeEntry()
            }
            table(
                "workouts.csv",
                listOf(
                    context.getString(R.string.csv_workout_id),
                    context.getString(R.string.csv_journal_date),
                    context.getString(R.string.csv_title),
                    context.getString(R.string.csv_start_utc),
                    context.getString(R.string.csv_end_utc),
                    context.getString(R.string.csv_zone_id),
                    context.getString(R.string.csv_duration_seconds),
                    context.getString(R.string.csv_effort),
                    context.getString(R.string.csv_session_note)
                ),
                workouts.asSequence().map { workout ->
                    val row = workout.row
                    listOf(
                        row.id, row.journalDate, row.title, row.start, row.end, row.zoneId, row.durationSeconds,
                        workout.checkIns.takeIf { selection.includeCheckIns }?.find { it.phase == "AFTER" }?.effort,
                        row.note.takeIf { selection.includeNotes }
                    )
                }
            )
            table(
                "exercises.csv",
                listOf(
                    context.getString(R.string.csv_session_item_id),
                    context.getString(R.string.csv_workout_id),
                    context.getString(R.string.csv_exercise_id),
                    context.getString(R.string.csv_equipment_id),
                    context.getString(R.string.csv_name_snapshot),
                    context.getString(R.string.csv_category),
                    context.getString(R.string.csv_measurement_basis),
                    context.getString(R.string.csv_disposition),
                    context.getString(R.string.csv_completion_kind),
                    context.getString(R.string.csv_performed_duration_seconds),
                    context.getString(R.string.csv_exercise_note),
                    context.getString(R.string.csv_discomfort),
                    context.getString(R.string.csv_set_summary)
                ),
                items.asSequence().map { item ->
                    val row = item.row
                    listOf(
                        row.id, row.workoutId, row.exerciseId, row.equipmentId,
                        row.name.exerciseDisplayName(
                            context
                        ),
                        row.category.categoryLabel(context), row.basis, row.disposition,
                        if (item.completedSets.isEmpty()) "COMPLETION_ONLY" else "SETS",
                        row.durationSeconds,
                        row.note.takeIf { selection.includeNotes },
                        row.discomfort,
                        itemSummary(context, item)
                    )
                }
            )
            table(
                "sets.csv",
                listOf(
                    context.getString(R.string.csv_set_id),
                    context.getString(R.string.csv_session_item_id),
                    context.getString(R.string.csv_set_order),
                    context.getString(R.string.csv_completed_at),
                    context.getString(R.string.csv_load_value),
                    context.getString(R.string.csv_load_unit),
                    context.getString(R.string.csv_reps)
                ),
                items.asSequence().flatMap { item ->
                    item.completedSets.asSequence().map { set ->
                        listOf(
                            set.id,
                            set.itemId,
                            set.position + 1,
                            set.completedAt,
                            set.load,
                            item.row.basis.takeIf { set.load != null },
                            set.reps
                        )
                    }
                }
            )
            table(
                "check_ins.csv",
                listOf(
                    context.getString(
                        R.string.csv_check_in_id
                    ),
                    context.getString(
                        R.string.csv_workout_id
                    ),
                    context.getString(
                        R.string.csv_phase
                    ),
                    context.getString(
                        R.string.csv_observed_at
                    ),
                    context.getString(
                        R.string.csv_zone_id
                    ),
                    context.getString(
                        R.string.csv_energy
                    ),
                    context.getString(R.string.csv_effort), context.getString(R.string.csv_feelings), context.getString(R.string.csv_note)
                ),
                checkIns.asSequence().map { check ->
                    listOf(
                        check.id,
                        check.workoutId,
                        check.phase,
                        check.observedAt,
                        check.zoneId,
                        check.energy,
                        check.effort,
                        check.feelings,
                        check.note.takeIf { selection.includeNotes }
                    )
                }
            )
            table(
                "body_observations.csv",
                listOf(
                    context.getString(R.string.csv_check_in_id),
                    context.getString(R.string.csv_region_id),
                    context.getString(R.string.csv_pain),
                    context.getString(R.string.csv_stiffness),
                    context.getString(R.string.csv_note)
                ),
                data.observations.asSequence().filter {
                    it.checkInId in
                        checkIds
                }.map {
                    listOf(
                        it.checkInId,
                        it.region,
                        it.pain,
                        it.stiffness,
                        it.note.takeIf { selection.includeNotes }
                    )
                }
            )
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(
                context.getString(
                    R.string.export_csv_readme,
                    context.getString(if (selection.includeNotes) R.string.export_included else R.string.export_excluded),
                    context.getString(if (selection.includeCheckIns) R.string.export_included else R.string.export_excluded)
                ).toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()
        }
    }
    private fun cell(value: Any?): String {
        var text = value?.toString() ?: ""
        if (value is String &&
            (
                text.trimStart().firstOrNull() in setOf('=', '+', '-', '@') ||
                    text.firstOrNull() in setOf('\t', '\r', '\n')
                )
        ) {
            text = "'$text"
        }
        return "\"${text.replace("\"", "\"\"")}\""
    }
}
