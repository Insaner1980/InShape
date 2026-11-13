package com.emmah.inshape.demo

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*

@Preview(widthDp = 390, heightDp = 1000)
@Preview(widthDp = 320, heightDp = 1000, fontScale = 2f)
@Composable fun LightComponents() { ComponentPreview(ThemeMode.Light) }
@Preview(widthDp = 390, heightDp = 1000)
@Preview(widthDp = 320, heightDp = 1000, fontScale = 2f)
@Composable fun DarkComponents() { ComponentPreview(ThemeMode.Dark) }

@Composable
fun ComponentPreview(mode: ThemeMode) {
    InShapeTheme(mode) { Surface { Page(bottomBar = { PrimaryButton("Save", {}, Modifier.fillMaxWidth()) }) {
        ScreenHeader("Components", trailing = { IconCircleButton(Icons.Outlined.Add, "Add", {}) })
        SectionLabel("Section", action = "See all", onAction = {})
        GroupLabel("Group")
        InShapeCard(color = InShapeTheme.colors.accent) { HeroButton("Start workout", {}, icon = Icons.Outlined.Add) }
        ListCard {
            item { ListRow("Workout", subtitle = "Recorded today", leading = { DateTile("13", "Fri") }, onClick = {}) }
            item { ListRow("Lat pulldown", leading = { InitialsTile("Lat pulldown") }, trailing = { Pill("Done", tone = PillTone.Accent) }) }
        }
        ActionRow { PrimaryButton("Primary", {}); SecondaryButton("Secondary", {}); OutlineButton("Outline", {}); TextAction("Action", {}) }
        ActionRow { Pill("Neutral"); Pill("Accent", tone = PillTone.Accent); FilterPill("Filter", true, {}) }
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
        EmptyState("No workouts", "Start when you are ready", "Create routine", {})
        SaveStatus(com.emmah.inshape.SaveState.Saved, {})
    } } }
}
