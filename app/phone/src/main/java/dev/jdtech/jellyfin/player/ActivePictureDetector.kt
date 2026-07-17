package dev.jdtech.jellyfin.player

import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max

/** Finds a stable, edge-connected active picture inside a rendered video frame. */
object ActivePictureDetector {
    private const val MIN_LUMA = 24
    private const val BACKGROUND_LUMA_MARGIN = 12
    // Prefer retaining a few genuine edge pixels over mistaking dark picture detail for a bar.
    private const val MIN_ACTIVE_LINE_FRACTION = 0.02f
    private const val MIN_ACTIVE_PIXEL_FRACTION = 0.01f
    private const val REQUIRED_ADJACENT_LINES = 2

    fun detect(pixels: IntArray, width: Int, height: Int): Rect? {
        if (width < 8 || height < 8 || pixels.size < width * height) return null

        val backgroundLuma =
            listOf(
                    luma(pixels[0]),
                    luma(pixels[width - 1]),
                    luma(pixels[(height - 1) * width]),
                    luma(pixels[height * width - 1]),
                )
                .sorted()
                .let { (it[1] + it[2]) / 2 }
        val threshold = max(MIN_LUMA, backgroundLuma + BACKGROUND_LUMA_MARGIN)

        val columnCounts = IntArray(width)
        val rowCounts = IntArray(height)
        var activePixels = 0
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                if (luma(pixels[rowOffset + x]) > threshold) {
                    columnCounts[x]++
                    rowCounts[y]++
                    activePixels++
                }
            }
        }

        if (activePixels < width * height * MIN_ACTIVE_PIXEL_FRACTION) return null

        val minimumColumnPixels = max(2, (height * MIN_ACTIVE_LINE_FRACTION).toInt())
        val minimumRowPixels = max(2, (width * MIN_ACTIVE_LINE_FRACTION).toInt())
        val left = firstStableLine(columnCounts, minimumColumnPixels) ?: return null
        val right = lastStableLine(columnCounts, minimumColumnPixels) ?: return null
        val top = firstStableLine(rowCounts, minimumRowPixels) ?: return null
        val bottom = lastStableLine(rowCounts, minimumRowPixels) ?: return null

        if (right <= left || bottom <= top) return null
        return Rect(left, top, right + 1, bottom + 1)
    }

    private fun firstStableLine(counts: IntArray, minimum: Int): Int? {
        for (index in 0..counts.size - REQUIRED_ADJACENT_LINES) {
            if ((0 until REQUIRED_ADJACENT_LINES).all { counts[index + it] >= minimum }) {
                return index
            }
        }
        return null
    }

    private fun lastStableLine(counts: IntArray, minimum: Int): Int? {
        for (index in counts.lastIndex downTo REQUIRED_ADJACENT_LINES - 1) {
            if ((0 until REQUIRED_ADJACENT_LINES).all { counts[index - it] >= minimum }) {
                return index
            }
        }
        return null
    }

    private fun luma(color: Int): Int =
        (Color.red(color) * 54 + Color.green(color) * 183 + Color.blue(color) * 19) shr 8
}
