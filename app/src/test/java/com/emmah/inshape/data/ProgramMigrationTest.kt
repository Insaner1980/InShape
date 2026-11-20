package com.emmah.inshape.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.feature.music.MusicDatabase
import com.emmah.inshape.feature.music.musicMigration1To2
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgramMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun opensARealV1JournalAndKeepsLegacyValues() = runBlocking {
        val name = "journal-migration-${newId()}"
        createV1(name, "com.emmah.inshape.data.JournalDatabase") { legacy ->
            legacy.execSQL(
                "INSERT INTO Exercise VALUES(?, ?, '', 'Machine', '', 'Gym', 0, 0, '', '', ?, '', 'kg', '[]', '[]', '[]')",
                arrayOf("old-exercise", "Legacy leg press", "actual-machine")
            )
            legacy.execSQL(
                "INSERT INTO EditorDraft VALUES('finish:fixture', 'finish', '{}', '2026-11-20T08:00:00Z', 1)"
            )
            legacy.execSQL(
                "INSERT INTO WorkoutEntity(id,title,kind,state,journalDate,zoneId,durationSource,timingNeedsReview,timingConfirmed," +
                    "note,revision,createdAt,modifiedAt) VALUES('old-workout','Legacy','LIVE','COMPLETED','2026-11-20','UTC'," +
                    "'UNKNOWN',0,0,'',1,'2026-11-20T08:00:00Z','2026-11-20T08:00:00Z')"
            )
            legacy.execSQL(
                "INSERT INTO SessionItem(id,workoutId,exerciseId,equipmentId,name,equipmentName,category,basis,setup,position," +
                    "disposition,note,discomfort,mode) VALUES('old-item','old-workout','old-exercise','actual-machine','Leg press',''," +
                    "'Machine','kg','',0,'DONE','',0,'SETS')"
            )
            legacy.execSQL(
                "INSERT INTO SessionSet VALUES('old-set', 'old-item', 0, '35', 12, '42,5', '12', '42.5', 12, '2026-11-20T08:00:00Z')"
            )
        }
        val db = Room.databaseBuilder(context, JournalDatabase::class.java, name).addMigrations(journalMigration1To2).build()
        try {
            val dao = db.journal()
            assertEquals("actual-machine", dao.exercises().single().equipmentId)
            assertEquals("{}", dao.drafts().single().payload)
            assertEquals("42.5", dao.sets().single().load)
            assertEquals("42,5", dao.sets().single().rawLoad)
            assertNull(dao.sets().single().plannedReps)
            assertNull(dao.programEnrollment())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun opensARealV1MusicCatalogAndKeepsThePlaylistOrder() = runBlocking {
        val name = "music-migration-${newId()}"
        createV1(name, "com.emmah.inshape.feature.music.MusicDatabase") { legacy ->
            legacy.execSQL("INSERT INTO MusicSource VALUES(0, 'content://fixture/music')")
            legacy.execSQL("INSERT INTO MusicTrack VALUES('track', 'content://fixture/music', 'content://fixture/song', 'Name', 'Artist', 3000, 1)")
            legacy.execSQL("INSERT INTO MusicPlaylist VALUES('playlist', 'Playlist')")
            legacy.execSQL("INSERT INTO MusicMembership VALUES('playlist', 'track', 0)")
        }
        val db = Room.databaseBuilder(context, MusicDatabase::class.java, name).addMigrations(musicMigration1To2).build()
        try {
            assertEquals(listOf("track"), db.playlists().trackIds("playlist"))
            assertEquals(3000L, db.music().tracks().single().durationMs)
            assertNull(db.music().tracks().single().energy)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private fun createV1(name: String, schemaDirectory: String, populate: (SQLiteDatabase) -> Unit) {
        val schema = File("schemas/$schemaDirectory/1.json")
        require(schema.isFile) { "Run this test from the app module." }
        val definition = Json.parseToJsonElement(schema.readText()) as JsonObject
        val database = definition.getValue("database") as JsonObject
        val target = context.getDatabasePath(name)
        require(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
        SQLiteDatabase.openOrCreateDatabase(target, null).use { db ->
            (database.getValue("entities") as JsonArray).forEach { element ->
                val entity = element as JsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                fun statement(field: JsonObject) {
                    db.execSQL(field.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
                statement(entity)
                (entity["indices"] as? JsonArray).orEmpty().forEach { statement(it as JsonObject) }
            }
            (database.getValue("setupQueries") as JsonArray).forEach { db.execSQL(it.jsonPrimitive.content) }
            db.version = 1
            populate(db)
        }
    }
}
