package com.emmah.inshape.feature.program

import com.emmah.inshape.data.ExercisePlan
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.PlannedReps
import com.emmah.inshape.data.ProgramWorkoutSource
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.WorkoutEntity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionEvidenceTest {
    @Test fun discomfortOnCurrentExerciseBlocksAnOtherwiseEligibleLoadIncrease() {
        val data = eligibleSnapshot()
        val workout = requireNotNull(data.active)
        val activeItem = workout.items.single()
        assertEquals(ProgressionEvidence(BigDecimal("20").stripTrailingZeros(), dates), evidence(data))
        assertNull(progressionEvidence(data, workout, activeItem.copy(row = activeItem.row.copy(discomfort = true))))
    }

    @Test fun mostRecentComparableSessionsIgnoreOtherEquipmentBasesWarmupsAndUnperformedItems() {
        val data = eligibleSnapshot()
        val recent = data.workouts.first()
        val row = data.items.first()
        val set = data.sets.first()
        val exclusions = listOf(
            row.copy(equipmentId = "other-machine"),
            row.copy(exerciseId = "other-exercise"),
            row.copy(basis = "level"),
            row.copy(plan = ExercisePlan(warmup = true)),
            row
        )
        val otherDays = exclusions.indices.map { index ->
            recent.copy(id = "other-$index", journalDate = "2026-11-21")
        }
        val items = exclusions.zip(otherDays) { item, day -> item.copy(id = day.id, workoutId = day.id) }
        val sets = items.dropLast(1).map { set.copy(id = it.id, itemId = it.id, load = "50") }

        assertEquals(
            ProgressionEvidence(BigDecimal("20").stripTrailingZeros(), dates),
            evidence(data.copy(workouts = data.workouts + otherDays, items = data.items + items, sets = data.sets + sets))
        )
        assertNull(evidence(data.copy(workouts = data.workouts.drop(1))))
    }

    @Test fun recentLightSessionOrDiscomfortPreventsTheSuggestion() {
        val data = eligibleSnapshot()
        val recent = data.workouts.first()
        val light = recent.copy(program = requireNotNull(recent.program).copy(mode = "LIGHT"))
        val uncomfortable = data.items.first().copy(discomfort = true)

        assertNull(evidence(data.copy(workouts = listOf(light) + data.workouts.drop(1))))
        assertNull(evidence(data.copy(items = listOf(uncomfortable) + data.items.drop(1))))
    }

    @Test fun everyPriorPlannedSetMustBeCompletedAtThePrescribedSideAndLoad() {
        val data = eligibleSnapshot()
        val set = data.sets.first()
        val additional = set.copy(id = "additional-set", position = 1, load = "20.00")
        assertEquals(ProgressionEvidence(BigDecimal("20").stripTrailingZeros(), dates), evidence(data.copy(sets = data.sets + additional)))
        val unfinished = listOf(
            additional.copy(completedAt = null),
            additional.copy(reps = 11),
            additional.copy(perSide = true),
            additional.copy(load = "25"),
            additional.copy(load = null)
        )
        unfinished.forEach { incomplete ->
            assertNull(evidence(data.copy(sets = data.sets + incomplete)))
        }
    }

    @Test fun currentExerciseNeedsANormalProgramAndUnfinishedPrescribedSets() {
        val data = eligibleSnapshot()
        val workout = requireNotNull(data.active)
        val item = workout.items.single()
        val variants = listOf(
            item.copy(row = item.row.copy(basis = "assistance_kg")),
            item.copy(row = item.row.copy(plan = ExercisePlan(warmup = true))),
            item.copy(sets = emptyList()),
            item.copy(sets = item.sets + item.sets.single().copy(plannedReps = null)),
            item.copy(sets = item.sets.map { it.copy(completedAt = workout.row.createdAt) })
        )
        assertNull(progressionEvidence(data, workout.copy(row = workout.row.copy(program = null)), item))
        assertNull(
            progressionEvidence(
                data,
                workout.copy(row = workout.row.copy(program = requireNotNull(workout.row.program).copy(mode = "LIGHT"))),
                item
            )
        )
        variants.forEach { assertNull(progressionEvidence(data, workout, it)) }
    }

    private val dates = listOf("2026-11-20", "2026-11-18")

    private fun evidence(data: JournalSnapshot): ProgressionEvidence? {
        val workout = requireNotNull(data.active)
        return progressionEvidence(data, workout, workout.items.single())
    }

    private fun eligibleSnapshot(): JournalSnapshot {
        val current = WorkoutEntity(
            journalDate = "2026-11-22",
            zoneId = "UTC",
            createdAt = "2026-11-22T12:00:00Z",
            modifiedAt = "2026-11-22T12:00:00Z",
            program = ProgramWorkoutSource("enrollment", "program", 1, "day", 0, 1, "STANDARD")
        )
        val previous = dates.map { date ->
            current.copy(id = date, journalDate = date, state = "COMPLETED", activeKey = null)
        }
        val item = SessionItem(
            workoutId = current.id,
            exerciseId = "exercise",
            equipmentId = "machine",
            name = "Fixture leg press",
            category = "Machine",
            basis = "kg"
        )
        val previousItems = previous.map { item.copy(id = it.id, workoutId = it.id, disposition = "DONE") }
        val target = SessionSet(itemId = item.id, plannedReps = PlannedReps(8, 12))
        val previousSets = previousItems.map {
            target.copy(id = it.id, itemId = it.id, load = "20", reps = 12, completedAt = "${it.workoutId}T12:00:00Z")
        }
        return JournalSnapshot(workouts = previous + current, items = previousItems + item, sets = previousSets + target)
    }
}
