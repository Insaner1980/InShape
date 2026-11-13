package com.emmah.inshape.transfer

import com.emmah.inshape.data.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.*
import java.security.MessageDigest
import java.util.zip.*

class BackupRestoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val time = "2026-11-13T08:00:00Z"
    private fun fixture(): Pair<JournalSnapshot, File> {
        val exercise = Exercise(name = "Näyttö, \"one\"", settings = listOf(Setting("Seat", "4")), notes = "First\nSecond", favorite = true)
        val photo = temporary.newFile().apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val asset = PhotoAsset(exerciseId = exercise.id, path = "original.png", mediaType = "image/png", byteLength = photo.length(), checksum = MessageDigest.getInstance("SHA-256").digest(photo.readBytes()).joinToString("") { "%02x".format(it) })
        val workout = WorkoutEntity(journalDate = "2026-11-13", zoneId = "Europe/Helsinki", createdAt = time, modifiedAt = time)
        val item = SessionItem(workoutId = workout.id, exerciseId = exercise.id, equipmentId = exercise.equipmentId, name = exercise.name, category = exercise.category, basis = "kg")
        val set = SessionSet(itemId = item.id, rawLoad = "27,5", rawReps = "10", load = "27.5", reps = 10, completedAt = time)
        val check = CheckIn(workoutId = workout.id, phase = "BEFORE", observedAt = time, zoneId = "Europe/Helsinki")
        val routine = Routine(title = "Routine")
        return JournalSnapshot(exercises = listOf(exercise), photos = listOf(asset), routines = listOf(routine), routineItems = listOf(RoutineItem(routineId = routine.id, exerciseId = exercise.id, sets = listOf(SetTarget("27.5", 10)))),
            workouts = listOf(workout), items = listOf(item), sets = listOf(set, SessionSet(itemId = item.id, position = 1, rawLoad = "27,")), checkIns = listOf(check), observations = listOf(BodyObservation(check.id, "left_knee", pain = 0)),
            drafts = listOf(EditorDraft("exercise:${exercise.id}", "exercise", "{\"raw\":\"unfinished\"}", time), EditorDraft("duration:${item.id}", "duration", "", time)),
            healthStates = listOf(HealthShareState(workout.id, clientVersion = 15)), preferences = Preferences(name = "Emma", remindersEnabled = true, reminderDays = listOf(1, 4))) to photo
    }
    private fun archive(data: JournalSnapshot, photo: File): ByteArray = ByteArrayOutputStream().also { BackupArchive.write(data, { photo }, it, time, "1.0") }.toByteArray()
    @Test fun roundTripRetainsPendingDeletionOfAnAlreadyDeletedWorkout() {
        val (data, photo) = fixture()
        val state = HealthShareState(newId(), clientVersion = 5, acknowledgedRevision = 3)
        val deletion = HealthOutbox(state.workoutId, state.clientRecordId, 5, "DELETE")
        val source = data.copy(healthStates = data.healthStates + state, outbox = listOf(deletion))
        val restored = BackupArchive.read(ByteArrayInputStream(archive(source, photo)), temporary.newFolder()).data
        assertEquals(listOf(deletion), restored.outbox)
    }
    @Test fun roundTripPreservesWholeJournalAndActualAssetBytes() {
        val (data, photo) = fixture(); val staged = BackupArchive.read(ByteArrayInputStream(archive(data, photo)), temporary.newFolder())
        assertEquals(data.copy(photos = staged.data.photos), staged.data)
        assertArrayEquals(photo.readBytes(), File(staged.directory, staged.data.photos.single().path).readBytes())
        assertEquals(0, staged.data.observations.single().pain); assertNull(staged.data.observations.single().stiffness)
        assertEquals("27,", staged.data.sets.last().rawLoad)
    }
    @Test fun damagedMissingAndUndeclaredEntriesAreRejected() {
        val (data, photo) = fixture(); val original = archive(data, photo)
        val entries = unzip(original); val asset = entries.keys.first { it.startsWith("photos/") }
        listOf(entries + (asset to byteArrayOf(9)), entries - asset, entries + ("../escape" to byteArrayOf(1)), entries + ("unlisted.json" to byteArrayOf(1))).forEach { changed ->
            assertThrows(Exception::class.java) { BackupArchive.read(ByteArrayInputStream(zip(changed)), temporary.newFolder()) }
        }
    }
    @Test fun unsupportedVersionAndInsufficientStorageAreRejected() {
        val (data, photo) = fixture(); val original = archive(data, photo); val entries = unzip(original)
        val manifest = Json.parseToJsonElement(entries.getValue("manifest.json").decodeToString()).jsonObject
        val newer = JsonObject(manifest + ("formatVersion" to JsonPrimitive(999)))
        assertThrows(Exception::class.java) { BackupArchive.read(ByteArrayInputStream(zip(entries + ("manifest.json" to newer.toString().toByteArray()))), temporary.newFolder()) }
        assertThrows(Exception::class.java) { BackupArchive.read(ByteArrayInputStream(original), temporary.newFolder(), 2) }
    }
    @Test fun invalidRelationshipsAndCompletedNumbersCannotBeBackedUp() {
        val (data, photo) = fixture()
        assertThrows(Exception::class.java) { archive(data.copy(items = data.items.map { it.copy(workoutId = newId()) }), photo) }
        assertThrows(Exception::class.java) { archive(data.copy(sets = data.sets.map { if (it.completedAt != null) it.copy(load = "-2") else it }), photo) }
        assertThrows(Exception::class.java) { archive(data.copy(workouts = data.workouts + data.workouts.single().copy(id = newId())), photo) }
    }
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap { ZipInputStream(ByteArrayInputStream(bytes)).use { stream -> while (true) { val entry = stream.nextEntry ?: break; put(entry.name, stream.readBytes()) } } }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } } }.toByteArray()
}
