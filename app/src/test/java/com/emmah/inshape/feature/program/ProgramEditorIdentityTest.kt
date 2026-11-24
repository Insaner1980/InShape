package com.emmah.inshape.feature.program

import com.emmah.inshape.data.ProgramExerciseLink
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramEditorIdentityTest {
    @Test fun addingEitherRepresentationBlocksUndoOfTheLinkedMovement() {
        val links = listOf(ProgramExerciseLink("press", "one"), ProgramExerciseLink("row", "two"))
        val occupied = setOf("press", "local_one")
        assertEquals(occupied, occupiedMovementIds(setOf("press"), links))
        assertEquals(occupied, occupiedMovementIds(setOf("local_one"), links))
    }
}
