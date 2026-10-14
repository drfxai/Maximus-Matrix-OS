package ai.drfx.maximus.matrixai.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Size-bounded, sampled image input, with platform codec support instead of extension assumptions.
 * JPEG, PNG, WebP, GIF (first frame) and HEIC/HEIF where the platform decoder supports them.
 * An unsupported TIFF/SVG/codec is reported, not silently uploaded as another format.
 */
object ImageInputPipeline {
    private const val MAX_SOURCE_BYTES = 60L * 1024L * 1024L
    private const val MAX_EDGE = 2048
    private const val MAX_UPLOAD_BYTES = 4 * 1024 * 1024

    data class Prepared(val name: String, val jpeg: ByteArray, val width: Int, val height: Int)

    fun decode(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !cursor.isNull(idx)) require(cursor.getLong(idx) <= MAX_SOURCE_BYTES) {
                    "Image file is larger than the 60 MB input limit."
                }
            }
        }
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val size = info.size
                require(size.width > 0 && size.height > 0 &&
                    size.width <= 100_000 && size.height <= 100_000) { "Invalid image dimensions." }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // Sampling before allocating prevents full-resolution bitmap OOMs.
                var factor = 1
                while (size.width / factor > MAX_EDGE || size.height / factor > MAX_EDGE) factor *= 2
                decoder.setTargetSampleSize(factor)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
                bounds.outWidth <= 100_000 && bounds.outHeight <= 100_000) {
                "Unsupported or invalid image format."
            }
            var sample = 1
            while (bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("The Android image decoder does not support this file.")
            val orientation = runCatching {
                resolver.openInputStream(uri)?.use { stream ->
                    android.media.ExifInterface(stream).getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL)
                } ?: android.media.ExifInterface.ORIENTATION_NORMAL
            }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) decoded else Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height,
                android.graphics.Matrix().apply { postRotate(degrees) }, true
            ).also { if (it !== decoded) decoded.recycle() }
        }
        require(bitmap.width > 0 && bitmap.height > 0)
        val scale = max(bitmap.width.toFloat() / MAX_EDGE, bitmap.height.toFloat() / MAX_EDGE)
        if (scale <= 1f) return bitmap
        return Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width / scale).toInt()),
            max(1, (bitmap.height / scale).toInt()), true).also { if (it !== bitmap) bitmap.recycle() }
    }

    fun prepare(context: Context, uri: Uri, originalName: String = "image"): Prepared {
        val bitmap = decode(context, uri)
        try {
            var quality = 87
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                require(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out))
                bytes = out.toByteArray()
                quality -= 12
            } while (bytes.size > MAX_UPLOAD_BYTES && quality >= 45)
            require(bytes.size <= MAX_UPLOAD_BYTES) { "Image cannot be compressed under the upload limit." }
            return Prepared(originalName.substringBeforeLast('.').take(100) + ".jpg",
                bytes, bitmap.width, bitmap.height)
        } finally { bitmap.recycle() }
    }
}
