package com.emmah.inshape.media

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.room.withTransaction
import com.emmah.inshape.data.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PhotoStore(private val context: Context, private val db: JournalDatabase, val root: File = File(context.filesDir, "photos")) {
    val assetLock = Mutex()
    suspend fun importPhoto(exerciseId: String, uri: Uri): PhotoAsset = withContext(Dispatchers.IO) { assetLock.withLock { import(exerciseId, uri, null) } }
    suspend fun replacePhoto(assetId: String, uri: Uri): PhotoAsset = withContext(Dispatchers.IO) { assetLock.withLock {
        val previous = db.journal().photos().first { it.id == assetId }
        import(previous.exerciseId, uri, previous)
    } }
    private suspend fun import(exerciseId: String, uri: Uri, previous: PhotoAsset?): PhotoAsset {
        check(root.isDirectory || root.mkdirs()) { "Could not create photo storage" }
        val token = newId(); val temporary = File(root, ".import-$token")
        var owned: File? = null
        var committed = false
        try {
            var length = 0L; val digest = MessageDigest.getInstance("SHA-256")
            (context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the selected photo")).use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer); if (read < 0) break
                        length += read; require(length <= MAX_PHOTO_BYTES) { "Choose a photo smaller than 32 MB" }
                        output.write(buffer, 0, read); digest.update(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000) { "Choose a supported, readable photo" }
            val extension = when (bounds.outMimeType) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "image/heif", "image/heic" -> "heic"; "image/gif" -> "gif"; else -> throw IllegalArgumentException("Unsupported image format") }
            decode(temporary, 512)?.recycle() ?: throw IllegalArgumentException("Could not decode this photo")
            owned = File(root, "$token.$extension")
            check(temporary.renameTo(owned)) { "Could not save photo" }
            val asset = PhotoAsset(previous?.id ?: newId(), exerciseId, owned.name, bounds.outMimeType, length,
                digest.digest().joinToString("") { "%02x".format(it) }, previous?.position ?: db.journal().photos().count { it.exerciseId == exerciseId })
            db.withTransaction { db.journal().put(asset) }
            committed = true
            previous?.let { file(it).delete() }
            return asset
        } finally {
            temporary.delete()
            if (!committed) owned?.delete()
        }
    }
    suspend fun removePhoto(assetId: String) = withContext(Dispatchers.IO) { assetLock.withLock {
        val asset = db.journal().photos().firstOrNull { it.id == assetId } ?: return@withLock
        db.journal().deletePhoto(assetId)
        if (db.journal().photos().none { it.path == asset.path }) file(asset).delete()
    } }
    fun file(asset: PhotoAsset): File {
        require(!File(asset.path).isAbsolute && asset.path.split('/', '\\').none { it == ".." })
        return File(root, asset.path).canonicalFile.also { require(it.path.startsWith(root.canonicalPath + File.separator)) { "Invalid photo path" } }
    }
    suspend fun thumbnail(asset: PhotoAsset, maxSize: Int = 1024): Bitmap? = withContext(Dispatchers.IO) { decode(file(asset), maxSize) }
    private fun decode(file: File, maxSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, options)
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > maxSize) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        val orientation = runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }
    companion object { const val MAX_PHOTO_BYTES = 32L * 1024 * 1024 }
}
