package com.emmah.inshape.feature.progress

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.R
import com.emmah.inshape.RedesignInteractionFixture
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.quantity
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressComparisonCoverageTest : RedesignInteractionFixture() {
    @Test fun exerciseListAndChartKeepAssistanceSeparateFromLoad() {
        val weight = MeasurementBasis.Weight
        val assistance = MeasurementBasis.Assistance
        val exercise = Exercise(id = "row", name = "Fixture row", equipmentId = "cable", equipmentName = "Cable stack", basis = weight.key)
        val walk = Exercise(id = "walk", name = "Fixture walk", basis = MeasurementBasis.Time.key)
        val workouts = listOf(
            workout("weight-first", "2026-11-03"),
            workout("weight-recent", "2026-11-10"),
            workout("assist-first", "2026-11-04"),
            workout("assist-recent", "2026-11-11"),
            workout("walk", "2026-11-12")
        )
        val items = workouts.mapIndexed { index, workout ->
            val selected = if (index == 4) walk else exercise
            SessionItem(
                id = "item-${workout.id}",
                workoutId = workout.id,
                exerciseId = selected.id,
                equipmentId = if (index in 2..3) "assist-machine" else selected.equipmentId,
                equipmentName = if (index in 2..3) "" else selected.equipmentName,
                name = selected.name,
                category = selected.category,
                basis = if (index in 2..3) assistance.key else selected.basis,
                quickCompletedAt = workout.createdAt.takeIf { index == 4 }
            )
        }
        val sets = listOf("15", "20", "35", "25").mapIndexed { index, load ->
            SessionSet(itemId = items[index].id, load = load, reps = 10, completedAt = workouts[index].createdAt)
        }
        val data = JournalSnapshot(exercises = listOf(exercise, walk), workouts = workouts, items = items, sets = sets)
        compose.setContent { InShapeTheme { ProgressScreen(data, today) } }

        click(exercise.name)
        compose.onNodeWithText(weight.progressValue(context, BigDecimal("20"))).assertExists()
        val label = ui(R.string.compare) + ", " + exercise.equipmentName + ", " + ui(weight.optionLabel)
        compose.onNodeWithContentDescription(label).performScrollTo().performClick()
        click(ui(R.string.recorded_equipment) + ", " + ui(assistance.optionLabel))
        compose.onNodeWithText(assistance.progressValue(context, BigDecimal("25"))).assertExists()
        compose.onNodeWithText(ui(R.string.best_so_far)).assertExists()
        compose.onNodeWithText(weight.progressValue(context, BigDecimal("20"))).assertDoesNotExist()

        click(ui(R.string.all_exercises))
        click(walk.name)
        compose.onNodeWithText(ui(R.string.best_so_far)).assertDoesNotExist()
    }

    @Test fun allTimePeriodExplainsTheBoundOnDisplayedWeeks() {
        val recent = workout("recent", "2026-11-10")
        val old = workout("old", "1999-01-12")
        val item = SessionItem(
            workoutId = recent.id,
            exerciseId = "exercise",
            equipmentId = "equipment",
            name = "Historic fixture",
            category = "Bodyweight",
            basis = MeasurementBasis.Bodyweight.key,
            quickCompletedAt = recent.createdAt
        )
        val oldItem = item.copy(id = "old-item", workoutId = old.id, quickCompletedAt = old.createdAt)
        val data = JournalSnapshot(workouts = listOf(recent, old), items = listOf(item, oldItem))
        compose.setContent { InShapeTheme { ProgressScreen(data, today) } }

        click(ui(R.string.all))
        compose.onNodeWithText(quantity(R.plurals.chart_latest_weeks, 260)).performScrollTo().assertExists()
        click(ui(R.string.weeks_4))
        compose.onNodeWithText(quantity(R.plurals.chart_latest_weeks, 260)).assertDoesNotExist()
    }

    private val today = LocalDate.parse("2026-11-13")

    private fun workout(id: String, date: String) = WorkoutEntity(
        id = id,
        state = "COMPLETED",
        activeKey = null,
        journalDate = date,
        zoneId = "Europe/Helsinki",
        createdAt = "${date}T09:00:00Z",
        modifiedAt = "${date}T09:00:00Z"
    )
}
