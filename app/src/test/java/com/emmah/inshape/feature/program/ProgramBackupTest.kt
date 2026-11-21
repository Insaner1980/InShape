package com.emmah.inshape.feature.program

import com.emmah.inshape.data.AssistantMemory
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.ProgramEnrollment
import com.emmah.inshape.data.ProgramExerciseLink
import com.emmah.inshape.data.newId
import com.emmah.inshape.transfer.BackupArchive
import com.emmah.inshape.transfer.BackupEntry
import com.emmah.inshape.transfer.BackupManifest
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramBackupTest {
    @Test fun acceptsARealVersionOneArchiveWithNoProgramData() {
        val exerciseId = newId()
        val equipmentId = newId()
        val payload = mapOf(
            "journal.json" to """{"workouts":[],"items":[],"sets":[],"checkIns":[],"observations":[],"drafts":[],"healthStates":[],"pendingDeletions":[]}""",
            "library.json" to """{"exercises":[{"id":"$exerciseId","name":"Original","equipmentId":"$equipmentId"}],"photos":[],"routines":[],"routineItems":[]}""",
            "preferences.json" to "{}"
        ).mapValues { it.value.encodeToByteArray() }
        val counts = mapOf(
            "workouts" to 0, "items" to 0, "sets" to 0, "checkIns" to 0,
            "observations" to 0, "drafts" to 0, "healthStates" to 0, "pendingDeletions" to 0,
            "exercises" to 1, "photos" to 0, "routines" to 0, "routineItems" to 0
        )
        val entries = payload.mapValues { (_, bytes) ->
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            BackupEntry(bytes.size.toLong(), sha)
        }
        val manifest = BackupManifest(1, "2026-11-20T12:00:00Z", "1.0", counts, entries)
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            val manifestAndPayload = mapOf("manifest.json" to Json.encodeToString(manifest).encodeToByteArray()) + payload
            manifestAndPayload.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        val storage = Files.createTempDirectory("inshape-v1-backup-test").toFile()
        try {
            val restored = BackupArchive.read(bytes.toByteArray().inputStream(), storage) {}
            assertEquals(1, restored.manifest.formatVersion)
            assertEquals(equipmentId, restored.data.exercises.single().equipmentId)
            assertNull(restored.data.programEnrollment)
            assertEquals(emptyList<AssistantMemory>(), restored.data.assistantMemories)
        } finally {
            storage.deleteRecursively()
        }
    }

    @Test fun versionTwoRoundTripsAcceptedMachineLinksAndConfirmedMemories() {
        val exercise = Exercise(name = "Personal machine")
        val memory = AssistantMemory(newId(), "reminder", exercise.id, "Seat 4", newId())
        val original = JournalSnapshot(
            exercises = listOf(exercise),
            programEnrollment = ProgramEnrollment(programId = "rauhallinen-aloitus", catalogVersion = 1, baselineDate = "2026-11-20"),
            programLinks = listOf(ProgramExerciseLink("leg_press", exercise.id)),
            assistantMemories = listOf(memory)
        )
        val storage = Files.createTempDirectory("inshape-backup-test").toFile()
        try {
            val bytes = ByteArrayOutputStream()
            BackupArchive.write(original, { error("No fixture photos") }, bytes, "2026-11-20T12:00:00Z", "test")
            val restored = BackupArchive.read(bytes.toByteArray().inputStream(), storage) {}
            assertEquals(2, restored.manifest.formatVersion)
            assertEquals(original.programEnrollment, restored.data.programEnrollment)
            assertEquals(original.programLinks, restored.data.programLinks)
            assertEquals(listOf(memory), restored.data.assistantMemories)
        } finally {
            storage.deleteRecursively()
        }
    }
}
