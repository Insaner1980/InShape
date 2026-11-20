package com.emmah.inshape.feature.music

import com.emmah.inshape.R
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.net.toUri
import androidx.room.Room
import androidx.room.withTransaction
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SavedPlaylist(val id: String, val name: String, val tracks: List<MusicTrack>, val fixtureArtwork: PlaylistArtworkFamily? = null)

data class MusicCatalog(
    val source: MusicSource? = null,
    val songs: List<MusicTrack> = emptyList(),
    val playlists: List<SavedPlaylist> = emptyList(),
    val accessLost: Boolean = false
)

class MusicStore(
    private val context: Context,
    name: String,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val resolver = context.contentResolver
    private val database = Room.databaseBuilder(context, MusicDatabase::class.java, name).addMigrations(musicMigration1To2).build()
    private val dao = database.music()
    private val playlists = database.playlists()
    private val accessCheck = MutableStateFlow(0)
    private var scanJob: Job? = null
    private val scanState = MutableStateFlow(false)
    private val scanMessage = MutableStateFlow<String?>(null)
    private var unreachableSource: String? = null
    val scanning: StateFlow<Boolean> = scanState
    val message: StateFlow<String?> = scanMessage

    val catalog = kotlinx.coroutines.flow.combine(
        database.invalidationTracker.createFlow("MusicSource", "MusicTrack", "MusicPlaylist", "MusicMembership"),
        accessCheck
    ) { _, _ -> readCatalog() }.stateIn(scope, SharingStarted.WhileSubscribed(), MusicCatalog())

    private suspend fun readCatalog(): MusicCatalog = database.withTransaction {
        val source = dao.source()
        val tracks = dao.tracks()
        val byId = tracks.associateBy { it.id }
        val memberships = playlists.memberships().groupBy { it.playlistId }
        val accessLost = source != null && !hasGrant(source.tree.toUri())
        val folderUnavailable = source != null && (accessLost || source.tree == unreachableSource)
        fun MusicTrack.withAccess() = if (folderUnavailable) copy(available = false) else this
        MusicCatalog(
            source,
            tracks.filter { it.tree == source?.tree }.map { it.withAccess() },
            playlists.playlists().map { playlist ->
                SavedPlaylist(
                    playlist.id,
                    playlist.name,
                    memberships[playlist.id].orEmpty().map { byId.getValue(it.trackId).withAccess() }
                )
            },
            accessLost
        )
    }

    private fun hasGrant(tree: Uri): Boolean = resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }

    fun checkAccess() {
        accessCheck.update { it + 1 }
    }

    @Synchronized
    fun scan(tree: Uri? = null) {
        if (scanJob != null) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var selected: Uri? = null
            try {
                selected = tree ?: dao.source()?.tree?.toUri() ?: return@launch
                scanFolder(selected)
                unreachableSource = null
                clearMusicArtwork()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                selected?.toString()?.let { uri ->
                    if (dao.source()?.tree == uri) unreachableSource = uri
                }
                scanMessage.value = context.getString(R.string.music_folder_unavailable)
            }
        }
        scanJob = job
        scanMessage.value = null
        scanState.value = true
        job.invokeOnCompletion {
            synchronized(this) {
                if (scanJob === job) {
                    checkAccess()
                    scanJob = null
                    scanState.value = false
                }
            }
        }
        job.start()
    }

    @Synchronized
    fun cancelScan() {
        scanJob?.cancel()
    }

    private suspend fun scanFolder(tree: Uri) = withContext(ioDispatcher) {
        require(tree.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(tree))
        val alreadyGranted = hasGrant(tree)
        if (!alreadyGranted) resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            val tracks = readTree(tree)
            check(hasGrant(tree)) { "Music folder permission was revoked" }
            database.withTransaction {
                val previous = dao.tracks().groupBy { it.tree to it.uri }
                dao.markUnavailable()
                dao.saveTracks(tracks.map { track -> identifyScannedTrack(track, previous[track.tree to track.uri].orEmpty()) })
                dao.saveSource(MusicSource(tree = tree.toString()))
            }
        } finally {
            if (!alreadyGranted) {
                // Keep the grant if the transaction committed just before cancellation.
                withContext(kotlinx.coroutines.NonCancellable) {
                    if (dao.source()?.tree != tree.toString()) {
                        resolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            }
        }
    }

    private suspend fun readTree(tree: Uri): List<MusicTrack> {
        val tracks = linkedMapOf<String, MusicTrack>()
        val pending = ArrayDeque<String>().apply { add(DocumentsContract.getTreeDocumentId(tree)) }
        val visited = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val parent = pending.removeLast()
            if (!visited.add(parent)) continue
            val documents = readAudioDocuments(tree, parent, pending)
            for ((id, name, flags) in documents) {
                currentCoroutineContext().ensureActive()
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                val stableId = UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString()
                if (stableId !in tracks) {
                    val partial = Build.VERSION.SDK_INT >= 29 && flags and Document.FLAG_PARTIAL != 0
                    val local = !partial && flags and Document.FLAG_VIRTUAL_DOCUMENT == 0
                    val track = readTrack(tree, uri, stableId, name, local)
                    tracks[stableId] = if (track.available) {
                        val fingerprint = audioFingerprint(uri)
                        track.copy(available = fingerprint != null, contentFingerprint = fingerprint)
                    } else {
                        track
                    }
                }
            }
        }
        return tracks.values.toList()
    }

    private suspend fun audioFingerprint(uri: Uri): String? = try {
        resolver.openInputStream(uri)?.use { stream ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().toHexString()
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun readTrack(tree: Uri, uri: Uri, id: String, name: String, local: Boolean): MusicTrack {
        val filename = name.substringBeforeLast('.').ifBlank { name }
        val fallback = MusicTrack(id, tree.toString(), uri.toString(), filename, context.getString(R.string.unknown_artist), null, local)
        if (!local) return fallback
        return try {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                readTrackTags(descriptor.fileDescriptor, fallback)
            } ?: fallback.copy(available = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            fallback.copy(available = false)
        } catch (_: SecurityException) {
            fallback.copy(available = false)
        }
    }

    private suspend fun readAudioDocuments(tree: Uri, parent: String, pending: ArrayDeque<String>): List<Triple<String, String, Int>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        return resolver.query(
            children,
            arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING) ||
                cursor.extras.getString(DocumentsContract.EXTRA_ERROR) != null
            ) {
                throw IOException("Incomplete folder listing")
            }
            buildList<Triple<String, String, Int>> {
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val id = cursor.getString(0)
                    val name = cursor.getString(1) ?: id
                    val mime = cursor.getString(2).orEmpty()
                    if (mime == Document.MIME_TYPE_DIR) {
                        pending.add(id)
                    } else if (mime.startsWith("audio/") || name.substringAfterLast('.').lowercase() in audioExtensions) {
                        add(Triple(id, name, cursor.getInt(3)))
                    }
                }
            }
        } ?: throw IOException("Folder could not be read")
    }

    private fun readTrackTags(descriptor: java.io.FileDescriptor, fallback: MusicTrack): MusicTrack {
        val tags = MediaMetadataRetriever()
        try {
            return try {
                tags.setDataSource(descriptor)
                fallback.copy(
                    title = tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank)
                        ?: fallback.title,
                    artist = tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank)
                        ?: fallback.artist,
                    durationMs = tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        ?.takeIf { it > 0L }
                )
            } catch (_: RuntimeException) {
                // Keep damaged files visible. Playback reports a decoder error with the song name.
                fallback
            }
        } finally {
            tags.release()
        }
    }

    suspend fun resolve(ids: List<String>, requireAvailable: Boolean = true): List<MusicTrack> {
        val rows = ids.distinct().chunked(500).flatMap { dao.tracksById(it) }.associateBy { it.id }
        val grants = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        return ids.mapNotNull { id ->
            rows[id]?.takeIf { (!requireAvailable || it.available) && it.tree in grants }
        }
    }

    suspend fun create(name: String, selections: List<String>): String = database.withTransaction {
        require(name.isNotBlank())
        val id = UUID.randomUUID().toString()
        playlists.savePlaylist(MusicPlaylist(id, name.trim()))
        insertMemberships(id, selections)
        id
    }

    suspend fun rename(id: String, name: String) = database.withTransaction {
        require(name.isNotBlank())
        val playlist = playlists.playlist(id) ?: return@withTransaction
        playlists.savePlaylist(playlist.copy(name = name.trim()))
    }

    suspend fun add(id: String, selections: List<String>) = database.withTransaction {
        saveOrder(id, playlists.trackIds(id) + selections)
    }

    suspend fun saveOrder(id: String, trackIds: List<String>) = database.withTransaction {
        if (playlists.playlist(id) == null) return@withTransaction
        playlists.clearMemberships(id)
        insertMemberships(id, trackIds)
    }

    private suspend fun insertMemberships(id: String, trackIds: List<String>) {
        playlists.insertMemberships(trackIds.distinct().mapIndexed { i, track -> MusicMembership(id, track, i) })
    }

    suspend fun delete(id: String) = playlists.deletePlaylist(id)

    internal suspend fun setEnergy(id: String, energy: String?) {
        require(energy == null || energy in playlistEnergyLabels)
        dao.setEnergy(id, energy)
    }

    internal suspend fun heardInWorkout(workoutId: String): Set<String>? = database.withTransaction {
        val events = dao.listening(workoutId)
        if (events.isEmpty()) return@withTransaction null
        val duration = dao.tracks().associate { it.id to it.durationMs }
        events.groupBy { it.trackId }.filter { (track, intervals) ->
            val threshold = minOf(duration[track]?.takeIf { it > 0 } ?: 10_000L, 10_000L)
            intervals.sumOf { it.playedMs } >= threshold
        }.keys
    }

    internal fun recordListening(event: ListeningEvent) {
        if (event.playedMs <= 0) return
        scope.launch {
            try {
                database.withTransaction {
                    if ((dao.listeningEvent(event.id)?.playedMs ?: 0L) < event.playedMs) dao.saveListening(event)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Playback remains independent of listening history.
            }
        }
    }

    internal suspend fun confirmPlaylist(
        commandId: String,
        tree: String?,
        previous: SavedPlaylist?,
        name: String,
        tracks: List<MusicTrack>
    ): String = database.withTransaction {
        val payload = listOf(tree, previous?.id, previous?.name, name, tracks.joinToString { it.id }).joinToString("|")
        val digest = com.emmah.inshape.feature.assistant.fingerprint(payload)
        playlists.receipt(commandId)?.let {
            require(it.digest == digest) { "This playlist was already saved from a different review." }
            return@withTransaction it.playlistId
        }
        require(name.isNotBlank() && tracks.isNotEmpty() && tracks.map { it.id }.distinct().size == tracks.size)
        val current = readCatalog()
        require(current.source?.tree == tree && tree != null && current.songs.containsAll(tracks) && tracks.all { it.available }) {
            "The music folder or selected tracks changed. Review again."
        }
        val id = if (previous == null) {
            create(name, tracks.map { it.id })
        } else {
            require(current.playlists.find { it.id == previous.id } == previous) { "The playlist changed. Review again." }
            rename(previous.id, name)
            saveOrder(previous.id, tracks.map { it.id })
            previous.id
        }
        playlists.saveReceipt(PlaylistReceipt(commandId, digest, id))
        id
    }

    fun close() = database.close()

    private companion object {
        val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus")
    }
}

internal fun identifyScannedTrack(scanned: MusicTrack, previous: List<MusicTrack>): MusicTrack {
    val fingerprint = scanned.contentFingerprint
    if (fingerprint == null) {
        val last = previous.firstOrNull { it.available } ?: previous.firstOrNull()
        return last?.copy(available = false) ?: scanned
    }
    val matching = previous.find { it.contentFingerprint == fingerprint }
        ?: previous.singleOrNull()?.takeIf { it.contentFingerprint == null }
    if (matching != null) return scanned.copy(id = matching.id, energy = matching.energy)
    if (previous.isEmpty()) return scanned
    val id = UUID.nameUUIDFromBytes("${scanned.tree}\n${scanned.uri}\n$fingerprint".encodeToByteArray()).toString()
    return scanned.copy(id = id)
}
