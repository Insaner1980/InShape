package com.emmah.inshape.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal enum class ArtworkWash(private val middle: Float, private val light: List<Color>, private val dark: List<Color>) {
    Routine(
        .48f,
        listOf(Color(0xFFFFFEFA), Color(0xFFF6F5ED), Color(0xFFDCE5D5)),
        listOf(Color(0xFF141817), Color(0xFF1A2422), Color(0xFF263B35))
    ),
    Active(
        .55f,
        listOf(Color(0xFFFFFFFF), Color(0xFFFAFBF8), Color(0xFFE6EEE2)),
        listOf(Color(0xFF121617), Color(0xFF162326), Color(0xFF17363A))
    ),
    Completed(
        .55f,
        listOf(Color(0xFFFFFEFB), Color(0xFFF3F4ED), Color(0xFFDAE4DB)),
        listOf(Color(0xFF0B0B0B), Color(0xFF101716), Color(0xFF203630))
    ),
    Paper(
        .60f,
        listOf(Color(0xFFFFFCF5), Color(0xFFF6F2E8), Color(0xFFE2E6D6)),
        listOf(Color(0xFF191B18), Color(0xFF20251F), Color(0xFF2B342B))
    ),
    Equipment(
        .55f,
        listOf(Color(0xFFF8F9F6), Color(0xFFEDF1EC), Color(0xFFDFE7E1)),
        listOf(Color(0xFF161A19), Color(0xFF1E2929), Color(0xFF294143))
    );

    @Composable
    fun brush(useDark: Boolean = InShapeTheme.colors.isDark): Brush {
        val colors = if (useDark) dark else light
        return if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
            Brush.horizontalGradient(0f to colors[0], middle to colors[1], 1f to colors[2])
        } else {
            Brush.horizontalGradient(0f to colors[2], (1f - middle) to colors[1], 1f to colors[0])
        }
    }
}

@Composable
internal fun BoxWithConstraintsScope.hasArtworkColumns(): Boolean = maxWidth >= 300.dp && LocalDensity.current.fontScale < 1.3f
