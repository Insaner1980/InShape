package com.emmah.inshape.feature.music

import android.content.Context
import com.emmah.inshape.ui.requireUi
import com.emmah.inshape.R
import com.emmah.inshape.feature.assistant.schemaArray
import com.emmah.inshape.feature.assistant.schemaObject
import com.emmah.inshape.feature.assistant.schemaValue
import com.emmah.inshape.feature.assistant.text
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

internal fun String?.energyLabel(): Int = when (this) {
    "calm" -> R.string.energy_calm
    "steady" -> R.string.energy_steady
    "energetic" -> R.string.energy_energetic
    else -> R.string.energy_unlabeled
}

internal val playlistEnergyLabels = setOf("calm", "steady", "energetic")

internal data class PlaylistRequest(
    val minutes: Int?,
    val allowedArtists: Set<String>,
    val excludedArtists: Set<String>,
    val excludePrevious: Boolean = false,
    val stages: List<String> = emptyList(),
    val name: String? = null
)

internal data class PlaylistPlan(val tracks: List<MusicTrack>, val messages: List<String>)

internal val playlistSchema = schemaObject(
    "minutes" to schemaValue("integer"),
    "allowedArtists" to schemaArray(schemaValue("string", false), 30),
    "excludedArtists" to schemaArray(schemaValue("string", false), 30),
    "excludePrevious" to schemaValue("boolean", false),
    "stages" to schemaArray(schemaValue("string", false), 6),
    "name" to schemaValue("string")
)

internal const val PLAYLIST_INSTRUCTIONS = """
Interpret a Finnish or mixed-language request for music that is already stored on the device.
Return constraints only. Preserve explicit artist exclusions and allowlists. Return duration in whole minutes or null if absent.
Map a request for a calm start, energetic middle or similar sections onto the ordered stages calm, steady, energetic.
Never propose track ids, filenames, external music or made-up BPM. If no stage is stated, return an empty array.
Identify a previous-workout exclusion only if requested. Use a suggested name only when the user provides a title.
"""

internal fun playlistRequest(result: JsonObject): PlaylistRequest {
    fun words(key: String, maximum: Int) = (result[key] as? JsonArray).orEmpty()
        .filterIsInstance<JsonPrimitive>().filter(JsonPrimitive::isString).take(maximum).map { it.content.take(120) }
    val stages = words("stages", 6)
    requireUi(stages.all { it in playlistEnergyLabels }, R.string.playlist_energy_invalid)
    val minutes = (result["minutes"] as? JsonPrimitive)?.content?.toIntOrNull()
    requireUi(minutes == null || minutes in 1..240, R.string.playlist_target_range)
    return PlaylistRequest(
        minutes, words("allowedArtists", 30).toSet(), words("excludedArtists", 30).toSet(),
        result["excludePrevious"]?.toString() == "true", stages, result.text("name")?.take(120)
    )
}

internal fun planPlaylist(context: Context, tracks: List<MusicTrack>, request: PlaylistRequest, heard: Set<String>?): PlaylistPlan {
    val eligible = tracks.filter { it.available && artistMatches(it, request) }.distinctBy { it.id }.sortedBy { it.id }
    if (request.excludePrevious && heard == null) {
        return PlaylistPlan(emptyList(), listOf(context.getString(R.string.playlist_no_coverage)))
    }
    val unique = if (request.excludePrevious) eligible.filter { it.id !in heard.orEmpty() } else eligible
    val messages = buildList {
        if (request.minutes != null && unique.any { it.durationMs == null || it.durationMs <= 0 }) {
            add(context.getString(R.string.playlist_unknown_lengths))
        }
        if (request.stages.isNotEmpty() && unique.any { it.energy == null }) add(context.getString(R.string.playlist_unlabeled_energy))
    }.toMutableList()
    val candidates = if (request.minutes == null) unique else unique.filter { (it.durationMs ?: 0) > 0 }
    val target = request.minutes?.times(60_000L)
    val selected = if (request.stages.isEmpty()) {
        if (target == null) candidates.take(30) else bestDuration(candidates, target)
    } else {
        val pool = candidates.toMutableList()
        request.stages.flatMap { label ->
            val matching = pool.filter { it.energy == label }
            val chosen = if (target == null) matching.take(1) else bestDuration(matching, target / request.stages.size)
            if (chosen.isEmpty()) messages.add(context.getString(R.string.playlist_no_stage_track, context.getString(label.energyLabel()).lowercase()))
            val ids = chosen.map { it.id }.toSet()
            pool.removeAll { it.id in ids }
            chosen
        }
    }
    if (selected.isEmpty()) messages.add(context.getString(R.string.playlist_no_matching_tracks))
    return PlaylistPlan(selected, messages.distinct())
}

internal fun artistMatches(track: MusicTrack, request: PlaylistRequest): Boolean =
    (request.allowedArtists.isEmpty() || request.allowedArtists.any { it.equals(track.artist, ignoreCase = true) }) &&
        request.excludedArtists.none { it.equals(track.artist, ignoreCase = true) }

private data class DurationChoice(val total: Long, val track: MusicTrack?, val prev: DurationChoice?)

private fun bestDuration(tracks: List<MusicTrack>, target: Long): List<MusicTrack> {
    if (tracks.isEmpty()) return emptyList()
    // Retain one candidate per second, keeping millisecond accuracy when comparing totals.
    val choices = mutableMapOf(0L to DurationChoice(0, null, null))
    val longestUseful = target + tracks.minOf { it.durationMs!! }
    tracks.filter { it.durationMs!! <= longestUseful }.take(600).forEach { track ->
        val duration = track.durationMs!!
        choices.values.toList().forEach { choice ->
            val total = choice.total + duration
            if (total > longestUseful) return@forEach
            val bucket = total / 1000
            val existing = choices[bucket]
            if (existing == null || abs(target - total) < abs(target - existing.total)) choices[bucket] = DurationChoice(total, track, choice)
        }
    }
    val lowestError = choices.values.filter { it.track != null }.minWithOrNull(
        compareBy<DurationChoice> { abs(target - it.total) }.thenBy { it.total }
    ) ?: return listOf(tracks.minWith(compareBy<MusicTrack> { abs(target - it.durationMs!!) }.thenBy { it.id }))
    return generateSequence(lowestError) { it.prev }.mapNotNull { it.track }.toList().asReversed()
}
