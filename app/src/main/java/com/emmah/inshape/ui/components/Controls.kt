package com.emmah.inshape.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.emmah.inshape.R
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType

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
    wash: Brush? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val body: @Composable () -> Unit = {
        Column(
            Modifier.then(if (wash == null) Modifier else Modifier.background(wash)).padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(gap),
            content = content
        )
    }
    val card = modifier.fillMaxWidth()
    val colors = InShapeTheme.colors
    val shape = if (inset) InShapeShapes.field else InShapeShapes.card
    val border = if (inset || wash != null || color == colors.ink) null else BorderStroke(Dimens.line, borderColor)
    val background = if (inset) colors.surface else color
    if (onClick == null) {
        Surface(card, shape = shape, border = border, color = background, contentColor = colors.text, content = body)
    } else {
        Surface(onClick, card, shape = shape, border = border, color = background, contentColor = colors.text, content = body)
    }
}

// Pages share the navigation host's snackbar queue so pending messages can follow a route change.
@Suppress("CompositionLocalAllowlist")
val LocalPageSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }

@Composable
fun Page(
    modifier: Modifier = Modifier,
    scrollViewportModifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    topBar: (@Composable () -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Dimens.maxPageWidth).fillMaxWidth().imePadding()) {
            topBar?.invoke()
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().then(scrollViewportModifier)) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scrollState).padding(
                        start = Dimens.screenPadding,
                        end = Dimens.screenPadding,
                        top = Dimens.screenTop,
                        bottom = Dimens.sectionGap
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.itemGap),
                    content = content
                )
            }
            LocalPageSnackbar.current?.let { state ->
                val colors = InShapeTheme.colors
                SnackbarHost(state) { entry ->
                    Snackbar(entry, containerColor = colors.ink, contentColor = colors.onInk, actionColor = colors.onInk)
                }
            }
            if (bottomBar != null) BottomActionBar(bottomBar)
        }
    }
}

@Composable
internal fun BottomActionBar(content: @Composable () -> Unit) {
    val colors = InShapeTheme.colors
    Surface(color = colors.background) {
        Column {
            HorizontalDivider(color = colors.divider, thickness = Dimens.line)
            Box(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(
                    start = Dimens.screenPadding,
                    end = Dimens.screenPadding,
                    top = Dimens.itemGap,
                    bottom = Dimens.inner
                )
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushedHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    trailingText: Boolean = false,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = InShapeTheme.colors
    TopAppBar(
        title = titleContent ?: { if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleLarge) },
        modifier = modifier,
        navigationIcon = {
            HeaderIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onBack)
        },
        actions = {
            actions()
            if (trailingText) Spacer(Modifier.width(Dimens.headerActionEnd))
        },
        expandedHeight = if (LocalDensity.current.fontScale >= 1.3f && (title.isNotEmpty() || titleContent != null)) Dimens.topBarLarge else Dimens.rowMinTwoLine,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.background,
            scrolledContainerColor = colors.background,
            navigationIconContentColor = colors.text
        )
    )
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    BoxWithConstraints(modifier) {
        val valueModifier = if (LocalDensity.current.fontScale >= 1.3f) {
            Modifier
        } else {
            Modifier.widthIn(max = maxWidth * 0.65f)
        }
        ListRow(
            label,
            titleStyle = MaterialTheme.typography.bodyLarge,
            trailingValue = {
                Text(
                    value,
                    valueModifier,
                    style = if (numeric) InShapeType.numeric else MaterialTheme.typography.bodyLarge,
                    color = if (onClick == null) InShapeTheme.colors.textSecondary else InShapeTheme.colors.text
                )
            },
            onClick = onClick,
            enabled = enabled
        )
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
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    titleStyle: TextStyle = MaterialTheme.typography.headlineLarge,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = titleStyle,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    ResponsiveRow(modifier.padding(top = Dimens.sectionGap - Dimens.itemGap)) {
        Text(
            text,
            Modifier.weight(1f).align(Alignment.CenterVertically),
            style = MaterialTheme.typography.titleLarge,
            color = InShapeTheme.colors.text
        )
        trailing?.invoke()
    }
}

@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(top = Dimens.innerSmall),
        style = MaterialTheme.typography.labelSmall,
        color = InShapeTheme.colors.textSecondary
    )
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = InShapeTheme.colors.textSecondary
        )
        if (action != null) OutlineButton(action, onAction, icon = icon)
    }
}
