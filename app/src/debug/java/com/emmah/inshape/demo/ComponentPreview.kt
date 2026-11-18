package com.emmah.inshape.demo

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.FilterPill
import com.emmah.inshape.ui.components.GroupLabel
import com.emmah.inshape.ui.components.IconCircleButton
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.InShapeSwitch
import com.emmah.inshape.ui.components.InitialsTile
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.Pill
import com.emmah.inshape.ui.components.PillTone
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.ProgressSegments
import com.emmah.inshape.ui.components.ProgressState
import com.emmah.inshape.ui.components.ScreenHeader
import com.emmah.inshape.ui.components.SearchField
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.components.SegmentedControl
import com.emmah.inshape.ui.components.SelectField
import com.emmah.inshape.ui.components.Stepper
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.ThemeMode

@Preview(widthDp = 390, heightDp = 1000)
@Preview(widthDp = 320, heightDp = 1000, fontScale = 2f)
@Composable
private fun LightComponents() {
    ComponentPreview(ThemeMode.Light)
}

@Preview(widthDp = 390, heightDp = 1000)
@Preview(widthDp = 320, heightDp = 1000, fontScale = 2f)
@Composable
private fun DarkComponents() {
    ComponentPreview(ThemeMode.Dark)
}

@Composable
fun ComponentPreview(mode: ThemeMode, modifier: Modifier = Modifier) {
    InShapeTheme(mode) {
        Surface(modifier) {
            Page(bottomBar = { PrimaryButton("Save", {}, Modifier.fillMaxWidth()) }) {
                ScreenHeader("Components", trailing = {
                    IconCircleButton(Icons.Outlined.Add, "Add", {})
                })
                SectionHeader("Section", action = "See all", onAction = {})
                GroupLabel("Group")
                InShapeCard(color = InShapeTheme.colors.ink) {
                    PrimaryButton("Start workout", {}, icon = Icons.Outlined.Add, inverted = true)
                }
                ListCard {
                    item {
                        ListRow("Workout", subtitle = "Recorded today", onClick = {})
                    }
                    item {
                        ListRow("Lat pulldown", leading = {
                            InitialsTile("Lat pulldown")
                        }, trailing = { Pill("Done", tone = PillTone.Accent) })
                    }
                }
                ActionRow {
                    PrimaryButton("Primary", {})
                    OutlineButton("Secondary", {})
                    OutlineButton("Outline", {})
                    TextAction("Action", {})
                }
                ActionRow {
                    Pill("Neutral")
                    Pill("Accent", tone = PillTone.Accent)
                    FilterPill("Filter", true, {})
                }
                SegmentedControl(listOf("System", "Light", "Dark"), 0, {})
                SelectField("Exercise", "Lat pulldown", listOf("Lat pulldown"), {})
                SelectField("Exercise", "Lat pulldown", listOf("Lat pulldown"), {}, inline = true)
                TextField("", "Note", {})
                NumberField("27.5", {}, "kg", decimal = true)
                NumberField("10", {}, "Reps", compact = true)
                SearchField("", {}, "Search exercises")
                InShapeSwitch(true, {}, "Reminder")
                Stepper("3", {}, {}, true, "Decrease", "Increase")
                ProgressSegments(ProgressState.entries, "1 of 3 done")
                EmptyState("No workouts", "Start when you are ready", action = "Create routine", onAction = {})
            }
        }
    }
}
