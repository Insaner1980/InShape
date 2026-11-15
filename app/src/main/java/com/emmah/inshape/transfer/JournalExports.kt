package com.emmah.inshape.transfer

import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportSelection(val workoutIds: Set<String>, val includeNotes: Boolean = false, val includeCheckIns: Boolean = false)
object JournalExports {
    // Keep the exported table schemas together; this is a flat format declaration.
    @Suppress("LongMethod")
    fun csv(data: JournalSnapshot, selection: ExportSelection, output: OutputStream) {
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
                    "workout_id",
                    "journal_date",
                    "title",
                    "start_utc",
                    "end_utc",
                    "zone_id",
                    "duration_seconds",
                    "effort",
                    "session_note"
                ),
                workouts.asSequence().map { workout ->
                    val row = workout.row
                    listOf(
                        row.id, row.journalDate, row.title, row.start, row.end, row.zoneId, row.durationSeconds,
                        if (selection.includeCheckIns) {
                            workout.checkIns.find {
                                it.phase == "AFTER"
                            }?.effort
                        } else {
                            null
                        },
                        if (selection.includeNotes) row.note else null
                    )
                }
            )
            table(
                "exercises.csv",
                listOf(
                    "session_item_id",
                    "workout_id",
                    "exercise_id",
                    "equipment_id",
                    "name_snapshot",
                    "category",
                    "measurement_basis",
                    "disposition",
                    "completion_kind",
                    "performed_duration_seconds",
                    "exercise_note",
                    "discomfort"
                ),
                items.asSequence().map { item ->
                    val row = item.row
                    listOf(
                        row.id, row.workoutId, row.exerciseId, row.equipmentId, row.name, row.category, row.basis, row.disposition,
                        if (item.completedSets.isEmpty()) "COMPLETION_ONLY" else "SETS",
                        row.durationSeconds,
                        if (selection.includeNotes) row.note else null,
                        row.discomfort
                    )
                }
            )
            table(
                "sets.csv",
                listOf(
                    "set_id",
                    "session_item_id",
                    "set_order",
                    "completed_at",
                    "load_value",
                    "load_unit",
                    "reps"
                ),
                items.asSequence().flatMap { item ->
                    item.completedSets.asSequence().map { set ->
                        listOf(
                            set.id,
                            set.itemId,
                            set.position + 1,
                            set.completedAt,
                            set.load,
                            if (set.load !=
                                null
                            ) {
                                item.row.basis
                            } else {
                                null
                            },
                            set.reps
                        )
                    }
                }
            )
            table(
                "check_ins.csv",
                listOf("check_in_id", "workout_id", "phase", "observed_at", "zone_id", "energy", "effort", "feelings", "note"),
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
                        if (selection.includeNotes) check.note else null
                    )
                }
            )
            table(
                "body_observations.csv",
                listOf("check_in_id", "region_id", "pain", "stiffness", "note"),
                data.observations.asSequence().filter {
                    it.checkInId in
                        checkIds
                }.map {
                    listOf(
                        it.checkInId,
                        it.region,
                        it.pain,
                        it.stiffness,
                        if (selection.includeNotes) it.note else null
                    )
                }
            )
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(
                (
                    "InShape CSV export\nUTF-8, comma-separated, every field quoted, doubled internal quotes, " +
                        "CRLF row endings. Quoted fields can contain newlines.\n" +
                        "Dates use ISO 8601. Times marked UTC include their offset. Decimal loads use a dot. Empty " +
                        "fields mean not recorded or excluded, not zero. Stable IDs link tables.\n" +
                        "Only selected completed records are exported. Exercise rows contain performed work, " +
                        "including partial exercises. Sets contain completed sets only. COMPLETION_ONLY does not " +
                        "imply a set.\n" +
                        "To prevent spreadsheet formulas, text whose first non-space character is =, +, - or @, or " +
                        "which starts with a tab or line break, is prefixed with one apostrophe. Remove that " +
                        "prefix to recover such text. The full backup preserves exact original text.\n" +
                        "Notes included: ${selection.includeNotes}. Check-ins included: ${selection.includeCheckIns}.\n"
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
