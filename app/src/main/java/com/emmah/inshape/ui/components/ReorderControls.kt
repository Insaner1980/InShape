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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.emmah.inshape.R
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme

@Composable
internal fun ReorderHandle(label: String, enabled: Boolean = true, onMove: (Int) -> Unit) {
    val move by rememberUpdatedState(onMove)
    val rowHeight = with(LocalDensity.current) { Dimens.rowMinTwoLine.toPx() }
    val drag = if (enabled) {
        Modifier.pointerInput(rowHeight) {
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
        }
    } else {
        Modifier
    }
    Box(Modifier.size(Dimens.touch).then(drag), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Outlined.DragIndicator,
            if (enabled) stringResource(R.string.drag_reorder, label) else null,
            Modifier.size(Dimens.buttonIcon),
            tint = InShapeTheme.colors.textSecondary
        )
    }
}

@Composable
internal fun Modifier.reorderActions(canMoveUp: Boolean, canMoveDown: Boolean, onMove: (Int) -> Unit): Modifier {
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    return semantics {
        customActions = buildList {
            if (canMoveUp) {
                add(
                    CustomAccessibilityAction(moveUp) {
                        onMove(-1)
                        true
                    }
                )
            }
            if (canMoveDown) {
                add(
                    CustomAccessibilityAction(moveDown) {
                        onMove(1)
                        true
                    }
                )
            }
        }
    }
}
