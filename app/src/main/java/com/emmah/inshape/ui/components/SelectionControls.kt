package com.emmah.inshape.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emmah.inshape.ui.theme.*

enum class PillTone { Neutral, Accent }
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, tone: PillTone = PillTone.Neutral) {
    val colors = InShapeTheme.colors
    Surface(modifier.heightIn(min = 28.dp), shape = InShapeShapes.pill, color = if (tone == PillTone.Accent) colors.accentContainer else colors.surfaceInset) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = InShapeType.pill, color = if (tone == PillTone.Accent) colors.onAccentContainer else colors.textSecondary)
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = InShapeTheme.colors
    Surface(onClick, modifier.heightIn(min = Dimens.touch).semantics { this.selected = selected; role = Role.Tab }.then(if (selected) Modifier else Modifier.cardElevation(InShapeShapes.pill)), shape = InShapeShapes.pill, color = if (selected) colors.text else colors.surface) {
        Box(Modifier.padding(horizontal = Dimens.inner, vertical = Dimens.innerSmall), contentAlignment = Alignment.Center) { Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium), color = if (selected) colors.background else colors.text) }
    }
}

@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, onCard: Boolean = false) {
    val colors = InShapeTheme.colors
    val stacked = LocalDensity.current.fontScale >= 1.3f
    Surface(modifier.fillMaxWidth(), shape = if (stacked) InShapeShapes.card else InShapeShapes.pill, color = if (onCard) colors.surfaceInset else colors.segmentTrack) {
        FlowRow(Modifier.padding(Dimens.innerTiny), horizontalArrangement = Arrangement.spacedBy(Dimens.innerTiny), verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny), maxItemsInEachRow = if (stacked) 1 else options.size.coerceAtLeast(1)) {
            options.forEachIndexed { index, label ->
                Surface(onClick = { onSelect(index) }, modifier = Modifier.weight(1f).heightIn(min = Dimens.touch).semantics { this.selected = selected == index; role = Role.Tab }, shape = InShapeShapes.pill,
                    shadowElevation = if (!colors.isDark && selected == index) 1.dp else 0.dp, color = if (selected == index) colors.selected else Color.Transparent) {
                    Box(Modifier.padding(horizontal = Dimens.innerSmall, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = if (selected == index) colors.text else colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun InShapeSwitch(checked: Boolean, onChange: (Boolean) -> Unit, contentDescription: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = InShapeTheme.colors
    Switch(checked, onChange, modifier.semantics { this.contentDescription = contentDescription }, enabled = enabled,
        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent, uncheckedTrackColor = colors.muted, uncheckedThumbColor = colors.text, checkedBorderColor = Color.Transparent, uncheckedBorderColor = Color.Transparent))
}

@Composable
fun Stepper(valueLabel: String, onDecrease: () -> Unit, onIncrease: () -> Unit, decreaseEnabled: Boolean, decreaseDescription: String, increaseDescription: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = InShapeShapes.pill, color = InShapeTheme.colors.surfaceInset) {
        Row(Modifier.padding(Dimens.innerTiny), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onDecrease, Modifier.size(Dimens.touch), enabled = decreaseEnabled, colors = IconButtonDefaults.filledIconButtonColors(containerColor = InShapeTheme.colors.selected, contentColor = InShapeTheme.colors.text)) { Icon(Icons.Outlined.Remove, decreaseDescription) }
            Box(Modifier.widthIn(min = 32.dp).padding(horizontal = Dimens.innerTiny), contentAlignment = Alignment.Center) { Text(valueLabel, style = InShapeType.setNumber) }
            FilledIconButton(onIncrease, Modifier.size(Dimens.touch), colors = IconButtonDefaults.filledIconButtonColors(containerColor = InShapeTheme.colors.selected, contentColor = InShapeTheme.colors.text)) { Icon(Icons.Outlined.Add, increaseDescription) }
        }
    }
}

enum class ProgressState { Done, Current, Pending }
@Composable
fun ProgressSegments(states: List<ProgressState>, contentDescription: String, modifier: Modifier = Modifier, onAccent: Boolean = false) {
    val colors = InShapeTheme.colors
    Row(modifier.fillMaxWidth().semantics { this.contentDescription = contentDescription }, horizontalArrangement = Arrangement.spacedBy(Dimens.innerTiny)) {
        states.forEach { state -> Surface(Modifier.weight(1f).height(6.dp), shape = InShapeShapes.pill, color =
            if (onAccent) colors.onAccent.copy(alpha = if (state == ProgressState.Done) 1f else .2f)
            else when (state) { ProgressState.Done -> colors.mark; ProgressState.Current -> colors.text; ProgressState.Pending -> colors.muted }) { } }
    }
}
