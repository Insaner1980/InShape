package com.emmah.inshape.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Upsert

@Database(
    entities = [
        Exercise::class,
        PhotoAsset::class,
        Routine::class,
        RoutineItem::class,
        WorkoutEntity::class,
        SessionItem::class,
        SessionSet::class,
        CheckIn::class,
        BodyObservation::class,
        Preferences::class,
        EditorDraft::class,
        HealthShareState::class,
        HealthOutbox::class,
        ProgramEnrollment::class,
        ProgramExerciseLink::class,
        AssistantDraft::class,
        AssistantReceipt::class,
        AssistantMemory::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(JournalConverters::class, ProgramConverters::class)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journal(): JournalDao
}

@Dao
// Room exposes typed operations for each journal table through this generated DAO.
@Suppress("TooManyFunctions")
interface JournalDao {
    @Query("SELECT * FROM AssistantDraft WHERE `key` = :key")
    suspend fun assistantDraft(key: String): AssistantDraft?

    @Query("SELECT * FROM AssistantReceipt WHERE id = :id")
    suspend fun assistantReceipt(id: String): AssistantReceipt?

    @Query("SELECT * FROM AssistantMemory")
    suspend fun assistantMemories(): List<AssistantMemory>

    @Upsert suspend fun put(value: AssistantDraft)
    @Upsert suspend fun put(value: AssistantReceipt)
    @Upsert suspend fun put(value: AssistantMemory)

    @Query("DELETE FROM AssistantDraft WHERE `key` = :key AND id = :id")
    suspend fun deleteAssistantDraft(key: String, id: String)

    @Query("DELETE FROM AssistantMemory WHERE id = :id AND revision = :revision")
    suspend fun deleteAssistantMemory(id: String, revision: Long)

    @Query("DELETE FROM AssistantDraft")
    suspend fun clearAssistantDrafts()

    @Query("DELETE FROM AssistantReceipt")
    suspend fun clearAssistantReceipts()

    @Query("DELETE FROM AssistantMemory")
    suspend fun clearAssistantMemories()
    @Query("SELECT * FROM ProgramEnrollment WHERE slot = 1")
    suspend fun programEnrollment(): ProgramEnrollment?

    @Query("SELECT * FROM ProgramExerciseLink")
    suspend fun programLinks(): List<ProgramExerciseLink>

    @Upsert suspend fun put(value: ProgramEnrollment)
    @Upsert suspend fun put(value: ProgramExerciseLink)

    @Query("DELETE FROM ProgramEnrollment")
    suspend fun clearProgramEnrollment()

    @Query("DELETE FROM ProgramExerciseLink")
    suspend fun clearProgramLinks()
    @Query("SELECT * FROM Exercise ORDER BY name COLLATE NOCASE")
    suspend fun exercises(): List<Exercise>

    @Query("SELECT * FROM PhotoAsset ORDER BY position")
    suspend fun photos(): List<PhotoAsset>

    @Query("SELECT * FROM Routine ORDER BY title")
    suspend fun routines(): List<Routine>

    @Query("SELECT * FROM RoutineItem ORDER BY position")
    suspend fun routineItems(): List<RoutineItem>

    @Query("SELECT * FROM WorkoutEntity ORDER BY journalDate DESC, createdAt DESC")
    suspend fun workouts(): List<WorkoutEntity>

    @Query("SELECT * FROM SessionItem ORDER BY position")
    suspend fun items(): List<SessionItem>

    @Query("SELECT * FROM SessionSet ORDER BY position")
    suspend fun sets(): List<SessionSet>

    @Query("SELECT * FROM CheckIn")
    suspend fun checkIns(): List<CheckIn>

    @Query("SELECT * FROM BodyObservation")
    suspend fun observations(): List<BodyObservation>

    @Query("SELECT * FROM Preferences WHERE id=1")
    suspend fun preferences(): Preferences?

    @Query("SELECT * FROM EditorDraft")
    suspend fun drafts(): List<EditorDraft>

    @Query("SELECT * FROM HealthShareState")
    suspend fun healthStates(): List<HealthShareState>

    @Query("SELECT * FROM HealthOutbox")
    suspend fun outbox(): List<HealthOutbox>

    @Upsert suspend fun put(value: Exercise)

    @Upsert suspend fun put(value: PhotoAsset)

    @Upsert suspend fun put(value: Routine)

    @Upsert suspend fun put(value: RoutineItem)

    @Upsert suspend fun put(value: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkout(value: WorkoutEntity)

    @Upsert suspend fun put(value: SessionItem)

    @Upsert suspend fun put(value: SessionSet)

    @Upsert suspend fun put(value: CheckIn)

    @Upsert suspend fun put(value: BodyObservation)

    @Upsert suspend fun put(value: Preferences)

    @Upsert suspend fun put(value: EditorDraft)

    @Upsert suspend fun put(value: HealthShareState)

    @Upsert suspend fun put(value: HealthOutbox)

    @Query("DELETE FROM Routine WHERE id=:id")
    suspend fun deleteRoutine(id: String)

    @Query("DELETE FROM RoutineItem WHERE routineId=:id")
    suspend fun clearRoutineItems(id: String)

    @Query("DELETE FROM WorkoutEntity WHERE id=:id")
    suspend fun deleteWorkout(id: String)

    @Query("DELETE FROM SessionItem WHERE id=:id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM SessionSet WHERE id=:id")
    suspend fun deleteSet(id: String)

    @Query("DELETE FROM BodyObservation WHERE checkInId=:id")
    suspend fun clearObservations(id: String)

    @Query("DELETE FROM PhotoAsset WHERE id=:id")
    suspend fun deletePhoto(id: String)

    @Query("DELETE FROM EditorDraft WHERE id=:id")
    suspend fun deleteDraft(id: String)

    @Query("DELETE FROM HealthOutbox WHERE workoutId=:id")
    suspend fun deleteOutbox(id: String)

    @Query("DELETE FROM HealthOutbox WHERE workoutId=:id AND operation='UPSERT'")
    suspend fun cancelUpsert(id: String): Int

    @Query("DELETE FROM HealthOutbox WHERE operation='UPSERT'")
    suspend fun cancelUpserts()

    @Query("DELETE FROM WorkoutEntity")
    suspend fun clearWorkouts()

    @Query("DELETE FROM Routine")
    suspend fun clearRoutines()

    @Query("DELETE FROM PhotoAsset")
    suspend fun clearPhotos()

    @Query("DELETE FROM Exercise")
    suspend fun clearExercises()

    @Query("DELETE FROM EditorDraft")
    suspend fun clearDrafts()

    @Query("DELETE FROM HealthShareState")
    suspend fun clearHealthStates()

    @Query("DELETE FROM HealthOutbox")
    suspend fun clearOutbox()
}
