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
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS AssistantDraft (`key` TEXT NOT NULL PRIMARY KEY, id TEXT NOT NULL, " +
                "input TEXT NOT NULL, review TEXT)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS AssistantReceipt (id TEXT NOT NULL PRIMARY KEY, digest TEXT NOT NULL, " +
                "targetId TEXT NOT NULL, resultIds TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS AssistantMemory (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, exerciseId TEXT, " +
                "text TEXT NOT NULL, creationWorkoutId TEXT, acknowledgedAt TEXT, revision INTEGER NOT NULL)"
        )
    }
}

internal val journalMigration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // These fixed IDs identify the demo defaults. Program links can also point to user exercises.
        val renames = listOf(
            Triple("f6eb71ef-0917-3851-90bf-0ce78876ea8a", "Lämmittelypyörä", "Kuntopyörä"),
            Triple("fadf83f1-90ec-302a-b990-d5af9db95019", "Rintapunnerruslaite", "Rintapunnerrus laitteessa"),
            Triple("4a59773e-755e-3631-84dd-71de6dec1660", "Ylätalja kuminauhalla", "Alasveto kuminauhalla")
        )
        for ((id, oldName, newName) in renames) {
            db.execSQL("UPDATE Exercise SET name = ? WHERE id = ? AND name = ?", arrayOf(newName, id, oldName))
        }
    }
}

internal val journalMigration3To4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE ProgramDayOverride (catalogVersion INTEGER NOT NULL, programId TEXT NOT NULL, " +
                "dayId TEXT NOT NULL, day TEXT NOT NULL, PRIMARY KEY(catalogVersion, programId, dayId))"
        )
    }
}
