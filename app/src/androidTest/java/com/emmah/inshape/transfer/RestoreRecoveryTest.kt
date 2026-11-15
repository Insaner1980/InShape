package com.emmah.inshape.transfer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.AppContainer
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.HealthOutbox
import com.emmah.inshape.data.HealthShareState
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.newId
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val token = newId()
    private val sourceName = "backup-source-$token.db"
    private val targetName = "backup-target-$token.db"
    private val clock = Clock.fixed(
        Instant.parse("2026-11-13T10:00:00Z"),
        ZoneId.of("Europe/Helsinki")
    )
    private val sourceHolder = lazy { AppContainer(context, clock, sourceName) }
    private val targetHolder = lazy { AppContainer(context, clock, targetName) }
    private val source by sourceHolder
    private val target by targetHolder
    private val scratch by lazy { File(context.cacheDir, "backup-test-$token").apply { mkdirs() } }

    @After fun cleanup() {
        if (sourceHolder.isInitialized()) {
            source.close()
            context.deleteDatabase(sourceName)
            source.storageRoot.deleteRecursively()
        }
        if (targetHolder.isInitialized()) {
            target.close()
            context.deleteDatabase(targetName)
            target.storageRoot.deleteRecursively()
        }
        scratch.deleteRecursively()
    }

    private suspend fun prepare(): File {
        source.storageReady.await()
        target.storageReady.await()
        val exercise = Exercise(name = "Incoming", notes = "Åäö\nSecond line")
        source.repository.saveExercise(exercise)
        val image = File(scratch, "picked.png")
        val bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        source.photos.importPhoto(exercise.id, Uri.fromFile(image))
        source.photos.importPhoto(exercise.id, Uri.fromFile(image))
        val id = source.repository.startWorkout(StartSource.Empty)
        val item = source.repository.addExercise(id, exercise.id)
        val set = source.repository.addSet(item)
        source.repository.saveSetDraft(set, "27,5", "10")
        source.repository.setSetCompleted(set, true)
        val draft = source.repository.addSet(item)
        source.repository.saveSetDraft(draft, "27,", "")
        val check =
            CheckIn(
                workoutId = id,
                phase = "BEFORE",
                observedAt = clock.instant().toString(),
                zoneId = clock.zone.id
            )
        source.repository.saveCheckIn(
            check,
            listOf(BodyObservation(region = "left_knee", pain = 0))
        )
        source.repository.updatePreferences(
            Preferences(
                name = "Emma",
                remindersEnabled = true,
                reminderDays = listOf(1),
                shareWorkouts = true
            )
        )
        source.database.journal().put(
            HealthShareState(id, clientVersion = 20, platformId = "old-device-record")
        )
        val deleted = HealthShareState(newId(), clientVersion = 7, acknowledgedRevision = 5)
        source.database.journal().put(deleted)
        source.database.journal().put(
            HealthOutbox(deleted.workoutId, deleted.clientRecordId, 7, "DELETE")
        )
        target.repository.saveExercise(Exercise(name = "Current"))
        val tombstoneId = newId()
        target.database.journal().put(HealthShareState(tombstoneId, acknowledgedRevision = 1))
        target.database.journal().put(
            HealthOutbox(tombstoneId, "inshape:workout:$tombstoneId", 2, "DELETE")
        )
        val archive = File(scratch, "journal.inshape-backup")
        source.backups.createBackup(Uri.fromFile(archive))
        image.delete()
        return archive
    }

    @Test fun restoreToDifferentStoragePreservesPhotosDraftsAndHoldsWithoutTransferringBackupDate() = runBlocking {
        val archive = prepare()
        val staged = target.backups.validateBackup(Uri.fromFile(archive))
        File(context.filesDir, "verification/cross-install.inshape-backup").also {
            it.parentFile!!.mkdirs()
            archive.copyTo(it, overwrite = true)
        }
        target.backups.replaceJournal(staged, staged.identity)
        val actual = target.repository.snapshot()
        assertEquals("Incoming", actual.exercises.single().name)
        assertNotNull(actual.active)
        assertEquals("27,", actual.sets.last().rawLoad)
        assertEquals(0, actual.observations.single().pain)
        assertNull(actual.observations.single().stiffness)
        assertFalse(actual.preferences.shareWorkouts)
        assertTrue(actual.preferences.remindersHeld)
        assertNull(actual.preferences.lastBackupAt)
        assertEquals("DELETE", actual.outbox.single().operation)
        assertEquals(
            1,
            actual.healthStates.count {
                it.status == "REMOVAL_HELD" &&
                    it.restoredHold
            }
        )
        assertTrue(
            actual.healthStates.first {
                it.workoutId == actual.active!!.row.id
            }.restoredHold
        )
        assertNull(
            actual.healthStates.first {
                it.workoutId == actual.active!!.row.id
            }.platformId
        )
        actual.photos.forEach {
            assertNotNull(target.photos.thumbnail(it))
            assertTrue(
                target.photos
                    .file(it)
                    .canonicalPath
                    .startsWith(target.storageRoot.canonicalPath + File.separator)
            )
        }
        val orphan = File(target.photos.root, "abandoned.png").apply {
            writeBytes(byteArrayOf(1))
        }
        target.backups.recoverOwnedStaging()
        assertFalse(orphan.exists())
        actual.photos.forEach { assertTrue(target.photos.file(it).exists()) }
    }

    @Test fun archiveFromAnotherInstallationRestoresOriginalPhotoBytesAndDrafts() = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString(
            "crossInstallationBackupPath"
        )
        val archive = if (path == null) prepare() else File(path)
        target.storageReady.await()
        if (path != null) {
            assertTrue(
                target.repository
                    .snapshot()
                    .exercises
                    .isEmpty()
            )
        }
        val staged = target.backups.validateBackup(Uri.fromFile(archive))
        target.backups.replaceJournal(staged, staged.identity)
        val actual = target.repository.snapshot()
        assertEquals("Incoming", actual.exercises.single().name)
        assertEquals(2, actual.photos.size)
        actual.photos.forEach {
            assertNotNull(target.photos.thumbnail(it))
            assertTrue(
                target.photos
                    .file(it)
                    .canonicalPath
                    .startsWith(target.storageRoot.canonicalPath + File.separator)
            )
        }
        assertEquals("27.5", actual.sets.single { it.completedAt != null }.load)
        assertEquals("27,", actual.sets.single { it.completedAt == null }.rawLoad)
        assertEquals(0, actual.observations.single().pain)
        assertNull(actual.observations.single().stiffness)
        assertFalse(actual.preferences.shareWorkouts)
        assertTrue(actual.preferences.remindersHeld)
        if (path != null) assertTrue(actual.outbox.isEmpty())
        assertEquals(
            1,
            actual.healthStates.count {
                it.status == "REMOVAL_HELD" &&
                    it.restoredHold
            }
        )
    }

    @Test fun transactionFailureLeavesOriginalJournalAndAllowsFreshValidationRetry() = runBlocking {
        val archive = prepare()
        val before = target.repository.snapshot()
        target.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_restore BEFORE INSERT ON Exercise WHEN NEW.name='Incoming' BEGIN " +
                "SELECT RAISE(ABORT, 'Injected storage failure'); END"
        )
        val staged = target.backups.validateBackup(Uri.fromFile(archive))
        try {
            target.backups.replaceJournal(staged, staged.identity)
            fail("Expected transaction failure")
        } catch (
            _: android.database.sqlite.SQLiteException
        ) {
        }
        assertEquals(before, target.repository.snapshot())
        assertFalse(staged.staged.directory.exists())
        target.database.openHelper.writableDatabase
            .execSQL("DROP TRIGGER fail_restore")
        val retry = target.backups.validateBackup(Uri.fromFile(archive))
        target.backups.replaceJournal(retry, retry.identity)
        assertEquals(
            "Incoming",
            target.repository
                .snapshot()
                .exercises
                .single()
                .name
        )
        target.repository
            .snapshot()
            .photos
            .forEach { assertNotNull(target.photos.thumbnail(it)) }
    }
}
