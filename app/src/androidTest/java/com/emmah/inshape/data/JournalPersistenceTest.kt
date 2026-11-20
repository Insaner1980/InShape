package com.emmah.inshape.data

import com.emmah.inshape.testContext
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalPersistenceTest {
    @Test fun committedSessionAndDraftSurviveFileDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-${newId()}.db"
        var db = Room.databaseBuilder(context, JournalDatabase::class.java, name).build()
        try {
            var repo = JournalRepository(db, Clock.systemUTC(), testContext())
            val exercise = Exercise(name = "Machine", settings = listOf(Setting("Seat", "4")))
            repo.saveExercise(exercise)
            val ids =
                coroutineScope {
                    (1..4).map {
                        async(Dispatchers.IO) { repo.startWorkout(StartSource.Empty) }
                    }.awaitAll()
                }
            assertEquals(1, ids.distinct().size)
            val item = repo.addExercise(ids.first(), exercise.id)
            val set = repo.addSet(item)
            repo.saveSetDraft(set, "27,5", "10")
            repo.setSetCompleted(set, true)
            val unfinished = repo.addSet(item)
            repo.saveSetDraft(unfinished, "27.", "")
            repo.saveEditorDraft(
                EditorDraft(
                    "exercise:new",
                    "exercise",
                    "unfinished form",
                    "2026-11-13T08:00:00Z"
                )
            )
            db.close()
            db = Room.databaseBuilder(context, JournalDatabase::class.java, name).build()
            repo = JournalRepository(db, Clock.systemUTC(), testContext())
            val snapshot = repo.snapshot()
            assertEquals(ids.first(), snapshot.active!!.row.id)
            assertEquals("27.5", snapshot.sets.first { it.id == set }.load)
            assertEquals("27.", snapshot.sets.first { it.id == unfinished }.rawLoad)
            assertNull(snapshot.sets.first { it.id == unfinished }.completedAt)
            assertEquals("unfinished form", snapshot.drafts.single().payload)
            assertEquals(item, snapshot.active!!.row.currentItemId)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun databaseRejectsSecondActiveButAllowsPastDraft() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val first =
                WorkoutEntity(
                    journalDate = "2026-11-13",
                    zoneId = "Europe/Helsinki",
                    createdAt = "2026-11-13T08:00:00Z",
                    modifiedAt = "2026-11-13T08:00:00Z"
                )
            db.journal().insertWorkout(first)
            try {
                db.journal().insertWorkout(first.copy(id = newId()))
                fail("A second active session was accepted")
            } catch (
                _: android.database.sqlite.SQLiteConstraintException
            ) {
            }
            db.journal().insertWorkout(
                first.copy(id = newId(), kind = "PAST", state = "DRAFT", activeKey = null)
            )
            assertEquals(2, db.journal().workouts().size)
        } finally {
            db.close()
        }
    }
}
