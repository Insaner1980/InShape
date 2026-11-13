package com.emmah.inshape.data

import androidx.room.*

@Database(entities = [Exercise::class, PhotoAsset::class, Routine::class, RoutineItem::class, WorkoutEntity::class, SessionItem::class, SessionSet::class, CheckIn::class, BodyObservation::class, Preferences::class, EditorDraft::class, HealthShareState::class, HealthOutbox::class], version = 1, exportSchema = true)
@TypeConverters(JournalConverters::class)
abstract class JournalDatabase : RoomDatabase() { abstract fun journal(): JournalDao }

@Dao
interface JournalDao {
    @Query("SELECT * FROM Exercise ORDER BY name COLLATE NOCASE") suspend fun exercises(): List<Exercise>
    @Query("SELECT * FROM PhotoAsset ORDER BY position") suspend fun photos(): List<PhotoAsset>
    @Query("SELECT * FROM Routine ORDER BY title") suspend fun routines(): List<Routine>
    @Query("SELECT * FROM RoutineItem ORDER BY position") suspend fun routineItems(): List<RoutineItem>
    @Query("SELECT * FROM WorkoutEntity ORDER BY journalDate DESC, createdAt DESC") suspend fun workouts(): List<WorkoutEntity>
    @Query("SELECT * FROM SessionItem ORDER BY position") suspend fun items(): List<SessionItem>
    @Query("SELECT * FROM SessionSet ORDER BY position") suspend fun sets(): List<SessionSet>
    @Query("SELECT * FROM CheckIn") suspend fun checkIns(): List<CheckIn>
    @Query("SELECT * FROM BodyObservation") suspend fun observations(): List<BodyObservation>
    @Query("SELECT * FROM Preferences WHERE id=1") suspend fun preferences(): Preferences?
    @Query("SELECT * FROM EditorDraft") suspend fun drafts(): List<EditorDraft>
    @Query("SELECT * FROM HealthShareState") suspend fun healthStates(): List<HealthShareState>
    @Query("SELECT * FROM HealthOutbox") suspend fun outbox(): List<HealthOutbox>
    @Upsert suspend fun put(value: Exercise)
    @Upsert suspend fun put(value: PhotoAsset)
    @Upsert suspend fun put(value: Routine)
    @Upsert suspend fun put(value: RoutineItem)
    @Upsert suspend fun put(value: WorkoutEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWorkout(value: WorkoutEntity)
    @Upsert suspend fun put(value: SessionItem)
    @Upsert suspend fun put(value: SessionSet)
    @Upsert suspend fun put(value: CheckIn)
    @Upsert suspend fun put(value: BodyObservation)
    @Upsert suspend fun put(value: Preferences)
    @Upsert suspend fun put(value: EditorDraft)
    @Upsert suspend fun put(value: HealthShareState)
    @Upsert suspend fun put(value: HealthOutbox)
    @Query("DELETE FROM Routine WHERE id=:id") suspend fun deleteRoutine(id: String)
    @Query("DELETE FROM RoutineItem WHERE routineId=:id") suspend fun clearRoutineItems(id: String)
    @Query("DELETE FROM WorkoutEntity WHERE id=:id") suspend fun deleteWorkout(id: String)
    @Query("DELETE FROM SessionItem WHERE id=:id") suspend fun deleteItem(id: String)
    @Query("DELETE FROM SessionSet WHERE id=:id") suspend fun deleteSet(id: String)
    @Query("DELETE FROM BodyObservation WHERE checkInId=:id") suspend fun clearObservations(id: String)
    @Query("DELETE FROM PhotoAsset WHERE id=:id") suspend fun deletePhoto(id: String)
    @Query("DELETE FROM EditorDraft WHERE id=:id") suspend fun deleteDraft(id: String)
    @Query("DELETE FROM HealthOutbox WHERE workoutId=:id") suspend fun deleteOutbox(id: String)
    @Query("DELETE FROM HealthOutbox WHERE operation='UPSERT'") suspend fun cancelUpserts()
    @Query("DELETE FROM WorkoutEntity") suspend fun clearWorkouts()
    @Query("DELETE FROM Routine") suspend fun clearRoutines()
    @Query("DELETE FROM PhotoAsset") suspend fun clearPhotos()
    @Query("DELETE FROM Exercise") suspend fun clearExercises()
    @Query("DELETE FROM EditorDraft") suspend fun clearDrafts()
    @Query("DELETE FROM HealthShareState") suspend fun clearHealthStates()
    @Query("DELETE FROM HealthOutbox") suspend fun clearOutbox()
}
