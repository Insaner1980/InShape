package com.emmah.inshape.feature.program

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgramCatalogTest {
    @Test fun packagedCatalogContainsEveryDayAndGuide() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = ProgramCatalog.read(context.assets.open("programs/beginner_fi_v1.json").bufferedReader().use { it.readText() })
        assertEquals(
            listOf("rauhallinen-aloitus", "tasapainoinen-perusohjelma", "kolme-lyhytta-kokovartaloa"),
            catalog.programs.map { it.id }
        )
        val days = catalog.programs.flatMap { it.sessions }
        assertEquals(listOf(6, 6, 6, 5, 5, 5), days.map { it.exercises.size })
        val slots = days.flatMap { it.exercises }
        assertEquals(11, slots.map { it.exerciseId }.distinct().size)
        assertEquals(4, slots.count { it.target.perSide })
        assertEquals(120, slots.first().restSeconds)
        assertEquals(60, days.first().exercises.last().restSeconds)
        assertEquals(10..15, slots.first().target.min..slots.first().target.max)
        assertEquals(6..10, days.first().exercises.last().alternatives.single().target!!.let { it.min..it.max })
        val keys = slots.flatMap { slot -> listOf(slot.exerciseId) + slot.alternatives.map { it.exerciseId } }.toSet()
        assertEquals(19, keys.size)
        assertTrue(keys.all { programArtwork(it) != null })
        assertEquals("Rintapunnerrus laitteessa", catalog.guideName("machine_chest_press"))
        assertEquals("Vatsarutistus laitteessa", catalog.guideName("machine_crunch"))
        assertEquals("Pystypunnerrus laitteessa", catalog.guideName("machine_shoulder_press"))
        mapOf(
            "machine_chest_press" to "Rintapunnerruslaite",
            "machine_crunch" to "Vatsarutistuslaite",
            "machine_shoulder_press" to "Pystypunnerruslaite"
        ).forEach { (id, oldName) ->
            val slot = slots.first { it.exerciseId == id }
            assertTrue(slot.matchesExerciseName(oldName))
            assertTrue(slot.matchesExerciseName(slot.name))
            assertFalse(slot.matchesExerciseName("$oldName, oma"))
        }
        assertEquals("Dead bug, 2 × 6–10 kummallekin puolelle", days.first().exercises.last().alternatives.single().label)
        assertEquals(
            listOf("3–4 toistoa varaa", "3–4 toistoa varaa", "2–3 toistoa varaa", "3–4 toistoa varaa, tarvittaessa kevyemmin"),
            catalog.programs.first().phases.map { it.effort }
        )
        assertTrue(
            days.all { day ->
                day.miniExerciseIds.size == 4 && day.miniExerciseIds.all { key -> day.exercises.any { it.exerciseId == key } }
            }
        )
    }
}
