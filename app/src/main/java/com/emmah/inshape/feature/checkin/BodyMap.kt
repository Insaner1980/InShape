package com.emmah.inshape.feature.checkin

import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.ui.components.SelectField
import com.emmah.inshape.ui.theme.*

data class BodyRegion(val id: String, val label: String)
val bodyRegions: List<BodyRegion> = listOf("neck", "upper_back", "lower_back", "chest", "abdomen").map { BodyRegion(it, it.replace('_', ' ').replaceFirstChar(Char::titlecase)) } +
    listOf("shoulder", "upper_arm", "elbow", "forearm_wrist", "hip", "thigh", "knee", "calf_shin", "ankle_foot").flatMap { part -> listOf("left", "right").map { side -> BodyRegion("${side}_$part", "$side ${part.replace('_', ' ')}".replaceFirstChar(Char::titlecase)) } }

data class RegionShape(val id: String, val path: Path)
fun bodyShapes(back: Boolean): List<RegionShape> = buildList {
    fun shape(id: String, left: Float, top: Float, right: Float, bottom: Float) { add(RegionShape(id, Path().apply { addRoundRect(RectF(left, top, right, bottom), 3f, 3f, Path.Direction.CW) })) }
    shape("neck", 45f, 30f, 55f, 44f)
    shape(if (back) "upper_back" else "chest", 36f, 44f, 64f, 73f)
    shape(if (back) "lower_back" else "abdomen", 36f, 73f, 64f, 104f)
    listOf(false, true).forEach { screenRight ->
        val side = if (screenRight == back) "right" else "left"
        val arm = if (screenRight) 66f else 25f
        val leg = if (screenRight) 51f else 36f
        shape("${side}_shoulder", arm, 44f, arm + 10, 57f)
        shape("${side}_upper_arm", arm, 58f, arm + 9, 82f)
        shape("${side}_elbow", arm, 83f, arm + 9, 93f)
        shape("${side}_forearm_wrist", arm, 94f, arm + 9, 120f)
        shape("${side}_hip", leg, 105f, leg + 13, 123f)
        shape("${side}_thigh", leg, 124f, leg + 13, 160f)
        shape("${side}_knee", leg, 161f, leg + 13, 176f)
        shape("${side}_calf_shin", leg, 177f, leg + 13, 207f)
        shape("${side}_ankle_foot", leg, 208f, leg + 13, 224f)
    }
}
fun bodyRegionAt(x: Float, y: Float, back: Boolean): String? = bodyShapes(back).firstOrNull { shape ->
    Region().apply { setPath(shape.path, Region(0, 0, 100, 240)) }.contains(x.toInt(), y.toInt())
}?.id

@Composable
fun BodyMap(back: Boolean, observations: List<BodyObservation>, onSelect: (String) -> Unit) {
    val colors = InShapeTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = Dimens.inner), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
    Canvas(Modifier.width(Dimens.bodyWidth).height(Dimens.bodyHeight).pointerInput(back) {
        detectTapGestures { point ->
            val scale = minOf(size.width / 100f, size.height / 240f)
            bodyRegionAt((point.x - (size.width - 100f * scale) / 2f) / scale, point.y / scale, back)?.let(onSelect)
        }
    }) {
        val scale = minOf(size.width / 100f, size.height / 240f)
        withTransform({ translate((size.width - 100f * scale) / 2f, 0f); scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
            drawCircle(colors.surfaceInset, 10f, androidx.compose.ui.geometry.Offset(50f, 20f))
            val silhouette = Path().apply {
                addRoundRect(RectF(45f, 29f, 55f, 47f), 2f, 2f, Path.Direction.CW)
                addRoundRect(RectF(36f, 43f, 64f, 124f), 10f, 10f, Path.Direction.CW)
                addRoundRect(RectF(25f, 44f, 34f, 120f), 4f, 4f, Path.Direction.CW)
                addRoundRect(RectF(66f, 44f, 75f, 120f), 4f, 4f, Path.Direction.CW)
                addRoundRect(RectF(36f, 121f, 49f, 224f), 6f, 6f, Path.Direction.CW)
                addRoundRect(RectF(51f, 121f, 64f, 224f), 6f, 6f, Path.Direction.CW)
            }
            drawPath(silhouette.asComposePath(), colors.surfaceInset)
            drawPath(silhouette.asComposePath(), colors.muted, style = Stroke(.7f))
            bodyShapes(back).forEach { region ->
                val observation = observations.find { it.region == region.id }
                if (observation != null) {
                    drawPath(region.path.asComposePath(), colors.accentContainer)
                    drawPath(region.path.asComposePath(), colors.mark, style = Stroke(.7f))
                    val bounds = RectF().also { region.path.computeBounds(it, true) }
                    if (observation.pain != null) drawCircle(colors.pain, 2f, androidx.compose.ui.geometry.Offset(bounds.centerX() - 2f, bounds.centerY()))
                    if (observation.stiffness != null) drawRect(colors.stiffness, androidx.compose.ui.geometry.Offset(bounds.centerX() + 1f, bounds.centerY() - 2f), androidx.compose.ui.geometry.Size(4f, 4f))
                }
            }
        }
    }
    Column(Modifier.weight(1f).padding(start = Dimens.innerSmall), verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
        Text("Pain, circle", color = colors.pain)
        Text("Stiffness, square", color = colors.stiffness)
        Text("Tap an area to add it. Pain and stiffness are scored separately.", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
    } }
    SelectField("Add body area", "Choose area", bodyRegions.map { it.label }, { label -> onSelect(bodyRegions.first { it.label == label }.id) })
}
