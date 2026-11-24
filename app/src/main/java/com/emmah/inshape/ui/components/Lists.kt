// This file is named for its functions or UI role; its accompanying model is secondary.
@file:Suppress("MatchingDeclarationName")

package com.emmah.inshape.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import com.emmah.inshape.R
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme

class ListCardScope {
    internal val items = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

@Composable
fun ListCard(modifier: Modifier = Modifier, content: ListCardScope.() -> Unit) {
    val rows: List<@Composable () -> Unit> = ListCardScope().apply(content).items
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = InShapeTheme.colors.divider)
            row()
        }
    }
}

// Optional layout and content slots keep call sites explicit.
@Suppress("LongParameterList")
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    trailingValue: (@Composable () -> Unit)? = null,
    showChevron: Boolean = trailing == null && trailingValue == null && onClick != null,
    details: (@Composable () -> Unit)? = null,
    titleColor: Color = Color.Unspecified
) {
    val large = LocalDensity.current.fontScale >= 1.3f
    val rowModifier = modifier.fillMaxWidth().heightIn(
        min = if (subtitle ==
            null
        ) {
            Dimens.rowMin
        } else {
            Dimens.rowMinTwoLine
        }
    )
        .then(
            if (onClick ==
                null
            ) {
                Modifier
            } else {
                Modifier.clickable(enabled = enabled, onClick = onClick)
            }
        )
        .padding(vertical = Dimens.rowPadding)
    Row(
        rowModifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)) {
            ListRowTitle(title, titleStyle, titleColor, large)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = InShapeTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            details?.invoke()
            if (large) trailingValue?.invoke()
        }
        if (!large) trailingValue?.invoke()
        trailing?.invoke()
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                null,
                Modifier.size(Dimens.buttonIcon),
                tint = InShapeTheme.colors.textTertiary
            )
        }
    }
}

@Composable
private fun ListRowTitle(title: String, style: TextStyle, color: Color, large: Boolean) {
    val paragraph = style.copy(localeList = LocaleList("fi-FI"), hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)
    val body = MaterialTheme.typography.bodyMedium
    if (large && paragraph.fontSize > body.fontSize) {
        BoxWithConstraints {
            val measurer = rememberTextMeasurer()
            val hasOversizeWord = title.split(' ').any { word ->
                measurer.measure(word, paragraph, softWrap = false).size.width > constraints.maxWidth
            }
            val fitted = if (hasOversizeWord) paragraph.copy(fontSize = body.fontSize, lineHeight = body.lineHeight) else paragraph
            Text(title, style = fitted, color = color)
        }
    } else {
        Text(title, style = paragraph, color = color)
    }
}

@Composable
fun InitialsTile(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier.sizeIn(minWidth = Dimens.check, minHeight = Dimens.check),
        shape = InShapeShapes.field,
        color = InShapeTheme.colors.surface
    ) {
        Box(Modifier.padding(Dimens.innerTiny), contentAlignment = Alignment.Center) {
            Text(
                text.split(Regex("\\s+")).filter {
                    it.isNotEmpty()
                }.take(2).joinToString("") {
                    it.first().uppercase()
                },
                style = MaterialTheme.typography.labelSmall,
                color = InShapeTheme.colors.textSecondary
            )
        }
    }
}

@Composable
fun DisclosureRow(label: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = InShapeTheme.colors
    val expansionStatus = stringResource(if (expanded) R.string.expanded else R.string.collapsed)
    Row(
        modifier.fillMaxWidth().heightIn(min = Dimens.rowMin)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = expansionStatus },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = colors.text)
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            null,
            Modifier.rotate(if (expanded) 180f else 0f),
            tint = colors.textSecondary
        )
    }
}
