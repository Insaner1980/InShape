package com.emmah.inshape.feature.music

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity
data class MusicSource(@PrimaryKey val slot: Int = 0, val tree: String)

@Entity
data class MusicTrack(
    @PrimaryKey val id: String,
    val tree: String,
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long?,
    val available: Boolean = true,
    val energy: String? = null,
    val contentFingerprint: String? = null
)

@Entity(indices = [Index("workoutId")])
data class ListeningEvent(
    @PrimaryKey val id: String,
    val trackId: String,
    val workoutId: String?,
    val startedAt: String,
    val playedMs: Long
)

@Entity
data class PlaylistReceipt(@PrimaryKey val id: String, val digest: String, val playlistId: String)

@Entity
data class MusicPlaylist(@PrimaryKey val id: String, val name: String)

@Entity(
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(entity = MusicPlaylist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MusicTrack::class, parentColumns = ["id"], childColumns = ["trackId"])
    ],
    indices = [Index("trackId")]
)
data class MusicMembership(val playlistId: String, val trackId: String, val position: Int)

@Dao
interface MusicDao {
    @Query("SELECT * FROM ListeningEvent WHERE workoutId = :workoutId")
    suspend fun listening(workoutId: String): List<ListeningEvent>

    @Query("SELECT * FROM ListeningEvent WHERE id = :id")
    suspend fun listeningEvent(id: String): ListeningEvent?

    @Upsert
    suspend fun saveListening(event: ListeningEvent)

    @Query("UPDATE MusicTrack SET energy = :energy WHERE id = :id AND available = 1")
    suspend fun setEnergy(id: String, energy: String?)

    @Query("SELECT * FROM MusicSource LIMIT 1")
    suspend fun source(): MusicSource?

    @Upsert
    suspend fun saveSource(source: MusicSource)

    @Query("SELECT * FROM MusicTrack ORDER BY title COLLATE NOCASE, artist COLLATE NOCASE, id")
    suspend fun tracks(): List<MusicTrack>

    @Query("SELECT * FROM MusicTrack WHERE id IN (:ids)")
    suspend fun tracksById(ids: List<String>): List<MusicTrack>

    @Query("UPDATE MusicTrack SET available = 0 WHERE available = 1")
    suspend fun markUnavailable()

    @Upsert
    suspend fun saveTracks(tracks: List<MusicTrack>)
}

@Dao
interface MusicPlaylistDao {
    @Query("SELECT * FROM PlaylistReceipt WHERE id = :id")
    suspend fun receipt(id: String): PlaylistReceipt?

    @Upsert
    suspend fun saveReceipt(receipt: PlaylistReceipt)

    @Query("SELECT * FROM MusicPlaylist ORDER BY rowid")
    suspend fun playlists(): List<MusicPlaylist>

    @Query("SELECT * FROM MusicPlaylist WHERE id = :id")
    suspend fun playlist(id: String): MusicPlaylist?

    @Upsert
    suspend fun savePlaylist(playlist: MusicPlaylist)

    @Query("DELETE FROM MusicPlaylist WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("SELECT * FROM MusicMembership ORDER BY position")
    suspend fun memberships(): List<MusicMembership>

    @Query("SELECT trackId FROM MusicMembership WHERE playlistId = :id ORDER BY position")
    suspend fun trackIds(id: String): List<String>

    @Query("DELETE FROM MusicMembership WHERE playlistId = :id")
    suspend fun clearMemberships(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMemberships(memberships: List<MusicMembership>)
}

@Database(
    entities = [
        MusicSource::class, MusicTrack::class, MusicPlaylist::class, MusicMembership::class,
        ListeningEvent::class, PlaylistReceipt::class
    ],
    version = 2
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun music(): MusicDao
    abstract fun playlists(): MusicPlaylistDao
}
