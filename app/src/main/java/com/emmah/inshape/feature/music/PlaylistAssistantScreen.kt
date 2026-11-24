package com.emmah.inshape.feature.music

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.AppContainer
import com.emmah.inshape.R
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.assistant.AssistantFailure
import com.emmah.inshape.feature.assistant.AssistantFailureKind
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.requireUi
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.ui.uiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PlaylistAssistantScreen(
    app: AppContainer,
    previousWorkout: Workout?,
    playlistId: String?,
    onSettings: () -> Unit,
    planningDispatcher: CoroutineDispatcher = Dispatchers.Default,
    onSave: (String) -> Unit
) {
    val store = app.music
    val catalog by store.catalog.collectAsStateWithLifecycle()
    val activePlaylist = catalog.playlists.find { it.id == playlistId }
    var question by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var command by rememberSaveable { mutableStateOf(newId()) }
    var requirements by remember { mutableStateOf<PlaylistRequest?>(null) }
    var suggestion by remember { mutableStateOf<PlaylistPlan?>(null) }
    var tree by remember { mutableStateOf<String?>(null) }
    var reviewedPlaylist by remember { mutableStateOf<SavedPlaylist?>(null) }
    var previewWorkout by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var working by remember { mutableStateOf<Job?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var needsKey by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun suggest(request: PlaylistRequest) {
        val workout = previousWorkout
        val heard = if (request.excludePrevious && workout != null) store.heardInWorkout(workout.row.id) else null
        val tracks = catalog.songs
        suggestion = withContext(planningDispatcher) { planPlaylist(app.context, tracks, request, heard) }
        requirements = request
        name = activePlaylist?.name ?: request.name ?: ""
        command = newId()
        tree = catalog.source?.tree
        reviewedPlaylist = activePlaylist
        previewWorkout = workout?.let { it.row.id to it.row.revision }
    }

    fun buildPreview() {
        message = null
        working = scope.launch {
            try {
                val answer = app.assistant.interpret(PLAYLIST_INSTRUCTIONS, question.trim(), playlistSchema)
                suggest(playlistRequest(answer.proposal))
            } catch (failure: AssistantFailure) {
                message = app.context.getString(failure.kind.message)
                needsKey = failure.kind in setOf(AssistantFailureKind.NO_KEY, AssistantFailureKind.UNREADABLE_KEY)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = app.context.getString(R.string.playlist_request_failed)
            } finally {
                working = null
            }
        }
    }

    fun savePreview(reviewed: PlaylistPlan, request: PlaylistRequest) {
        saving = true
        message = null
        scope.launch {
            try {
                if (request.excludePrevious) requireCurrentPlaylistWorkout(app, previewWorkout)
                requireUi(playlistId == null || reviewedPlaylist?.id == playlistId, R.string.playlist_removed)
                onSave(store.confirmPlaylist(command, tree, reviewedPlaylist, name, reviewed.tracks))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IllegalArgumentException) {
                message = failure.uiMessage(app.context, R.string.review_playlist_again)
            } catch (_: Exception) {
                message = app.context.getString(R.string.playlist_save_failed)
            } finally {
                saving = false
            }
        }
    }

    val reviewed = suggestion
    val request = requirements
    Page(bottomBar = {
        if (reviewed == null || request == null) {
            PrimaryButton(
                stringResource(R.string.build_playlist_preview),
                modifier = Modifier.fillMaxWidth(),
                enabled = working == null && question.isNotBlank() && catalog.songs.isNotEmpty(),
                onClick = ::buildPreview
            )
        } else {
            PrimaryButton(
                stringResource(R.string.save_playlist),
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving && name.isNotBlank() && reviewed.tracks.isNotEmpty(),
                onClick = { savePreview(reviewed, request) }
            )
        }
    }) {
        if (reviewed == null || request == null) {
            TextField(question, stringResource(R.string.write_request), { question = it.take(4000) })
            if (working != null) {
                OutlineButton(stringResource(R.string.cancel_request), modifier = Modifier.fillMaxWidth(), onClick = {
                    working?.cancel()
                })
            }
        } else {
            TextField(name, stringResource(R.string.playlist_name), { name = it.take(120) })
            PlaylistPreviewSummary(request, reviewed.tracks)
            reviewed.messages.forEach { Text(it) }
            if (request.excludePrevious && reviewed.tracks.isEmpty()) {
                OutlineButton(stringResource(R.string.skip_previous_workout_filter), modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch { suggest(request.copy(excludePrevious = false)) }
                })
            }
            PlaylistPreviewTracks(reviewed.tracks) { tracks ->
                suggestion = reviewed.copy(tracks = tracks)
            }
            OutlineButton(stringResource(R.string.edit_request), modifier = Modifier.fillMaxWidth(), enabled = !saving, onClick = {
                suggestion = null
            })
        }
        message?.let { Text(it) }
        if (needsKey) OutlineButton(stringResource(R.string.assistant_settings), onSettings, Modifier.fillMaxWidth())
    }
}

