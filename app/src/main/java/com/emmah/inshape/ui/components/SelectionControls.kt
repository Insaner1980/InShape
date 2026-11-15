package com.emmah.inshape.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.isDark

enum class PillTone { Neutral, Accent }

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, tone: PillTone = PillTone.Neutral) {
    val colors = InShapeTheme.colors
    Surface(
        modifier.heightIn(min = Dimens.weekCircle),
        shape = InShapeShapes.chip,
        color = if (tone ==
            PillTone.Accent
        ) {
            colors.ink
        } else {
            colors.surface
        }
    ) {
        Text(
            text,
            Modifier.padding(horizontal = Dimens.itemGap, vertical = Dimens.innerTiny),
            style = MaterialTheme.typography.labelMedium,
            color = if (tone ==
                PillTone.Accent
            ) {
                colors.onInk
            } else {
                colors.textSecondary
            }
        )
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        Box(
            Modifier.padding(horizontal = Dimens.inner, vertical = Dimens.innerSmall),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) colors.background else colors.text
            )
        }
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val colors = InShapeTheme.colors
    val stacked = LocalDensity.current.fontScale >= 1.3f
    Surface(
        modifier.fillMaxWidth(),
        shape = InShapeShapes.field,
        color = colors.surface
    ) {
        FlowRow(
            Modifier.padding(Dimens.innerTiny),
            horizontalArrangement = Arrangement.spacedBy(Dimens.innerTiny),
            verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny),
            maxItemsInEachRow = if (stacked) 1 else options.size.coerceAtLeast(1)
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
                            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
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
            uncheckedTrackColor = colors.surfaceStrong,
            uncheckedThumbColor = colors.background,
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = colors.divider
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
                    containerColor = InShapeTheme.colors.surfaceStrong,
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
                    containerColor = InShapeTheme.colors.surfaceStrong,
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
fun ProgressSegments(states: List<ProgressState>, contentDescription: String, modifier: Modifier = Modifier, onAccent: Boolean = false) {
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
                color =
                    if (onAccent) {
                        if (state ==
                            ProgressState.Done
                        ) {
                            colors.onInk
                        } else {
                            colors.textTertiary.copy(
                                alpha = if (state ==
                                    ProgressState.Current
                                ) {
                                    .5f
                                } else {
                                    .35f
                                }
                            )
                        }
                    } else {
                        when (state) {
                            ProgressState.Done -> colors.ink
                            ProgressState.Current -> colors.textTertiary
                            ProgressState.Pending -> colors.surfaceStrong
                        }
                    }
            ) { }
        }
    }
}
