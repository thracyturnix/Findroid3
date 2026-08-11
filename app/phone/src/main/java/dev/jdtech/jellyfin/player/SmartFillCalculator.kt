package dev.jdtech.jellyfin.player

import kotlin.math.ln
import kotlin.math.min

/** Pure geometry and acceptance rules for fitting padded video into the usable player area. */
internal object SmartFillCalculator {
    const val MIN_SCALE = 1.025f
    const val MAX_SCALE = 1.5f
    const val MIN_SOURCE_PADDING_FRACTION = 0.03f
    const val SAFETY_MARGIN_FRACTION = 0.005f

    data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float
            get() = right - left

        val height: Float
            get() = bottom - top

        val centerX: Float
            get() = (left + right) / 2f

        val centerY: Float
            get() = (top + bottom) / 2f

        val isValid: Boolean
            get() =
                left.isFinite() &&
                    top.isFinite() &&
                    right.isFinite() &&
                    bottom.isFinite() &&
                    width > 0f &&
                    height > 0f
    }

    data class SampleBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    enum class SkipReason(val description: String, val canRetry: Boolean = false) {
        INVALID_GEOMETRY("invalid geometry"),
        ACTIVE_OUTSIDE_VIDEO("detected picture outside fitted video", canRetry = true),
        NO_SOURCE_PADDING("no encoded padding detected"),
        SCALE_TOO_SMALL("size gain below threshold"),
        SCALE_TOO_LARGE("detected picture would require excessive enlargement", canRetry = true),
    }

    sealed interface Decision {
        data class Apply(
            val scale: Float,
            val zoom: Double,
            val panX: Double,
            val panY: Double,
            val sourcePaddingFraction: Float,
        ) : Decision

        data class Skip(val reason: SkipReason) : Decision
    }

    /**
     * Keeps the outer edge reported by most samples while discarding one-off bright pixels, logos,
     * and subtitles. The safety margin biases the result toward retaining genuine picture pixels.
     */
    fun combineSamples(
        samples: List<SampleBounds>,
        sampleWidth: Int,
        sampleHeight: Int,
    ): SampleBounds? {
        if (samples.isEmpty() || sampleWidth <= 0 || sampleHeight <= 0) return null
        if (samples.any { it.left < 0 || it.top < 0 || it.right > sampleWidth || it.bottom > sampleHeight || it.right <= it.left || it.bottom <= it.top }) {
            return null
        }

        val trimIndex = ((samples.size - 1) * SAMPLE_OUTLIER_FRACTION).toInt()
        val upperIndex = samples.lastIndex - trimIndex
        val marginX = (sampleWidth * SAFETY_MARGIN_FRACTION).coerceAtLeast(1f).toInt()
        val marginY = (sampleHeight * SAFETY_MARGIN_FRACTION).coerceAtLeast(1f).toInt()

        return SampleBounds(
            left =
                (samples.map { it.left }.sorted()[trimIndex] - marginX).coerceAtLeast(0),
            top = (samples.map { it.top }.sorted()[trimIndex] - marginY).coerceAtLeast(0),
            right =
                (samples.map { it.right }.sorted()[upperIndex] + marginX)
                    .coerceAtMost(sampleWidth),
            bottom =
                (samples.map { it.bottom }.sorted()[upperIndex] + marginY)
                    .coerceAtMost(sampleHeight),
        )
    }

    fun calculate(
        surfaceWidth: Int,
        surfaceHeight: Int,
        safeRect: Bounds,
        activeRect: Bounds,
        fittedVideoRect: Bounds,
    ): Decision {
        if (
            surfaceWidth <= 0 ||
                surfaceHeight <= 0 ||
                !safeRect.isValid ||
                !activeRect.isValid ||
                !fittedVideoRect.isValid
        ) {
            return Decision.Skip(SkipReason.INVALID_GEOMETRY)
        }

        val outsideToleranceX = fittedVideoRect.width * ACTIVE_BOUNDS_TOLERANCE_FRACTION
        val outsideToleranceY = fittedVideoRect.height * ACTIVE_BOUNDS_TOLERANCE_FRACTION
        if (
            activeRect.left < fittedVideoRect.left - outsideToleranceX ||
                activeRect.right > fittedVideoRect.right + outsideToleranceX ||
                activeRect.top < fittedVideoRect.top - outsideToleranceY ||
                activeRect.bottom > fittedVideoRect.bottom + outsideToleranceY
        ) {
            return Decision.Skip(SkipReason.ACTIVE_OUTSIDE_VIDEO)
        }

        val horizontalPadding =
            (activeRect.left - fittedVideoRect.left).coerceAtLeast(0f) +
                (fittedVideoRect.right - activeRect.right).coerceAtLeast(0f)
        val verticalPadding =
            (activeRect.top - fittedVideoRect.top).coerceAtLeast(0f) +
                (fittedVideoRect.bottom - activeRect.bottom).coerceAtLeast(0f)
        val sourcePaddingFraction =
            maxOf(
                horizontalPadding / fittedVideoRect.width,
                verticalPadding / fittedVideoRect.height,
            )
        if (sourcePaddingFraction < MIN_SOURCE_PADDING_FRACTION) {
            return Decision.Skip(SkipReason.NO_SOURCE_PADDING)
        }

        // Use the smaller axis scale so the entire active picture remains inside the safe area.
        val scaleX = safeRect.width / activeRect.width
        val scaleY = safeRect.height / activeRect.height
        val scale = min(scaleX, scaleY)
        if (!scale.isFinite()) return Decision.Skip(SkipReason.INVALID_GEOMETRY)
        if (scale < MIN_SCALE) return Decision.Skip(SkipReason.SCALE_TOO_SMALL)
        if (scale > MAX_SCALE) return Decision.Skip(SkipReason.SCALE_TOO_LARGE)

        val surfaceCenterX = surfaceWidth / 2f
        val surfaceCenterY = surfaceHeight / 2f
        val translatedCenterX =
            surfaceCenterX + scale * (activeRect.centerX - surfaceCenterX)
        val translatedCenterY =
            surfaceCenterY + scale * (activeRect.centerY - surfaceCenterY)
        val translationX = safeRect.centerX - translatedCenterX
        val translationY = safeRect.centerY - translatedCenterY
        val panX = (translationX / (fittedVideoRect.width * scale)).toDouble()
        val panY = (translationY / (fittedVideoRect.height * scale)).toDouble()
        val zoom = ln(scale.toDouble()) / LN_2

        return Decision.Apply(
            scale = scale,
            zoom = zoom,
            panX = panX,
            panY = panY,
            sourcePaddingFraction = sourcePaddingFraction,
        )
    }

    private const val SAMPLE_OUTLIER_FRACTION = 0.15f
    private const val ACTIVE_BOUNDS_TOLERANCE_FRACTION = 0.02f
    private val LN_2 = ln(2.0)
}
