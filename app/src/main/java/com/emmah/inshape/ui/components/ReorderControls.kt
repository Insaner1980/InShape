package com.emmah.inshape.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme

@Composable
internal fun ReorderHandle(label: String, onMove: (Int) -> Unit) {
    val move by rememberUpdatedState(onMove)
    val rowHeight = with(LocalDensity.current) { Dimens.rowMinTwoLine.toPx() }
    Box(
        Modifier.size(Dimens.touch).pointerInput(rowHeight) {
            var distance = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { distance = 0f },
                onDrag = { change, amount ->
                    change.consume()
                    distance += amount.y
                },
                onDragEnd = {
                    val offset = (distance / rowHeight).toInt()
                    if (offset != 0) move(offset)
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.DragIndicator,
            "Drag to reorder $label",
            Modifier.size(Dimens.buttonIcon),
            tint = InShapeTheme.colors.textSecondary
        )
    }
}

internal fun Modifier.reorderActions(canMoveUp: Boolean, canMoveDown: Boolean, onMove: (Int) -> Unit): Modifier = semantics {
    customActions = buildList {
        if (canMoveUp) {
            add(
                CustomAccessibilityAction("Move up") {
                    onMove(-1)
                    true
                }
            )
        }
        if (canMoveDown) {
            add(
                CustomAccessibilityAction("Move down") {
                    onMove(1)
                    true
                }
            )
        }
    }
}
