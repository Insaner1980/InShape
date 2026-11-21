package com.emmah.inshape.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.emmah.inshape.ui.theme.DarkColors
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType

enum class PillTone { Neutral, Accent }

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.Neutral,
    style: TextStyle = MaterialTheme.typography.labelSmall
) {
    val colors = InShapeTheme.colors
    val accent = tone == PillTone.Accent
    Surface(
        modifier.heightIn(min = Dimens.setNumber),
        shape = InShapeShapes.chip,
        color = if (accent) colors.ink else colors.surface,
        contentColor = if (accent) colors.onInk else colors.text
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, Modifier.padding(horizontal = Dimens.pillPadding, vertical = Dimens.innerTiny), style = style)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = InShapeTheme.colors
    SecondaryTabRow(
        selectedTabIndex = selected,
        modifier = modifier,
        containerColor = colors.background,
        contentColor = colors.text,
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(selected, matchContentSize = false),
                height = Dimens.tabIndicator,
                color = colors.ink
            )
        },
        divider = { HorizontalDivider(thickness = Dimens.line, color = colors.divider) }
    ) {
        options.forEachIndexed { index, option ->
            Tab(
                selected = selected == index,
                onClick = { onSelect(index) },
                modifier = Modifier.heightIn(min = Dimens.touch),
                selectedContentColor = colors.text,
                unselectedContentColor = colors.textSecondary,
                text = {
                    Text(
                        option,
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }
            )
        }
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, showDropdown: Boolean = false) {
    val colors = InShapeTheme.colors
    Surface(
        onClick,
        modifier.heightIn(min = Dimens.touch).semantics {
            this.selected = selected
            role =
                Role.Tab
        },
        shape = InShapeShapes.chip,
        color = if (selected) colors.text else colors.surface
    ) {
        Row(
            Modifier.padding(horizontal = Dimens.inner, vertical = Dimens.innerSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.innerTiny)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) colors.background else colors.text
            )
            if (showDropdown) {
                Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(Dimens.iconSmall))
            }
        }
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = InShapeTheme.colors
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()
    Surface(
        modifier.fillMaxWidth(),
        shape = InShapeShapes.field,
        color = colors.surface
    ) {
        BoxWithConstraints(Modifier.padding(Dimens.innerTiny)) {
            val widestLabel = options.maxOfOrNull {
                textMeasurer.measure(it, labelStyle, softWrap = false, maxLines = 1).size.width
            } ?: 0
            val itemWidth = with(density) { widestLabel.toDp() } + Dimens.innerTiny * 2
            val count = options.size.coerceAtLeast(1)
            val itemsPerRow = when {
                density.fontScale >= 1.3f -> 1
                itemWidth * count + Dimens.innerTiny * (count - 1) <= maxWidth -> count
                itemWidth * 2 + Dimens.innerTiny <= maxWidth -> 2
                else -> 1
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.innerTiny),
                verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny),
                maxItemsInEachRow = itemsPerRow
            ) {
                options.forEachIndexed { index, label ->
                    Surface(
                        onClick = {
                            onSelect(index)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.touch).semantics {
                            this.selected =
                                selected == index
                            role = Role.Tab
                        },
                        shape = InShapeShapes.segment,
                        color = if (selected ==
                            index
                        ) {
                            colors.ink
                        } else {
                            Color.Transparent
                        }
                    ) {
                        Box(
                            Modifier.padding(horizontal = Dimens.innerTiny, vertical = Dimens.innerTiny),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = labelStyle,
                                maxLines = 1,
                                softWrap = false,
                                color = if (selected ==
                                    index
                                ) {
                                    colors.onInk
                                } else {
                                    colors.textSecondary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InShapeSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = InShapeTheme.colors
    Switch(
        checked,
        onChange,
        modifier.semantics {
            this.contentDescription = contentDescription
        },
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = colors.ink,
            checkedThumbColor = colors.onInk,
            uncheckedTrackColor = colors.background,
            uncheckedThumbColor = colors.textSecondary,
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = colors.textSecondary,
            disabledCheckedTrackColor = colors.ink.copy(alpha = .38f),
            disabledCheckedThumbColor = colors.onInk.copy(alpha = .38f),
            disabledUncheckedTrackColor = colors.background,
            disabledUncheckedThumbColor = colors.textSecondary.copy(alpha = .38f),
            disabledUncheckedBorderColor = colors.textSecondary.copy(alpha = .38f)
        )
    )
}

@Composable
fun Stepper(
    valueLabel: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseEnabled: Boolean,
    decreaseDescription: String,
    increaseDescription: String,
    modifier: Modifier = Modifier
) {
    Surface(modifier, shape = InShapeShapes.chip, color = InShapeTheme.colors.surface) {
        Row(Modifier.padding(Dimens.innerTiny), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onDecrease,
                Modifier.size(Dimens.touch),
                enabled = decreaseEnabled,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = InShapeTheme.colors.surface,
                    contentColor = InShapeTheme.colors.text
                )
            ) {
                Icon(Icons.Outlined.Remove, decreaseDescription)
            }
            Box(
                Modifier.widthIn(min = Dimens.weekCircle).padding(horizontal = Dimens.innerTiny),
                contentAlignment = Alignment.Center
            ) {
                Text(valueLabel, style = InShapeType.setValue)
            }
            FilledIconButton(
                onIncrease,
                Modifier.size(Dimens.touch),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = InShapeTheme.colors.surface,
                    contentColor = InShapeTheme.colors.text
                )
            ) {
                Icon(Icons.Outlined.Add, increaseDescription)
            }
        }
    }
}

enum class ProgressState { Done, Current, Pending }

@Composable
fun ProgressSegments(
    states: List<ProgressState>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onDarkSurface: Boolean = false
) {
    val colors = InShapeTheme.colors
    Row(
        modifier.fillMaxWidth().semantics {
            this.contentDescription = contentDescription
        },
        horizontalArrangement = Arrangement.spacedBy(Dimens.innerTiny)
    ) {
        states.forEach { state ->
            Surface(
                Modifier.weight(1f).height(Dimens.progressHeight),
                shape = InShapeShapes.chip,
                color = if (onDarkSurface) {
                    when (state) {
                        ProgressState.Done -> DarkColors.ink
                        ProgressState.Current -> DarkColors.textSecondary
                        ProgressState.Pending -> Color.Transparent
                    }
                } else {
                    when (state) {
                        ProgressState.Done -> colors.ink
                        ProgressState.Current -> colors.textTertiary
                        ProgressState.Pending -> colors.surface
                    }
                },
                border = if (onDarkSurface && state == ProgressState.Pending) {
                    BorderStroke(Dimens.line, DarkColors.textTertiary)
                } else {
                    null
                }
            ) { }
        }
    }
}
