package com.emmah.inshape.transfer

import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.EditorDraft
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.HealthOutbox
import com.emmah.inshape.data.HealthShareState
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.PhotoAsset
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.data.Routine
import com.emmah.inshape.data.RoutineItem
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.SetTarget
import com.emmah.inshape.data.Setting
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.workout.FinishDraft
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupRestoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val time = "2026-11-13T08:00:00Z"
    private fun fixture(): Pair<JournalSnapshot, File> {
        val exercise =
            Exercise(
                name = "Näyttö, \"one\"",
                settings = listOf(Setting("Seat", "4")),
                notes = "First\nSecond",
                favorite = true
            )
        val photo = temporary.newFile().apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val asset =
            PhotoAsset(
                exerciseId = exercise.id,
                path = "original.png",
                mediaType = "image/png",
                byteLength = photo.length(),
                checksum = MessageDigest.getInstance(
                    "SHA-256"
                ).digest(photo.readBytes()).joinToString("") {
                    "%02x".format(it)
                }
            )
        val workout =
            WorkoutEntity(
                journalDate = "2026-11-13",
                zoneId = "Europe/Helsinki",
                createdAt = time,
                modifiedAt = time
            )
        val item =
            SessionItem(
                workoutId = workout.id,
                exerciseId = exercise.id,
                equipmentId = exercise.equipmentId,
                name = exercise.name,
                category = exercise.category,
                basis = "kg"
            )
        val set =
            SessionSet(
                itemId = item.id,
                rawLoad = "27,5",
                rawReps = "10",
                load = "27.5",
                reps = 10,
                completedAt = time
            )
        val check =
            CheckIn(
                workoutId = workout.id,
                phase = "BEFORE",
                observedAt = time,
                zoneId = "Europe/Helsinki"
            )
        val routine = Routine(title = "Routine")
        return JournalSnapshot(
            exercises = listOf(
                exercise
            ),
            photos = listOf(
                asset
            ),
            routines = listOf(
                routine
            ),
            routineItems = listOf(
                RoutineItem(
                    routineId = routine.id,
                    exerciseId = exercise.id,
                    sets = listOf(SetTarget("27.5", 10))
                )
            ),
            workouts = listOf(
                workout
            ),
            items = listOf(
                item
            ),
            sets = listOf(
                set,
                SessionSet(itemId = item.id, position = 1, rawLoad = "27,")
            ),
            checkIns = listOf(
                check
            ),
            observations = listOf(BodyObservation(check.id, "left_knee", pain = 0)),
            drafts = listOf(
                EditorDraft(
                    "exercise:${exercise.id}",
                    "exercise",
                    "{\"raw\":\"unfinished\"}",
                    time
                ),
                EditorDraft("duration:${item.id}", "duration", "", time)
            ),
            healthStates = listOf(
                HealthShareState(workout.id, clientVersion = 15)
            ),
            preferences = Preferences(
                name = "Emma",
                remindersEnabled = true,
                reminderDays = listOf(1, 4)
            )
        ) to
            photo
    }
    private fun archive(data: JournalSnapshot, photo: File): ByteArray = ByteArrayOutputStream().also {
        BackupArchive.write(data, { photo }, it, time, "1.0")
    }.toByteArray()

    @Test fun roundTripRetainsPendingDeletionOfAnAlreadyDeletedWorkout() {
        val (data, photo) = fixture()
        val state = HealthShareState(newId(), clientVersion = 5, acknowledgedRevision = 3)
        val deletion = HealthOutbox(state.workoutId, state.clientRecordId, 5, "DELETE")
        val source = data.copy(healthStates = data.healthStates + state, outbox = listOf(deletion))
        val restored = BackupArchive.read(
            ByteArrayInputStream(archive(source, photo)),
            temporary.newFolder()
        ).data
        assertEquals(listOf(deletion), restored.outbox)
    }

    @Test fun roundTripPreservesWholeJournalAndActualAssetBytes() {
        val (fixture, photo) = fixture()
        val finish = FinishDraft(end = time, note = "Unfinished workout note", unknownTimes = true)
        val draft = EditorDraft("finish:${fixture.workouts.single().id}", "finish", Json.encodeToString(finish), time)
        val data = fixture.copy(drafts = fixture.drafts + draft)
        val staged = BackupArchive.read(
            ByteArrayInputStream(archive(data, photo)),
            temporary.newFolder()
        )
        assertEquals(data.copy(photos = staged.data.photos), staged.data)
        assertArrayEquals(
            photo.readBytes(),
            File(staged.directory, staged.data.photos.single().path).readBytes()
        )
        assertEquals(0, staged.data.observations.single().pain)
        assertNull(staged.data.observations.single().stiffness)
        assertEquals("27,", staged.data.sets.last().rawLoad)
    }

    @Test fun finishDraftsWithInvalidTimestampsCannotBeBackedUp() {
        val (data, photo) = fixture()
        val id = "finish:${data.workouts.single().id}"
        listOf(
            FinishDraft(end = "not an instant", note = ""),
            FinishDraft(end = time, note = "", fixedStart = "not an instant")
        ).forEach { finish ->
            val draft = EditorDraft(id, "finish", Json.encodeToString(finish), time)
            assertThrows(DateTimeException::class.java) {
                archive(data.copy(drafts = data.drafts + draft), photo)
            }
        }
    }

    @Test fun finishDraftIdsCannotUseDifferentDraftKinds() {
        val (data, photo) = fixture()
        val id = "finish:${data.workouts.single().id}"
        val payload = Json.encodeToString(FinishDraft(end = "not an instant", note = "Unfinished workout note"))
        val disguised = EditorDraft(id, "exercise", payload, time)
        assertThrows(IllegalArgumentException::class.java) {
            archive(data.copy(drafts = data.drafts + disguised), photo)
        }
    }

    @Test fun workoutTimestampsMustFitAndroidMillisecondDates() {
        val (data, photo) = fixture()
        val workout = data.workouts.single()
        listOf(
            workout.copy(start = Instant.MIN.toString()),
            workout.copy(end = Instant.MAX.toString())
        ).forEach { invalid ->
            assertThrows(ArithmeticException::class.java) {
                archive(data.copy(workouts = listOf(invalid)), photo)
            }
        }
    }

    @Test fun damagedMissingAndUndeclaredEntriesAreRejected() {
        val (data, photo) = fixture()
        val original = archive(data, photo)
        val entries = unzip(original)
        val asset = entries.keys.first { it.startsWith("photos/") }
        listOf(
            entries + (asset to byteArrayOf(9)),
            entries - asset,
            entries + ("../escape" to byteArrayOf(1)),
            entries + ("unlisted.json" to byteArrayOf(1))
        ).forEach { changed ->
            assertThrows(Exception::class.java) {
                BackupArchive.read(ByteArrayInputStream(zip(changed)), temporary.newFolder())
            }
        }
    }

    @Test fun unsupportedVersionAndInsufficientStorageAreRejected() {
        val (data, photo) = fixture()
        val original = archive(data, photo)
        val entries = unzip(original)
        val manifest = Json.parseToJsonElement(
            entries.getValue("manifest.json").decodeToString()
        ).jsonObject
        val newer = JsonObject(manifest + ("formatVersion" to JsonPrimitive(999)))
        assertThrows(Exception::class.java) {
            BackupArchive.read(
                ByteArrayInputStream(
                    zip(entries + ("manifest.json" to newer.toString().toByteArray()))
                ),
                temporary.newFolder()
            )
        }
        assertThrows(Exception::class.java) {
            BackupArchive.read(ByteArrayInputStream(original), temporary.newFolder(), 2)
        }
    }

    @Test fun declaredTraversalPathsAreRejectedBeforeWriting() {
        val (data, photo) = fixture()
        val entries = unzip(archive(data, photo))
        val asset = entries.keys.first { it.startsWith("photos/") }
        val manifest = Json.parseToJsonElement(entries.getValue("manifest.json").decodeToString()).jsonObject
        val declared = manifest.getValue("entries").jsonObject
        listOf("../escape", "photos/../../escape", "/escape", "photos\\..\\escape").forEach { unsafe ->
            val changed = JsonObject(manifest + ("entries" to JsonObject(declared - asset + (unsafe to declared.getValue(asset)))))
            val archive = entries - asset + (unsafe to entries.getValue(asset)) +
                ("manifest.json" to changed.toString().toByteArray())
            val directory = temporary.newFolder()
            val error = assertThrows(IllegalArgumentException::class.java) {
                BackupArchive.read(ByteArrayInputStream(zip(archive)), directory)
            }
            assertEquals("Unsafe or unknown backup path", error.message)
            assertEquals(0, directory.list()!!.size)
        }
    }

    @Test fun invalidRelationshipsAndCompletedNumbersCannotBeBackedUp() {
        val (data, photo) = fixture()
        assertThrows(Exception::class.java) {
            archive(data.copy(items = data.items.map { it.copy(workoutId = newId()) }), photo)
        }
        assertThrows(Exception::class.java) {
            archive(
                data.copy(
                    sets = data.sets.map {
                        if (it.completedAt !=
                            null
                        ) {
                            it.copy(load = "-2")
                        } else {
                            it
                        }
                    }
                ),
                photo
            )
        }
        assertThrows(Exception::class.java) {
            archive(
                data.copy(
                    workouts =
                        data.workouts + data.workouts.single().copy(id = newId())
                ),
                photo
            )
        }
    }
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { stream ->
            while (true) {
                val entry =
                    stream.nextEntry ?: break
                put(entry.name, stream.readBytes())
            }
        }
    }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()
}
