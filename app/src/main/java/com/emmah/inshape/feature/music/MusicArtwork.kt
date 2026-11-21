package com.emmah.inshape.feature.music

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import com.emmah.inshape.R
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

enum class PlaylistArtworkFamily(@DrawableRes val square: Int, @DrawableRes val wide: Int? = null) {
    NightDrive(R.drawable.art_music_night_drive_cover, R.drawable.art_music_night_drive),
    HeavyRotation(R.drawable.art_music_heavy_rotation),
    SlowMotion(R.drawable.art_music_slow_motion)
}

internal fun playlistArtworkFamily(id: String): PlaylistArtworkFamily =
    PlaylistArtworkFamily.entries[Math.floorMod(id.hashCode(), PlaylistArtworkFamily.entries.size)]

internal sealed interface MusicCover {
    data object Loading : MusicCover
    data class Personal(val bitmap: Bitmap) : MusicCover
    data class Bundled(@DrawableRes val resource: Int, @DrawableRes val wide: Int? = null) : MusicCover
}

private data class Art(val cover: MusicCover.Personal?)
private data class ArtKey(val uri: String, val revision: Long)

private val artCache = object : LruCache<ArtKey, Art>(6 * 1024 * 1024) {
    override fun sizeOf(key: ArtKey, value: Art) = value.cover?.bitmap?.allocationByteCount ?: 1024
}
private val decoders = Semaphore(2)
private val generation = MutableStateFlow(0L)
private val monochrome = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private val grooves = MusicCover.Bundled(R.drawable.art_music_record_grooves)

@Composable
internal fun playlistCover(playlist: SavedPlaylist): MusicCover {
    if (playlist.tracks.isEmpty()) return grooves
    val family = playlist.fixtureArtwork ?: playlistArtworkFamily(playlist.id)
    val fallback = MusicCover.Bundled(family.square, family.wide)
    if (playlist.fixtureArtwork != null) return fallback
    return resolvedCover(playlist.tracks.filter { it.available }.map { it.uri }, fallback)
}

@Composable
internal fun resolvedCover(
    uris: List<String>,
    fallback: MusicCover.Bundled,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): MusicCover {
    if (uris.isEmpty()) return fallback
    val context = LocalContext.current.applicationContext
    val revision by generation.collectAsState()
    return key(uris, fallback, context, revision, ioDispatcher) {
        val artwork by produceState<MusicCover>(MusicCover.Loading) {
            value = withContext(ioDispatcher) {
                uris.firstNotNullOfOrNull { uri ->
                    decoders.withPermit {
                        val cacheKey = ArtKey(uri, revision)
                        val cached = artCache[cacheKey]
                        if (cached != null) {
                            cached.cover
                        } else {
                            val cover = readArt(context, uri.toUri())?.let { MusicCover.Personal(it) }
                            artCache.put(cacheKey, Art(cover))
                            cover
                        }
                    }
                } ?: fallback
            }
        }
        artwork
    }
}

@Composable
internal fun MusicArtwork(uri: String?, size: Dp, modifier: Modifier = Modifier) {
    val packageName = LocalContext.current.packageName
    val bundled = remember(uri, packageName) {
        val resource = uri?.toUri()
        if (resource?.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE && resource.authority == packageName) {
            val id = resource.lastPathSegment?.toIntOrNull()
            PlaylistArtworkFamily.entries.firstOrNull { it.square == id }?.let { MusicCover.Bundled(it.square) }
        } else {
            null
        }
    }
    val cover = bundled ?: resolvedCover(listOfNotNull(uri), grooves)
    MusicArtwork(cover, size, modifier)
}

@Composable
internal fun MusicArtwork(cover: MusicCover, size: Dp, modifier: Modifier = Modifier) {
    val tile = modifier.size(size).clip(RoundedCornerShape(Dimens.innerSmall))
    when (cover) {
        MusicCover.Loading -> Box(tile.background(InShapeTheme.colors.surface), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MusicNote, null, Modifier.size(Dimens.icon), tint = InShapeTheme.colors.textSecondary)
        }

        is MusicCover.Bundled -> Image(painterResource(cover.resource), null, tile, contentScale = ContentScale.Fit)

        is MusicCover.Personal -> Image(
            cover.bitmap.asImageBitmap(),
            null,
            tile,
            contentScale = ContentScale.Crop,
            colorFilter = monochrome
        )
    }
}

private fun readArt(context: Context, uri: Uri): Bitmap? = try {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.embeddedPicture?.takeIf { it.size <= 8 * 1024 * 1024 }?.let(::decodeCover)
    } finally {
        retriever.release()
    }
} catch (_: IOException) {
    null
} catch (_: RuntimeException) {
    null
}

private fun decodeCover(bytes: ByteArray): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val edge = maxOf(options.outWidth, options.outHeight)
    options.inSampleSize = 1
    while (edge / options.inSampleSize > 768) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

internal fun clearMusicArtwork() {
    artCache.evictAll()
    generation.update { it + 1 }
}
