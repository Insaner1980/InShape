package com.emmah.inshape.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.emmah.inshape.ui.theme.hasArtworkColumns

@Composable
internal fun ArtworkHeader(
    @DrawableRes artwork: Int?,
    artWidth: Dp,
    artHeight: Dp,
    modifier: Modifier = Modifier,
    cropEnd: Boolean = false,
    mirror: Boolean = false,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = minOf(artWidth, maxWidth * .4f, maxWidth - 184.dp)
        val showArtwork = hasArtworkColumns()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) { content() }
            if (artwork != null && showArtwork) {
                val flip = mirror && LocalLayoutDirection.current == LayoutDirection.Rtl
                Image(
                    painterResource(artwork),
                    null,
                    Modifier.width(width).height(artHeight).scale(if (flip) -1f else 1f, 1f),
                    alignment = if (cropEnd) AbsoluteAlignment.CenterRight else Alignment.BottomEnd,
                    contentScale = if (cropEnd) ContentScale.Crop else ContentScale.Fit
                )
            }
        }
    }
}
