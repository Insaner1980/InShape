package com.emmah.inshape.data

import androidx.room.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable data class Setting(val label: String, val value: String)
@Serializable data class SavedLink(val label: String, val url: String)
@Serializable data class SetTarget(val load: String? = null, val reps: Int? = null)

@Serializable
@Entity(indices = [Index("name"), Index("equipmentId")])
data class Exercise(
    @PrimaryKey val id: String = newId(), val name: String = "", val nickname: String = "",
    val category: String = "Machine", val muscles: String = "", val location: String = "Gym",
    val favorite: Boolean = false, val archived: Boolean = false,
    val instructions: String = "", val notes: String = "",
    val equipmentId: String = newId(), val equipmentName: String = "", val basis: String = "kg",
    val settings: List<Setting> = emptyList(), val links: List<SavedLink> = emptyList(),
    val alternatives: List<String> = emptyList(),
)

@Serializable
@Entity(foreignKeys = [ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)], indices = [Index("exerciseId")])
data class PhotoAsset(@PrimaryKey val id: String = newId(), val exerciseId: String, val path: String, val mediaType: String, val byteLength: Long, val checksum: String, val position: Int = 0)

@Serializable @Entity
data class Routine(@PrimaryKey val id: String = newId(), val title: String = "", val description: String = "", val durationMinutes: Int? = null, val revision: Long = 1)

@Serializable
@Entity(foreignKeys = [ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"])], indices = [Index("routineId"), Index("exerciseId")])
data class RoutineItem(@PrimaryKey val id: String = newId(), val routineId: String, val exerciseId: String, val position: Int = 0, val mode: String = "SETS", val durationSeconds: Long? = null, val note: String = "", val sets: List<SetTarget> = emptyList())

@Serializable
@Entity(indices = [Index(value = ["activeKey"], unique = true), Index("journalDate"), Index("state")])
data class WorkoutEntity(
    @PrimaryKey val id: String = newId(), val title: String = "Workout", val sourceRoutineId: String? = null, val sourceWorkoutId: String? = null,
    val kind: String = "LIVE", val state: String = "ACTIVE", val activeKey: Int? = 1,
    val journalDate: String, val start: String? = null, val end: String? = null, val zoneId: String,
    val startOffset: String? = null, val endOffset: String? = null,
    val durationSeconds: Long? = null, val durationSource: String = "UNKNOWN", val timingNeedsReview: Boolean = false,
    val timingConfirmed: Boolean = false, val note: String = "", val currentItemId: String? = null,
    val revision: Long = 1, val createdAt: String, val modifiedAt: String,
)

@Serializable
@Entity(foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)], indices = [Index("workoutId"), Index("exerciseId")])
data class SessionItem(
    @PrimaryKey val id: String = newId(), val workoutId: String, val exerciseId: String, val equipmentId: String,
    val name: String, val equipmentName: String = "", val category: String, val basis: String, val setup: String = "",
    val position: Int = 0, val disposition: String = "PLANNED", val alternativeOf: String? = null,
    val note: String = "", val discomfort: Boolean = false, val quickCompletedAt: String? = null,
    val durationSeconds: Long? = null, val targetDurationSeconds: Long? = null, val mode: String = "SETS",
)

@Serializable
@Entity(foreignKeys = [ForeignKey(entity = SessionItem::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)], indices = [Index("itemId")])
data class SessionSet(
    @PrimaryKey val id: String = newId(), val itemId: String, val position: Int = 0,
    val suggestedLoad: String? = null, val suggestedReps: Int? = null,
    val rawLoad: String = "", val rawReps: String = "",
    val load: String? = null, val reps: Int? = null, val completedAt: String? = null,
)

@Serializable
@Entity(foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["workoutId", "phase"], unique = true)])
data class CheckIn(@PrimaryKey val id: String = newId(), val workoutId: String, val phase: String, val observedAt: String, val zoneId: String, val energy: String? = null, val effort: Int? = null, val feelings: String = "", val note: String = "")

@Serializable
@Entity(primaryKeys = ["checkInId", "region"], foreignKeys = [ForeignKey(entity = CheckIn::class, parentColumns = ["id"], childColumns = ["checkInId"], onDelete = ForeignKey.CASCADE)])
data class BodyObservation(val checkInId: String = "", val region: String, val pain: Int? = null, val stiffness: Int? = null, val note: String = "")

@Serializable @Entity
data class Preferences(
    @PrimaryKey val id: Int = 1, val name: String = "", val weeklyGoal: Int? = null,
    val remindersEnabled: Boolean = false, val reminderDays: List<Int> = emptyList(), val reminderTime: String = "18:00", val remindersHeld: Boolean = false,
    val shareWorkouts: Boolean = false, val readSteps: Boolean = false, val readSleep: Boolean = false, val readHeartRate: Boolean = false, val heartRateSource: String? = null,
    val lastBackupAt: String? = null,
)

@Serializable @Entity
data class EditorDraft(@PrimaryKey val id: String, val kind: String, val payload: String, val modifiedAt: String, val version: Int = 1)

@Serializable @Entity
data class HealthShareState(@PrimaryKey val workoutId: String, val clientRecordId: String = "inshape:workout:$workoutId", val clientVersion: Long = 1, val acknowledgedRevision: Long = 0, val platformId: String? = null, val status: String = "PENDING", val error: String? = null, val restoredHold: Boolean = false)

@Serializable @Entity
data class HealthOutbox(@PrimaryKey val workoutId: String, val clientRecordId: String, val revision: Long, val operation: String, val attempts: Int = 0, val error: String? = null)

class JournalConverters {
    @TypeConverter fun settingsToJson(value: List<Setting>): String = Json.encodeToString(value)
    @TypeConverter fun settingsFromJson(value: String): List<Setting> = Json.decodeFromString(value)
    @TypeConverter fun linksToJson(value: List<SavedLink>): String = Json.encodeToString(value)
    @TypeConverter fun linksFromJson(value: String): List<SavedLink> = Json.decodeFromString(value)
    @TypeConverter fun stringsToJson(value: List<String>): String = Json.encodeToString(value)
    @TypeConverter fun stringsFromJson(value: String): List<String> = Json.decodeFromString(value)
    @TypeConverter fun intsToJson(value: List<Int>): String = Json.encodeToString(value)
    @TypeConverter fun intsFromJson(value: String): List<Int> = Json.decodeFromString(value)
    @TypeConverter fun targetsToJson(value: List<SetTarget>): String = Json.encodeToString(value)
    @TypeConverter fun targetsFromJson(value: String): List<SetTarget> = Json.decodeFromString(value)
}
