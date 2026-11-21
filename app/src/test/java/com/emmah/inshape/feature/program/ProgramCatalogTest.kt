package com.emmah.inshape.feature.program

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
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
        assertTrue(days.all { day ->
            day.miniExerciseIds.size == 4 && day.miniExerciseIds.all { key -> day.exercises.any { it.exerciseId == key } }
        })
    }
}
