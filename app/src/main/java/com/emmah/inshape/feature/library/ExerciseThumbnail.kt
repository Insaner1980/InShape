package com.emmah.inshape.feature.library

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.PhotoAsset
import com.emmah.inshape.media.PhotoStore
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private data class ThumbnailKey(@DrawableRes val resource: Int, val sample: Int)

private val thumbnails = object : LruCache<ThumbnailKey, Bitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: ThumbnailKey, value: Bitmap): Int = value.allocationByteCount
}
private val decoders = Semaphore(2)

@Composable
internal fun exerciseThumbnail(
    exercise: Exercise,
    photo: PhotoAsset?,
    store: PhotoStore,
    size: Dp,
    showIllustration: Boolean
): (@Composable () -> Unit)? {
    if (photo != null) return { PhotoImage(photo, store, Modifier.size(size).clip(InShapeShapes.field), maxSize = 256) }
    if (!showIllustration) return null
    val artwork = remember(exercise.equipmentName, exercise.name) { equipmentArtwork(exercise.equipmentName, exercise.name) }
    return artwork?.let { { EquipmentThumbnail(it.drawable, size) } }
}

@Composable
private fun EquipmentThumbnail(@DrawableRes resource: Int, size: Dp) {
    val resources = LocalResources.current
    val edge = with(LocalDensity.current) { (size - Dimens.innerSmall).roundToPx() }.coerceAtLeast(1)
    var visible by remember { mutableStateOf(false) }
    Box(Modifier.size(size).onVisibilityChanged(minFractionVisible = .1f) { visible = it }) {
        if (visible) {
            key(resources, resource, edge) {
                val bitmap by produceState<Bitmap?>(null) {
                    value = withContext(Dispatchers.IO) { decoders.withPermit { thumbnail(resources, resource, edge) } }
                }
                bitmap?.let {
                    Image(it.asImageBitmap(), null, Modifier.size(size).padding(Dimens.innerTiny), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

private fun thumbnail(resources: Resources, @DrawableRes resource: Int, edge: Int): Bitmap? {
    // The supplied exercise cutouts all have a 768 px edge.
    var sample = 1
    while (768 / (sample * 2) >= edge) sample *= 2
    val key = ThumbnailKey(resource, sample)
    return thumbnails.get(key) ?: BitmapFactory.decodeResource(
        resources,
        resource,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
        }
    )?.also { thumbnails.put(key, it) }
}
