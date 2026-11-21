package com.emmah.inshape.feature.music

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val musicMigration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE MusicTrack ADD COLUMN energy TEXT")
        db.execSQL("ALTER TABLE MusicTrack ADD COLUMN contentFingerprint TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ListeningEvent (id TEXT NOT NULL PRIMARY KEY, trackId TEXT NOT NULL, " +
                "workoutId TEXT, startedAt TEXT NOT NULL, playedMs INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ListeningEvent_workoutId ON ListeningEvent(workoutId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS PlaylistReceipt (id TEXT NOT NULL PRIMARY KEY, digest TEXT NOT NULL, playlistId TEXT NOT NULL)")
    }
}
