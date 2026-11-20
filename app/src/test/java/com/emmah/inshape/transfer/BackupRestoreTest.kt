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
import com.emmah.inshape.data.SavedLink
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
import java.io.IOException
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
                instructions = "Keep shoulders down",
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

    private fun readArchive(input: ByteArrayInputStream, directory: File) = BackupArchive.read(input, directory) {}

    @Test fun backupCreationTimeMustFitInEpochMilliseconds() {
        val (data, photo) = fixture()
        val latest = Instant.ofEpochMilli(Long.MAX_VALUE)
        val output = ByteArrayOutputStream()
        assertThrows(ArithmeticException::class.java) {
            BackupArchive.write(data, { photo }, output, latest.plusMillis(1).toString(), "1.0")
        }
        assertEquals(0, output.size())

        BackupArchive.write(data, { photo }, output, latest.toString(), "1.0")
        val staged = readArchive(ByteArrayInputStream(output.toByteArray()), temporary.newFolder())
        assertEquals(latest.toString(), staged.manifest.createdAt)
    }

    @Test fun roundTripRetainsPendingDeletionOfAnAlreadyDeletedWorkout() {
        val (data, photo) = fixture()
        val state = HealthShareState(newId(), clientVersion = 5, acknowledgedRevision = 3)
        val deletion = HealthOutbox(state.workoutId, state.clientRecordId, 5, "DELETE")
        val source = data.copy(healthStates = data.healthStates + state, outbox = listOf(deletion))
        val restored = readArchive(
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
        val staged = readArchive(
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
                readArchive(ByteArrayInputStream(zip(changed)), temporary.newFolder())
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
            readArchive(
                ByteArrayInputStream(
                    zip(entries + ("manifest.json" to newer.toString().toByteArray()))
                ),
                temporary.newFolder()
            )
        }
        val staging = temporary.newFolder()
        val required = entries.filterKeys { it != "manifest.json" }.values.sumOf { it.size.toLong() } + 10L * 1024 * 1024
        assertThrows(IOException::class.java) {
            BackupArchive.read(ByteArrayInputStream(original), staging) { bytes ->
                assertEquals(required, bytes)
                throw IOException("Insufficient staging space")
            }
        }
        assertEquals(emptyList<String>(), staging.list()?.toList())
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
                readArchive(ByteArrayInputStream(zip(archive)), directory)
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

    @Test fun roundTripPreservesOptionalWorkoutAndLibraryValues() {
        val (base, photo) = fixture()
        val alternative = Exercise(name = "Seated row")
        val exercise = base.exercises.single().copy(
            alternatives = listOf(alternative.id),
            links = listOf(SavedLink("Guide", "https://example.com/exercises/row"))
        )
        val item = base.items.single()
        val replacement = item.copy(
            id = newId(), exerciseId = alternative.id, equipmentId = alternative.equipmentId,
            position = 1, alternativeOf = item.id, mode = "DURATION",
            targetDurationSeconds = 45, durationSeconds = 60, quickCompletedAt = time
        )
        val end = "2026-11-13T08:01:00Z"
        val workout = base.workouts.single().copy(
            state = "COMPLETED", activeKey = null, start = time, end = end,
            startOffset = "+02:00", endOffset = "+02:00", durationSeconds = 60,
            durationSource = "INTERVAL", timingConfirmed = true, currentItemId = replacement.id
        )
        val checkIn = base.checkIns.single().copy(phase = "AFTER", effort = 10, energy = "GOOD")
        val set = base.sets.first()
        val state = base.healthStates.single()
        val data = base.copy(
            exercises = listOf(exercise, alternative),
            routines = base.routines.map { it.copy(durationMinutes = 30) },
            routineItems = base.routineItems.map {
                it.copy(sets = listOf(SetTarget(), SetTarget(reps = 8), SetTarget(load = "20")))
            },
            workouts = listOf(workout),
            items = listOf(item, replacement),
            sets = listOf(set.copy(load = null), set.copy(id = newId(), position = 1, reps = null)),
            checkIns = listOf(checkIn),
            observations = base.observations.map { it.copy(stiffness = 10) },
            preferences = base.preferences.copy(weeklyGoal = 3, lastBackupAt = time),
            outbox = listOf(HealthOutbox(state.workoutId, state.clientRecordId, 15, "UPSERT"))
        )
        val staged = readArchive(ByteArrayInputStream(archive(data, photo)), temporary.newFolder())
        assertEquals(
            data.copy(
                photos = base.photos.map { it.copy(path = "photos/${it.id}.png") },
                preferences = data.preferences.copy(lastBackupAt = null),
                outbox = emptyList()
            ),
            staged.data
        )
    }

    @Test fun restoresOlderJournalWithoutPendingDeletionField() {
        val (data, photo) = fixture()
        val entries = unzip(archive(data, photo))
        val journal = Json.parseToJsonElement(entries.getValue("journal.json").decodeToString()).jsonObject
        val olderJournal = JsonObject(journal - "pendingDeletions").toString().toByteArray()
        val manifest = Json.decodeFromString<BackupManifest>(entries.getValue("manifest.json").decodeToString())
        val updatedManifest = manifest.copy(
            entries = manifest.entries + ("journal.json" to BackupEntry(olderJournal.size.toLong(), checksum(olderJournal)))
        )
        val olderArchive = entries + ("journal.json" to olderJournal) +
            ("manifest.json" to Json.encodeToString(updatedManifest).toByteArray())
        val restored = readArchive(ByteArrayInputStream(zip(olderArchive)), temporary.newFolder()).data
        assertEquals(data.copy(photos = restored.photos), restored)
    }

    @Test fun detectsChangedAndMissingSourcePhotosBeforeExport() {
        val (data, photo) = fixture()
        photo.writeBytes(byteArrayOf(4, 3, 2, 1))
        val changed = assertThrows(IllegalArgumentException::class.java) { archive(data, photo) }
        assertEquals("A journal photo failed its checksum", changed.message)
        val missing = assertThrows(IllegalArgumentException::class.java) {
            archive(data, File(photo.parentFile, "missing.png"))
        }
        assertEquals("A journal photo is missing or changed", missing.message)
    }

    @Test fun rejectsRestoreIntoAnOccupiedStagingDirectory() {
        val (data, photo) = fixture()
        val directory = temporary.newFolder()
        val existing = File(directory, "existing.txt").apply { writeText("Keep this file") }
        val error = assertThrows(IllegalArgumentException::class.java) {
            readArchive(ByteArrayInputStream(archive(data, photo)), directory)
        }
        assertEquals("Restore staging must be empty", error.message)
        assertEquals("Keep this file", existing.readText())
    }

    @Test fun rejectsInvalidExerciseLibraryRecords() {
        val (data, _) = fixture()
        val exercise = data.exercises.single()
        val other = Exercise(name = "Other exercise")
        val photo = data.photos.single()
        val routine = data.routines.single()
        val item = data.routineItems.single()
        listOf(
            "duplicate exercise" to data.copy(exercises = listOf(exercise, exercise)),
            "incomplete equipment identifier" to data.copy(exercises = listOf(exercise.copy(equipmentId = "1-2-3-4-5"))),
            "blank exercise name" to data.copy(exercises = listOf(exercise.copy(name = " "))),
            "duplicate alternative" to data.copy(exercises = listOf(exercise.copy(alternatives = listOf(other.id, other.id)), other)),
            "its own alternative" to data.copy(exercises = listOf(exercise.copy(alternatives = listOf(exercise.id)))),
            "missing alternative" to data.copy(exercises = listOf(exercise.copy(alternatives = listOf(other.id)))),
            "unsupported link scheme" to
                data.copy(exercises = listOf(exercise.copy(links = listOf(SavedLink("Guide", "ftp://example.com/guide"))))),
            "link without a host" to data.copy(exercises = listOf(exercise.copy(links = listOf(SavedLink("Guide", "https:/guide"))))),
            "relative link" to data.copy(exercises = listOf(exercise.copy(links = listOf(SavedLink("Guide", "guide.html"))))),
            "photo without an exercise" to data.copy(photos = listOf(photo.copy(exerciseId = other.id))),
            "negative photo position" to data.copy(photos = listOf(photo.copy(position = -1))),
            "empty photo" to data.copy(photos = listOf(photo.copy(byteLength = 0))),
            "oversized photo" to data.copy(photos = listOf(photo.copy(byteLength = MAX_PHOTO_BYTES + 1))),
            "missing photo checksum" to data.copy(photos = listOf(photo.copy(checksum = ""))),
            "blank routine title" to data.copy(routines = listOf(routine.copy(title = " "))),
            "zero routine duration" to data.copy(routines = listOf(routine.copy(durationMinutes = 0))),
            "zero routine revision" to data.copy(routines = listOf(routine.copy(revision = 0))),
            "missing routine" to data.copy(routineItems = listOf(item.copy(routineId = newId()))),
            "missing routine exercise" to data.copy(routineItems = listOf(item.copy(exerciseId = other.id))),
            "negative routine item position" to data.copy(routineItems = listOf(item.copy(position = -1))),
            "unknown routine item mode" to data.copy(routineItems = listOf(item.copy(mode = "")))
        ).forEach { (description, invalid) ->
            assertThrows(description, IllegalArgumentException::class.java) { validateBackupData(invalid) }
        }
    }

    @Test fun rejectsInconsistentWorkoutAndSetRecords() {
        val (data, _) = fixture()
        val workout = data.workouts.single()
        val past = workout.copy(id = newId(), state = "DRAFT", activeKey = null, kind = "PAST")
        val item = data.items.single()
        val draft = data.sets.last()
        listOf(
            "end before start" to data.copy(workouts = listOf(workout.copy(start = "2026-11-13T08:15:00Z", end = time))),
            "unknown workout state" to data.copy(workouts = listOf(workout.copy(state = ""))),
            "unknown workout kind" to data.copy(workouts = listOf(workout.copy(kind = ""))),
            "active workout without its key" to data.copy(workouts = listOf(workout.copy(activeKey = null))),
            "invalid inactive workout key" to data.copy(workouts = listOf(past.copy(activeKey = 2))),
            "confirmed timing without a start" to data.copy(workouts = listOf(workout.copy(end = time, timingConfirmed = true))),
            "confirmed timing without an end" to data.copy(workouts = listOf(workout.copy(start = time, timingConfirmed = true))),
            "zero workout revision" to data.copy(workouts = listOf(workout.copy(revision = 0))),
            "current item from another workout" to data.copy(workouts = listOf(workout, past.copy(currentItemId = item.id))),
            "unknown duration source" to data.copy(workouts = listOf(workout.copy(durationSource = ""))),
            "duplicate session item" to data.copy(items = listOf(item, item.copy(position = 1))),
            "session item without an exercise" to data.copy(items = listOf(item.copy(exerciseId = newId()))),
            "negative session item position" to data.copy(items = listOf(item.copy(position = -1))),
            "unknown exercise disposition" to data.copy(items = listOf(item.copy(disposition = ""))),
            "zero performed duration" to data.copy(items = listOf(item.copy(durationSeconds = 0))),
            "unknown session item mode" to data.copy(items = listOf(item.copy(mode = ""))),
            "exercise is its own replacement" to data.copy(items = listOf(item.copy(alternativeOf = item.id))),
            "alternative in a different workout" to data.copy(
                workouts = listOf(workout, past),
                items = listOf(item, item.copy(id = newId(), workoutId = past.id, alternativeOf = item.id))
            ),
            "set without an item" to data.copy(sets = listOf(draft.copy(itemId = newId()))),
            "negative set position" to data.copy(sets = listOf(draft.copy(position = -1))),
            "draft set with performed load" to data.copy(sets = listOf(draft.copy(load = "10"))),
            "draft set with performed reps" to data.copy(sets = listOf(draft.copy(reps = 1)))
        ).forEach { (description, invalid) ->
            assertThrows(description, IllegalArgumentException::class.java) { validateBackupData(invalid) }
        }
    }

    @Test fun rejectsInconsistentCheckInSettingsAndDrafts() {
        val (data, _) = fixture()
        val check = data.checkIns.single()
        val observation = data.observations.single()
        val draft = data.drafts.first()
        val duration = data.drafts.last()
        listOf(
            "duplicate check-in phase" to data.copy(checkIns = listOf(check, check.copy(id = newId()))),
            "check-in without its workout" to data.copy(checkIns = listOf(check.copy(workoutId = newId()))),
            "unknown check-in phase" to data.copy(checkIns = listOf(check.copy(phase = ""))),
            "unknown energy" to data.copy(checkIns = listOf(check.copy(energy = ""))),
            "effort before workout" to data.copy(checkIns = listOf(check.copy(effort = 5))),
            "effort above scale" to data.copy(checkIns = listOf(check.copy(phase = "AFTER", effort = 11))),
            "negative effort" to data.copy(checkIns = listOf(check.copy(phase = "AFTER", effort = -1))),
            "duplicate body region" to data.copy(observations = listOf(observation, observation.copy(pain = 5))),
            "observation without its check-in" to data.copy(observations = listOf(observation.copy(checkInId = newId()))),
            "unknown body region" to data.copy(observations = listOf(observation.copy(region = "knee"))),
            "negative pain" to data.copy(observations = listOf(observation.copy(pain = -1))),
            "pain above scale" to data.copy(observations = listOf(observation.copy(pain = 11))),
            "negative stiffness" to data.copy(observations = listOf(observation.copy(stiffness = -1))),
            "stiffness above scale" to data.copy(observations = listOf(observation.copy(stiffness = 11))),
            "invalid preferences identity" to data.copy(preferences = data.preferences.copy(id = 2)),
            "zero weekly goal" to data.copy(preferences = data.preferences.copy(weeklyGoal = 0)),
            "day below calendar range" to data.copy(preferences = data.preferences.copy(reminderDays = listOf(0))),
            "day above calendar range" to data.copy(preferences = data.preferences.copy(reminderDays = listOf(8))),
            "duplicate reminder days" to data.copy(preferences = data.preferences.copy(reminderDays = listOf(1, 1))),
            "duplicate draft" to data.copy(drafts = listOf(draft, draft)),
            "blank draft identity" to data.copy(drafts = listOf(draft.copy(id = " "))),
            "unknown draft kind" to data.copy(drafts = listOf(draft.copy(kind = ""))),
            "unsupported draft version" to data.copy(drafts = listOf(draft.copy(version = 2))),
            "oversized duration draft" to data.copy(drafts = listOf(duration.copy(payload = "1".repeat(65)))),
            "oversized editor draft" to data.copy(drafts = listOf(draft.copy(payload = " ".repeat(1_048_577))))
        ).forEach { (description, invalid) ->
            assertThrows(description, IllegalArgumentException::class.java) { validateBackupData(invalid) }
        }
    }

    @Test fun rejectsInconsistentHealthSharingRecords() {
        val (data, _) = fixture()
        val state = data.healthStates.single()
        val deletion = HealthOutbox(state.workoutId, state.clientRecordId, state.clientVersion, "DELETE")
        listOf(
            "unrelated health record identity" to data.copy(healthStates = listOf(state.copy(clientRecordId = "other"))),
            "zero client version" to data.copy(healthStates = listOf(state.copy(clientVersion = 0))),
            "negative acknowledged revision" to data.copy(healthStates = listOf(state.copy(acknowledgedRevision = -1))),
            "unrelated outbox identity" to data.copy(outbox = listOf(deletion.copy(clientRecordId = "other"))),
            "zero outbox revision" to data.copy(outbox = listOf(deletion.copy(revision = 0))),
            "unknown health operation" to data.copy(outbox = listOf(deletion.copy(operation = "")))
        ).forEach { (description, invalid) ->
            assertThrows(description, IllegalArgumentException::class.java) { validateBackupData(invalid) }
        }
    }

    @Test fun backupUsesPortableExtensionsForSupportedImageFormats() {
        mapOf(
            "image/jpeg" to "jpg",
            "image/webp" to "webp",
            "image/heif" to "heic",
            "image/heic" to "heic",
            "image/gif" to "gif"
        ).forEach { (mediaType, extension) ->
            assertEquals(mediaType, extension, extensionFor(mediaType))
        }
        assertThrows(IllegalStateException::class.java) { extensionFor("application/octet-stream") }
    }

    private fun checksum(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

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
