package com.emmah.inshape.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme

@Composable
fun AppMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    inHeader: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded,
        onDismiss,
        modifier,
        offset = DpOffset(if (inHeader) -Dimens.headerActionEnd else Dimens.zero, Dimens.zero),
        shape = InShapeShapes.card,
        containerColor = InShapeTheme.colors.background,
        tonalElevation = Dimens.zero,
        shadowElevation = Dimens.zero,
        border = BorderStroke(Dimens.line, InShapeTheme.colors.divider),
        content = content
    )
}

@Composable
fun MenuAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val colors = InShapeTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) {
                    colors.error
                } else if (enabled) {
                    colors.text
                } else {
                    colors.textTertiary
                }
            )
        },
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Dimens.touch),
        leadingIcon = { Icon(icon, null, Modifier.size(Dimens.icon), tint = if (destructive) colors.error else colors.textSecondary) }
    )
}
