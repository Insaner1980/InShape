package com.emmah.inshape.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.emmah.inshape.ui.theme.*

@Composable
fun InShapeCard(modifier: Modifier = Modifier, color: Color = InShapeTheme.colors.surface, contentPadding: PaddingValues = PaddingValues(Dimens.cardPadding), onClick: (() -> Unit)? = null, gap: Dp = Dimens.inner, content: @Composable ColumnScope.() -> Unit) {
    val body: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(gap), content = content) }
    val card = modifier.fillMaxWidth().cardElevation()
    if (onClick == null) Surface(card, shape = InShapeShapes.card, color = color, contentColor = InShapeTheme.colors.text, content = body)
    else Surface(onClick, card, shape = InShapeShapes.card, color = color, contentColor = InShapeTheme.colors.text, content = body)
}

@Composable
fun Page(modifier: Modifier = Modifier, bottomBar: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Dimens.maxPageWidth).fillMaxWidth().imePadding()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = Dimens.screenPadding, end = Dimens.screenPadding, top = Dimens.screenTop, bottom = Dimens.sectionGap), verticalArrangement = Arrangement.spacedBy(Dimens.itemGap), content = content)
            if (bottomBar != null) Surface(color = InShapeTheme.colors.background) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = Dimens.screenPadding, end = Dimens.screenPadding, top = Dimens.itemGap, bottom = Dimens.inner)) { bottomBar() }
            }
        }
    }
}

@Composable
fun ResponsiveRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall), verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall), maxItemsInEachRow = if (LocalDensity.current.fontScale >= 1.3f) 1 else Int.MAX_VALUE, content = content)
}

@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    if (LocalDensity.current.fontScale >= 1.3f) Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
        Text(title, style = MaterialTheme.typography.headlineLarge); trailing?.invoke()
    } else Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge); trailing?.invoke()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    ResponsiveRow(modifier.padding(top = Dimens.itemGap, start = Dimens.innerTiny)) {
        Text(text, Modifier.weight(1f).align(Alignment.CenterVertically), style = MaterialTheme.typography.titleSmall, color = InShapeTheme.colors.textSecondary)
        if (action != null && onAction != null) TextAction(action, onAction)
    }
}

@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(java.util.Locale.ENGLISH), modifier.padding(top = Dimens.inner, start = Dimens.innerTiny), style = MaterialTheme.typography.labelSmall, color = InShapeTheme.colors.textSecondary)
}

@Composable
fun EmptyState(title: String, message: String, action: String? = null, onAction: () -> Unit = {}, modifier: Modifier = Modifier) {
    InShapeCard(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = InShapeTheme.colors.textSecondary)
        if (action != null) SecondaryButton(action, onAction)
    }
}
