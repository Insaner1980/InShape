package com.emmah.inshape.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.emmah.inshape.ui.theme.*

class ListCardScope {
    internal val items = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) { items += content }
}

@Composable
fun ListCard(modifier: Modifier = Modifier, content: ListCardScope.() -> Unit) {
    val rows = ListCardScope().apply(content).items
    InShapeCard(modifier, contentPadding = PaddingValues(horizontal = Dimens.listCardPadding), gap = 0.dp) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = InShapeTheme.colors.divider)
            row()
        }
    }
}

@Composable
fun ListRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null, enabled: Boolean = true, titleStyle: TextStyle = InShapeType.rowTitle) {
    val large = LocalDensity.current.fontScale >= 1.3f
    val rowModifier = modifier.fillMaxWidth().heightIn(min = if (subtitle == null) Dimens.rowMin else Dimens.rowMinTwoLine)
        .then(if (onClick == null) Modifier else Modifier.clickable(enabled = enabled, onClick = onClick))
        .padding(vertical = Dimens.innerSmall)
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
        if (!large) leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)) {
            if (large) leading?.invoke()
            Text(title, style = titleStyle)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.textSecondary)
            if (large) trailing?.invoke()
        }
        if (!large) trailing?.invoke()
        if (trailing == null && onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = InShapeTheme.colors.textSecondary)
    }
}

@Composable
fun DateTile(day: String, weekday: String, modifier: Modifier = Modifier) {
    Surface(modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp), shape = InShapeShapes.tile, color = InShapeTheme.colors.surfaceInset) {
        Column(Modifier.padding(Dimens.innerTiny), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(day, style = InShapeType.compactDisplay)
            Text(weekday, style = InShapeType.tileCaption, color = InShapeTheme.colors.textSecondary)
        }
    }
}

@Composable
fun InitialsTile(text: String, modifier: Modifier = Modifier) {
    Surface(modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp), shape = InShapeShapes.tile, color = InShapeTheme.colors.surfaceInset) {
        Box(Modifier.padding(Dimens.innerTiny), contentAlignment = Alignment.Center) { Text(text.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }, style = MaterialTheme.typography.labelSmall, color = InShapeTheme.colors.textSecondary) }
    }
}
