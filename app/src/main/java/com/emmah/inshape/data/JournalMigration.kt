package com.emmah.inshape.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val journalMigration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE RoutineItem ADD COLUMN plan TEXT")
        db.execSQL("ALTER TABLE WorkoutEntity ADD COLUMN program TEXT")
        db.execSQL("ALTER TABLE SessionItem ADD COLUMN plan TEXT")
        db.execSQL("ALTER TABLE SessionSet ADD COLUMN plannedReps TEXT")
        db.execSQL("ALTER TABLE SessionSet ADD COLUMN rir INTEGER")
        db.execSQL("ALTER TABLE SessionSet ADD COLUMN perSide INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ProgramEnrollment (slot INTEGER NOT NULL PRIMARY KEY, id TEXT NOT NULL, " +
                "programId TEXT NOT NULL, catalogVersion INTEGER NOT NULL, baselineDate TEXT NOT NULL, " +
                "phaseWeek INTEGER NOT NULL, nextDayIndex INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ProgramExerciseLink (catalogExerciseId TEXT NOT NULL, exerciseId TEXT NOT NULL, " +
                "PRIMARY KEY(catalogExerciseId, exerciseId))"
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS AssistantDraft (`key` TEXT NOT NULL PRIMARY KEY, id TEXT NOT NULL, input TEXT NOT NULL, review TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS AssistantReceipt (id TEXT NOT NULL PRIMARY KEY, digest TEXT NOT NULL, targetId TEXT NOT NULL, resultIds TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS AssistantMemory (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, exerciseId TEXT, " +
                "text TEXT NOT NULL, creationWorkoutId TEXT, acknowledgedAt TEXT, revision INTEGER NOT NULL)"
        )
    }
}
