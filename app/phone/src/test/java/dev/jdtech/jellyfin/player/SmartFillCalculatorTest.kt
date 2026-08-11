package dev.jdtech.jellyfin.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartFillCalculatorTest {
    @Test
    fun ordinaryUnpaddedVideoIsNotZoomed() {
        val decision =
            calculate(
                safe = bounds(0, 0, 2400, 1080),
                active = bounds(240, 0, 2160, 1080),
                fitted = bounds(240, 0, 2160, 1080),
            )

        assertSkip(decision, SmartFillCalculator.SkipReason.NO_SOURCE_PADDING)
    }

    @Test
    fun encodedLetterboxIsMaximizedWithoutCroppingPicture() {
        val decision =
            calculate(
                safe = bounds(0, 0, 2400, 1080),
                active = bounds(240, 140, 2160, 940),
                fitted = bounds(240, 0, 2160, 1080),
            ) as SmartFillCalculator.Decision.Apply

        assertClose(1.25f, decision.scale)
        assertClose(0.2593f, decision.sourcePaddingFraction)
        assertTrue(1920f * decision.scale <= 2400f)
        assertTrue(800f * decision.scale <= 1080f)
    }

    @Test
    fun matchingPaddedPictureFillsScreenWithoutCrop() {
        val decision =
            calculate(
                safe = bounds(0, 0, 1920, 1080),
                active = bounds(160, 90, 1760, 990),
                fitted = bounds(0, 0, 1920, 1080),
                surfaceWidth = 1920,
            ) as SmartFillCalculator.Decision.Apply

        assertClose(1.2f, decision.scale)
        assertClose(1920f, 1600f * decision.scale)
        assertClose(1080f, 900f * decision.scale)
    }

    @Test
    fun pillarboxThatRequiresVerticalCropIsNotZoomed() {
        val decision =
            calculate(
                safe = bounds(0, 0, 1920, 1080),
                active = bounds(240, 0, 1680, 1080),
                fitted = bounds(0, 0, 1920, 1080),
                surfaceWidth = 1920,
            )

        assertSkip(decision, SmartFillCalculator.SkipReason.SCALE_TOO_SMALL)
    }

    @Test
    fun tinyTitleCardIsRejectedBecauseScaleIsUnsafe() {
        val decision =
            calculate(
                safe = bounds(0, 0, 2400, 1080),
                active = bounds(600, 270, 1800, 810),
                fitted = bounds(240, 0, 2160, 1080),
            )

        assertSkip(decision, SmartFillCalculator.SkipReason.SCALE_TOO_LARGE)
    }

    @Test
    fun asymmetricSafeAreaProducesPanTowardItsCenter() {
        val decision =
            calculate(
                safe = bounds(120, 0, 2400, 1080),
                active = bounds(240, 140, 2160, 940),
                fitted = bounds(240, 0, 2160, 1080),
            ) as SmartFillCalculator.Decision.Apply

        assertTrue(decision.panX > 0.0)
        assertClose(1.1875f, decision.scale)
    }

    @Test
    fun pixelCutoutSafeSurfaceKeepsWholePicturePastCameraEdge() {
        val unsafeLeft = 145f
        val surfaceWidth = 3120 - unsafeLeft.toInt()
        val surfaceHeight = 1440
        val fitted = bounds(207.5f, 0f, 2767.5f, 1440f)
        val active = bounds(207.5f, 200f, 2767.5f, 1240f)
        val decision =
            calculate(
                safe = bounds(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat()),
                active = active,
                fitted = fitted,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
            ) as SmartFillCalculator.Decision.Apply

        val translationX = (decision.panX * fitted.width * decision.scale).toFloat()
        val surfaceCenterX = surfaceWidth / 2f
        val transformedLeft =
            surfaceCenterX + decision.scale * (active.left - surfaceCenterX) + translationX
        val transformedRight =
            surfaceCenterX + decision.scale * (active.right - surfaceCenterX) + translationX

        assertTrue(unsafeLeft + transformedLeft >= unsafeLeft)
        assertTrue(transformedRight <= surfaceWidth + 0.001f)
    }

    @Test
    fun sampleCombinationDiscardsSingleEdgeOutlierAndAddsSafetyMargin() {
        val normal = SmartFillCalculator.SampleBounds(24, 20, 232, 124)
        val samples = List(8) { normal } + SmartFillCalculator.SampleBounds(0, 0, 256, 144)

        val combined = SmartFillCalculator.combineSamples(samples, 256, 144)

        assertEquals(SmartFillCalculator.SampleBounds(23, 19, 233, 125), combined)
    }

    private fun calculate(
        safe: SmartFillCalculator.Bounds,
        active: SmartFillCalculator.Bounds,
        fitted: SmartFillCalculator.Bounds,
        surfaceWidth: Int = 2400,
        surfaceHeight: Int = 1080,
    ) =
        SmartFillCalculator.calculate(
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            safeRect = safe,
            activeRect = active,
            fittedVideoRect = fitted,
        )

    private fun bounds(left: Int, top: Int, right: Int, bottom: Int) =
        bounds(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    private fun bounds(left: Float, top: Float, right: Float, bottom: Float) =
        SmartFillCalculator.Bounds(
            left,
            top,
            right,
            bottom,
        )

    private fun assertSkip(
        decision: SmartFillCalculator.Decision,
        reason: SmartFillCalculator.SkipReason,
    ) {
        assertTrue(decision is SmartFillCalculator.Decision.Skip)
        assertEquals(reason, (decision as SmartFillCalculator.Decision.Skip).reason)
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertEquals(expected, actual, 0.0002f)
    }
}
