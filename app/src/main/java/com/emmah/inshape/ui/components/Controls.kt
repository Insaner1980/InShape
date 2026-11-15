package com.emmah.inshape.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme

@Composable
fun InShapeCard(
    modifier: Modifier = Modifier,
    color: Color = InShapeTheme.colors.background,
    inset: Boolean = false,
    borderColor: Color = InShapeTheme.colors.outline,
    contentPadding: PaddingValues = if (inset) {
        PaddingValues(
            horizontal = Dimens.rowPadding,
            vertical = Dimens.itemGap
        )
    } else {
        PaddingValues(Dimens.cardPadding)
    },
    onClick: (() -> Unit)? = null,
    gap: Dp = Dimens.inner,
    content: @Composable ColumnScope.() -> Unit
) {
    val body: @Composable () -> Unit = {
        Column(
            Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(gap),
            content = content
        )
    }
    val card = modifier.fillMaxWidth()
    val colors = InShapeTheme.colors
    val shape = if (inset) InShapeShapes.field else InShapeShapes.card
    val border = if (inset || color == colors.ink) null else BorderStroke(Dimens.line, borderColor)
    val background = if (inset) colors.surface else color
    if (onClick == null) {
        Surface(card, shape = shape, border = border, color = background, contentColor = colors.text, content = body)
    } else {
        Surface(onClick, card, shape = shape, border = border, color = background, contentColor = colors.text, content = body)
    }
}

@Composable
fun Page(modifier: Modifier = Modifier, bottomBar: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Dimens.maxPageWidth).fillMaxWidth().imePadding()) {
            Column(
                Modifier.weight(
                    1f
                ).fillMaxWidth().verticalScroll(
                    rememberScrollState()
                ).padding(
                    start = Dimens.screenPadding,
                    end = Dimens.screenPadding,
                    top = Dimens.screenTop,
                    bottom = Dimens.sectionGap
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.itemGap),
                content = content
            )
            if (bottomBar != null) {
                Surface(color = InShapeTheme.colors.background) {
                    Box(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(
                            start = Dimens.screenPadding,
                            end = Dimens.screenPadding,
                            top = Dimens.itemGap,
                            bottom = Dimens.inner
                        )
                    ) {
                        bottomBar()
                    }
                }
            }
        }
    }
}

@Composable
fun ResponsiveRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall),
        maxItemsInEachRow = if (LocalDensity.current.fontScale >=
            1.3f
        ) {
            1
        } else {
            Int.MAX_VALUE
        },
        content = content
    )
}

@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    if (LocalDensity.current.fontScale >=
        1.3f
    ) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            trailing?.invoke()
        }
    } else {
        Row(
            modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            trailing?.invoke()
        }
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    quiet: Boolean = false
) {
    ResponsiveRow(modifier.padding(top = Dimens.inner)) {
        Text(
            text,
            Modifier.weight(1f).align(Alignment.CenterVertically),
            style = if (quiet) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium,
            color = if (quiet) InShapeTheme.colors.textSecondary else InShapeTheme.colors.text
        )
        if (action != null && onAction != null) TextAction(action, onAction)
    }
}

@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(top = Dimens.inner, start = Dimens.innerTiny),
        style = MaterialTheme.typography.labelSmall,
        color = InShapeTheme.colors.textSecondary
    )
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = InShapeTheme.colors.textSecondary
        )
        if (action != null) OutlineButton(action, onAction)
    }
}
