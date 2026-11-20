package com.emmah.inshape.demo

import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
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

private const val LAT_PULLDOWN = "Ylätalja"

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
            Page(bottomBar = { PrimaryButton(stringResource(R.string.save), {}, Modifier.fillMaxWidth()) }) {
                ScreenHeader("Komponentit", trailing = {
                    IconCircleButton(Icons.Outlined.Add, "Lisää", {})
                })
                SectionHeader("Osio", trailing = {
                    TextAction("Näytä kaikki", {}, icon = Icons.AutoMirrored.Outlined.List)
                })
                GroupLabel("Ryhmä")
                InShapeCard(color = InShapeTheme.colors.ink) {
                    PrimaryButton(stringResource(R.string.start_workout), {}, icon = Icons.Outlined.Add, inverted = true)
                }
                ListCard {
                    item {
                        ListRow("Treeni", subtitle = "Kirjattu tänään", onClick = {})
                    }
                    item {
                        ListRow(LAT_PULLDOWN, leading = {
                            InitialsTile(LAT_PULLDOWN)
                        }, trailing = { Pill(stringResource(R.string.done), tone = PillTone.Accent) })
                    }
                }
                ActionRow {
                    PrimaryButton("Ensisijainen", {})
                    OutlineButton("Toissijainen", {})
                    OutlineButton("Reunus", {})
                    TextAction("Toiminto", {}, icon = Icons.Outlined.Add)
                }
                ActionRow {
                    Pill("Neutraali")
                    Pill("Korostus", tone = PillTone.Accent)
                    FilterPill("Suodatin", true, {})
                }
                SegmentedControl(listOf("Järjestelmä", "Vaalea", "Tumma"), 0, {})
                SelectField(stringResource(R.string.exercise), LAT_PULLDOWN, listOf(LAT_PULLDOWN), {})
                SelectField(stringResource(R.string.exercise), LAT_PULLDOWN, listOf(LAT_PULLDOWN), {}, inline = true)
                TextField("", stringResource(R.string.note), {})
                NumberField("27.5", {}, "kg", decimal = true)
                NumberField("10", {}, stringResource(R.string.reps), compact = true)
                SearchField("", {}, stringResource(R.string.search_exercises))
                InShapeSwitch(true, {}, "Muistutus")
                Stepper("3", {}, {}, true, "Vähennä", "Lisää")
                ProgressSegments(ProgressState.entries, "1/3 tehty")
                EmptyState("Ei treenejä", "Aloita, kun olet valmis", action = stringResource(R.string.create_routine), onAction = {})
            }
        }
    }
}
