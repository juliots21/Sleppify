package com.example.sleppify.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.text.TextUtils
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object YouTubeImageProcessor {
    private const val TAG = "YouTubeImageProcessor"

    /**
     * Minimum decode size (one side) before running [smartCrop]. Tiny bitmaps (e.g. 64×64 list
     * thumbs) make margin detection unreliable so the row art no longer matches the full player.
     */
    private const val MIN_DECODE_PX_FOR_SMART_CROP = 320

    /**
     * Glide [.override] width/height for a view that will display at [displayPx] on one axis,
     * when [YouTubeCropTransformation] / [smartCrop] will run. Keeps bucketing for cache hits but
     * never decodes smaller than [MIN_DECODE_PX_FOR_SMART_CROP] for consistent crops.
     */
    @JvmStatic
    fun decodeDimensionForSmartCrop(displayPx: Int): Int {
        val safe = max(1, displayPx)
        val bucketed = max(64, ((safe + 63) / 64) * 64)
        return max(bucketed, MIN_DECODE_PX_FOR_SMART_CROP)
    }

    @JvmStatic
    fun smartCrop(source: Bitmap?): Bitmap? {
        if (source == null) return null

        val width = source.width
        val height = source.height
        if (width < 50 || height < 50) return source

        var cropTop = 0
        var cropBottom = height
        var cropLeft = 0
        var cropRight = width

        // 1. Detect Top Margin (checking more of the image and using 7 points)
        for (y in 2 until height / 2 step 2) {
            if (!isRowUniform(source, y)) {
                cropTop = maxOf(0, y - 2)
                break
            }
        }

        // 2. Detect Bottom Margin
        for (y in height - 3 downTo height / 2 step 2) {
            if (!isRowUniform(source, y)) {
                cropBottom = minOf(height, y + 2)
                break
            }
        }

        // 3. Detect Left Margin
        for (x in 2 until width / 2 step 2) {
            if (!isColumnUniform(source, x)) {
                cropLeft = maxOf(0, x - 2)
                break
            }
        }

        // 4. Detect Right Margin
        for (x in width - 3 downTo width / 2 step 2) {
            if (!isColumnUniform(source, x)) {
                cropRight = minOf(width, x + 2)
                break
            }
        }

        // Ensure we don't crop into oblivion
        val finalWidth = cropRight - cropLeft
        val finalHeight = cropBottom - cropTop

        if (finalWidth < width / 5 || finalHeight < height / 5) {
            return source
        }

        return try {
            val content = Bitmap.createBitmap(source, cropLeft, cropTop, finalWidth, finalHeight)
            val aspectRatio = finalWidth.toFloat() / finalHeight

            // Already ~square: use as-is.
            if (abs(aspectRatio - 1.0f) < 0.05f) {
                return content
            }

            if (finalWidth > finalHeight) {
                // Wide image: pad top/bottom to complete 1:1, preserving full width
                val side = finalWidth
                val bgColor = sampleMarginColor(source, cropTop, cropLeft, finalWidth, height)
                val padded = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(padded)
                canvas.drawColor(bgColor)
                val offsetY = (side - finalHeight) / 2
                canvas.drawBitmap(content, 0f, offsetY.toFloat(), null)
                padded
            } else {
                // Tall image: center-crop to square
                val side = finalWidth
                val cropY = max(0, (finalHeight - side) / 2)
                val squared = Bitmap.createBitmap(content, 0, cropY, side, side)
                squared
            }
        } catch (e: Exception) {
            Log.e(TAG, "smartCrop: failed", e)
            source
        }
    }

    private fun isRowUniform(bmp: Bitmap, y: Int): Boolean {
        val w = bmp.width
        // Use 7 points for better coverage
        val points = intArrayOf(w/10, 2*w/10, 4*w/10, 5*w/10, 6*w/10, 8*w/10, 9*w/10)
        val firstColor = bmp.getPixel(points[0], y)
        
        for (i in 1 until points.size) {
            if (!isColorSimilar(firstColor, bmp.getPixel(points[i], y))) return false
        }
        return true
    }

    private fun isColumnUniform(bmp: Bitmap, x: Int): Boolean {
        val h = bmp.height
        val points = intArrayOf(h/10, 2*h/10, 4*h/10, 5*h/10, 6*h/10, 8*h/10, 9*h/10)
        val firstColor = bmp.getPixel(x, points[0])
        
        for (i in 1 until points.size) {
            if (!isColorSimilar(firstColor, bmp.getPixel(x, points[i]))) return false
        }
        return true
    }

    private fun isColorSimilar(c1: Int, c2: Int): Boolean {
        // Reduced threshold for tighter "uniformity" (better margin detection)
        val threshold = 18 
        return Math.abs(Color.red(c1) - Color.red(c2)) <= threshold &&
               Math.abs(Color.green(c1) - Color.green(c2)) <= threshold &&
               Math.abs(Color.blue(c1) - Color.blue(c2)) <= threshold
    }

    /**
     * Samples the dominant margin color from the original image edges.
     * Used for padding wide images to 1:1 without introducing mismatched backgrounds.
     */
    private fun sampleMarginColor(source: Bitmap, cropTop: Int, cropLeft: Int, contentWidth: Int, sourceHeight: Int): Int {
        return try {
            val samples = mutableListOf<Int>()
            val midX = cropLeft + contentWidth / 2
            val safeX = midX.coerceIn(0, source.width - 1)
            // Sample from the top and bottom margin areas
            if (cropTop > 2) {
                samples.add(source.getPixel(safeX, 1))
                samples.add(source.getPixel(safeX, cropTop / 2))
            }
            val bottomMarginStart = cropTop + (sourceHeight - cropTop)
            if (bottomMarginStart < source.height - 2) {
                samples.add(source.getPixel(safeX, source.height - 2))
            }
            // Also sample corners for resilience
            if (source.width > 4 && source.height > 4) {
                samples.add(source.getPixel(2, 2))
                samples.add(source.getPixel(source.width - 3, 2))
            }
            if (samples.isEmpty()) Color.BLACK
            else samples.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: Color.BLACK
        } catch (e: Exception) {
            Color.BLACK
        }
    }

    @JvmStatic
    fun shouldProcess(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val lower = url.lowercase()
        return lower.contains("ytimg.com") || 
               lower.contains("googleusercontent.com") || 
               lower.contains("youtube.com/vi/")
    }
}