private suspend fun requireCurrentPlaylistWorkout(app: AppContainer, previewWorkout: Pair<String, Long>?) {
    val latest = app.repository.snapshot().history.filter { it.qualifiesForAttendance }
        .maxWithOrNull(compareBy({ it.row.journalDate }, { it.row.createdAt }, { it.row.id }))
    requireUi(
        previewWorkout != null && latest?.row?.id == previewWorkout.first && latest.row.revision == previewWorkout.second,
        R.string.playlist_history_changed
    )
}

@Composable
private fun PlaylistPreviewSummary(request: PlaylistRequest, tracks: List<MusicTrack>) {
    if (request.allowedArtists.isNotEmpty()) Text(stringResource(R.string.only_artists, request.allowedArtists.joinToString()))
    if (request.excludedArtists.isNotEmpty()) Text(stringResource(R.string.excluded_artists, request.excludedArtists.joinToString()))
    if (request.stages.isNotEmpty()) {
        val stages = request.stages.map { stringResource(it.energyLabel()) }.joinToString(" → ")
        Text(stringResource(R.string.energy_order, stages))
    }
    val durations = tracks.mapNotNull { it.durationMs }
    val total = durations.sum()
    Text(stringResource(R.string.known_duration, formatDuration(total / 1000)))
    if (durations.size != tracks.size) {
        Text(stringResource(R.string.unknown_track_lengths, tracks.size - durations.size))
    }
    request.minutes?.let { minutes ->
        val difference = total - minutes * 60_000L
        if (difference != 0L) {
            val label = if (difference < 0) R.string.shorter_playlist else R.string.longer_playlist
            Text(stringResource(label, formatDuration(kotlin.math.abs(difference) / 1000)))
        }
    }
}

@Composable
private fun PlaylistPreviewTracks(tracks: List<MusicTrack>, onChange: (List<MusicTrack>) -> Unit) {
    ListCard {
        tracks.forEachIndexed { index, track ->
            item {
                val duration = track.durationMs?.let { formatDuration(it / 1000) }
                ListRow(track.title, subtitle = listOfNotNull(track.artist, duration).joinToString(", "))
                ActionRow {
                    if (index > 0) {
                        OutlineButton(stringResource(R.string.move_up), {
                            onChange(buildList {
                                addAll(tracks)
                                add(index - 1, removeAt(index))
                            })
                        })
                    }
                    OutlineButton(stringResource(R.string.remove), { onChange(tracks.filterNot { it.id == track.id }) })
                }
            }
        }
    }
}

@Composable
internal fun TrackEnergyScreen(store: MusicStore, id: String, onSave: () -> Unit) {
    val catalog by store.catalog.collectAsStateWithLifecycle()
    val track = catalog.songs.find { it.id == id }
    val scope = rememberCoroutineScope()
    Page {
        Text(track?.title ?: stringResource(R.string.track), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.current_energy, stringResource(track?.energy.energyLabel())))
        if (track?.available == true) {
            (playlistEnergyLabels.toList() + null).forEach { label ->
                OutlineButton(
                    stringResource(label.energyLabel()).replaceFirstChar(Char::uppercase),
                    onClick = {
                        scope.launch {
                            store.setEnergy(id, label)
                            onSave()
                        }
                    }
                )
            }
        }
    }
}
