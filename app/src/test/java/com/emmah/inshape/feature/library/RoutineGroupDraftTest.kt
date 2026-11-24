package com.emmah.inshape.feature.library

import com.emmah.inshape.data.ExercisePlan
import com.emmah.inshape.data.PlanGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineGroupDraftTest {
    private val group = PlanGroup("pair", "superset", 2)
    private val plan = ExercisePlan(restSeconds = 60, group = group)
    private val first = RoutineItemDraft(exerciseId = "first", plan = plan)
    private val second = RoutineItemDraft(exerciseId = "second", plan = plan)
    private val other = RoutineItemDraft(exerciseId = "other")

    @Test fun movingEitherGroupMemberMovesTheWholeGroup() {
        val draft = RoutineDraft(items = listOf(first, second, other))
        val moved = draft.moveItem(index = 1, offset = 1)
        assertEquals(listOf(other, first, second), moved.items)
        assertEquals(draft, moved.moveItem(index = 1, offset = -1))
    }

    @Test fun movingAnExercisePastAGroupKeepsTheGroupTogether() {
        val draft = RoutineDraft(items = listOf(other, first, second))
        assertEquals(listOf(first, second, other), draft.moveItem(index = 0, offset = 1).items)
    }

    @Test fun singletonAndSplitGroupsRetainTheirExerciseData() {
        val singleton = RoutineDraft(items = listOf(first, other)).withContiguousGroups()
        assertEquals(first.copy(plan = plan.copy(group = null)), singleton.items.first())
        val split = RoutineDraft(items = listOf(first, other, second)).withContiguousGroups()
        assertEquals(listOf(first.id, other.id, second.id), split.items.map { it.id })
        split.items.forEach { assertNull(it.plan?.group) }
    }
}
