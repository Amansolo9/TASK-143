package com.learnmart.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Image memory manager with downsampling and bounded LRU cache.
 * Keeps peak image memory under 20MB to prevent OOM.
 *
 * Currently the app uses only vector Material Icons and does not load
 * user-provided bitmap images. This utility is provided for when catalog
 * or material images are added in the future.
 */
@Singleton
class ImageMemoryManager @Inject constructor() {

    companion object {
        const val MAX_CACHE_SIZE_BYTES = 20 * 1024 * 1024 // 20MB budget
        const val DEFAULT_MAX_WIDTH = 1024
        const val DEFAULT_MAX_HEIGHT = 1024
    }

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    fun getCachedBitmap(key: String): Bitmap? = cache.get(key)

    fun putBitmap(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun evict(key: String) {
        cache.remove(key)
    }

    fun clearCache() {
        cache.evictAll()
    }

    fun currentCacheSizeBytes(): Int = cache.size()

    fun maxCacheSizeBytes(): Int = cache.maxSize()

    /**
     * Decode a byte array into a downsampled bitmap that fits within
     * the given max dimensions. Runs on IO dispatcher.
     */
    suspend fun decodeSampled(
        data: ByteArray,
        maxWidth: Int = DEFAULT_MAX_WIDTH,
        maxHeight: Int = DEFAULT_MAX_HEIGHT
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // First pass: decode bounds only
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)

            // Calculate sample size
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false

            BitmapFactory.decodeByteArray(data, 0, data.size, options)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decode and cache a bitmap. Returns cached version if available.
     */
    suspend fun loadAndCache(
        key: String,
        data: ByteArray,
        maxWidth: Int = DEFAULT_MAX_WIDTH,
        maxHeight: Int = DEFAULT_MAX_HEIGHT
    ): Bitmap? {
        val cached = getCachedBitmap(key)
        if (cached != null) return cached

        val bitmap = decodeSampled(data, maxWidth, maxHeight) ?: return null
        putBitmap(key, bitmap)
        return bitmap
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
