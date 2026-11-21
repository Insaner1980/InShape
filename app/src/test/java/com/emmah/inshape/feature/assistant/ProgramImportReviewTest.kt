package com.emmah.inshape.feature.assistant

import com.emmah.inshape.testContext
import com.emmah.inshape.data.AssistantDraft
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.data.createTestDatabase
import com.emmah.inshape.data.newId
import java.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgramImportReviewTest {
    @Test fun stagedImportKeepsItsOrderedGroupRepRangesAndRestAndCommitsOnce() = runBlocking {
        val db = createTestDatabase()
        try {
            val repo = JournalRepository(db, Clock.systemUTC(), testContext())
            val text = """
                {"days":[{"title":"Day A","rows":[
                  {"name":"Cable row","basis":"kg","sets":"2","rest":"90","group":"g1","groupType":"Superset","rounds":"2",
                   "roundTargets":[{"load":"10,5","min":"8","max":"10"},{"load":"12","min":"6","max":"8"}]},
                  {"name":"Pallof press","basis":"kg","sets":"2","min":"10","max":"12","perSide":true,
                   "group":"g1","groupType":"Superset","rounds":"2"}
                ]}]}
            """.trimIndent()
            val reviewed = parseProgramImport(Json.parseToJsonElement(text).jsonObject).map { day ->
                day.copy(rows = day.rows.map { it.copy(selection = CREATE_IMPORT_EXERCISE) })
            }
            val pending = AssistantDraft("import:program", newId(), "synthetic import")
            db.journal().put(pending)
            val broken = listOf(reviewed.single().copy(rows = listOf(reviewed.single().rows.first())))
            assertTrue(runCatching { repo.confirmProgramImport(pending, broken, emptyList()) }.isFailure)
            assertTrue(repo.snapshot().exercises.isEmpty())
            val ids = repo.confirmProgramImport(pending, reviewed, emptyList())
            assertEquals(ids, repo.confirmProgramImport(pending, reviewed, emptyList()))
            val snapshot = repo.snapshot()
            val rows = snapshot.routineItems.sortedBy { it.position }
            assertEquals(2, rows.size)
            assertEquals(listOf("10.5", "12"), rows.first().sets.map { it.load })
            assertEquals(listOf(8, 6), rows.first().sets.map { it.plannedReps?.min })
            assertEquals(90, rows.first().plan?.restSeconds)
            assertEquals(rows.first().plan?.group, rows.last().plan?.group)
            assertEquals(true, rows.last().sets.first().plannedReps?.perSide)
        } finally {
            db.close()
        }
    }
}
