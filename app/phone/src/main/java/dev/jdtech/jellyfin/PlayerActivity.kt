package dev.jdtech.jellyfin

import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Rational
import android.util.TypedValue
import android.view.Gravity
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.databinding.ActivityPlayerBinding
import dev.jdtech.jellyfin.player.ActivePictureDetector
import dev.jdtech.jellyfin.player.local.mpv.MPVPlayer
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.player.SpeedSelectionDialogFragment
import dev.jdtech.jellyfin.presentation.player.TrackSelectionDialogFragment
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants
import dev.jdtech.jellyfin.utils.PlayerGestureHelper
import dev.jdtech.jellyfin.utils.PreviewScrubListener
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

var isControlsLocked: Boolean = false

@AndroidEntryPoint
class PlayerActivity : BasePlayerActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    lateinit var binding: ActivityPlayerBinding
    private var playerGestureHelper: PlayerGestureHelper? = null
    override val viewModel: PlayerViewModel by viewModels()
    private var previewScrubListener: PreviewScrubListener? = null
    private var wasZoom: Boolean = false
    private var skipButtonTimeoutExpired: Boolean = true
    private var cutoutAvoidanceEnabled: Boolean = false
    private var hasCurrentSegment: Boolean = false
    private var cameraCutoutDiagnostic: String = "Cutout: waiting for video"
    private val smartFillSamples = mutableListOf<Rect>()
    private var smartFillApplied = false
    private var smartFillAnalysisStarted = false
    private var smartFillSampleInFlight = false
    private var smartFillSampleAttempts = 0
    private var smartFillGeneration = 0
    private var smartFillVideoSize = VideoSize.UNKNOWN

    private lateinit var skipSegmentButton: Button

    private val cutoutAvoidancePlayerListener =
        object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize != smartFillVideoSize) {
                    smartFillVideoSize = videoSize
                    resetSmartFillAnalysis(resetManualSelection = false)
                }
                updateCameraCutoutAvoidance()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                resetSmartFillAnalysis(resetManualSelection = true)
                binding.playerView.post { updateCameraCutoutAvoidance() }
            }
        }

    private val isPipSupported by lazy {
        // Check if device has PiP feature
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return@lazy false
        }

        // Check if PiP is enabled for the app
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager?
        appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    private val handler = Handler(Looper.getMainLooper())
    private val smartFillSample = Runnable { captureSmartFillSample() }
    private val skipButtonTimeout = Runnable {
        if (!binding.playerView.isControllerFullyVisible) {
            skipSegmentButton.isVisible = false
            skipButtonTimeoutExpired = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind")
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning")

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cutoutAvoidanceEnabled = appPreferences.getValue(appPreferences.playerAvoidCameraCutout)
        binding.playerView.player = viewModel.player
        applySubtitleAppearance()
        viewModel.player.addListener(cutoutAvoidancePlayerListener)
        binding.playerView.addOnLayoutChangeListener {
            _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (
                right - left != oldRight - oldLeft ||
                    bottom - top != oldBottom - oldTop
            ) {
                resetSmartFillAnalysis(resetManualSelection = false)
            }
            updateCameraCutoutAvoidance()
        }
        binding.root.setOnApplyWindowInsetsListener { _, windowInsets ->
            updateCameraCutoutAvoidance()
            windowInsets
        }
        val playerControls = binding.playerView.findViewById<View>(R.id.player_controls)
        val lockedControls = binding.playerView.findViewById<View>(R.id.locked_player_view)

        isControlsLocked = false

        configureInsets(playerControls)
        configureInsets(lockedControls)

        if (appPreferences.getValue(appPreferences.playerGestures)) {
            playerGestureHelper =
                PlayerGestureHelper(
                    appPreferences,
                    this,
                    binding.playerView,
                    getSystemService(AUDIO_SERVICE) as AudioManager,
                )
        }

        binding.playerView.findViewById<View>(R.id.back_button).setOnClickListener {
            finishPlayback()
        }

        val videoNameTextView = binding.playerView.findViewById<TextView>(R.id.video_name)

        val audioButton = binding.playerView.findViewById<ImageButton>(R.id.btn_audio_track)
        val subtitleButton = binding.playerView.findViewById<ImageButton>(R.id.btn_subtitle)
        val speedButton = binding.playerView.findViewById<ImageButton>(R.id.btn_speed)
        skipSegmentButton = binding.playerView.findViewById(R.id.btn_skip_segment)
        val pipButton = binding.playerView.findViewById<ImageButton>(R.id.btn_pip)
        val lockButton = binding.playerView.findViewById<ImageButton>(R.id.btn_lockview)
        val unlockButton = binding.playerView.findViewById<ImageButton>(R.id.btn_unlock)
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                handleControllerVisibilityChanged(visibility)
            }
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        Timber.d("$uiState")
                        uiState.apply {
                            // Title
                            videoNameTextView.text = currentItemTitle

                            // Media segment
                            hasCurrentSegment = currentSegment != null
                            currentSegment?.let { segment ->
                                // Skip Button - text
                                skipSegmentButton.text = getString(currentSkipButtonStringRes)
                                // Skip Button - visibility
                                skipSegmentButton.isVisible = !isInPictureInPictureMode
                                if (skipSegmentButton.isVisible) {
                                    skipButtonTimeoutExpired = false
                                    handler.removeCallbacks(skipButtonTimeout)
                                    handler.postDelayed(
                                        skipButtonTimeout,
                                        viewModel.segmentsSkipButtonDuration * 1000,
                                    )
                                }
                                // Skip Button - onClick
                                skipSegmentButton.setOnClickListener {
                                    viewModel.skipSegment(segment)
                                    skipSegmentButton.isVisible = false
                                }
                            } ?: run { skipSegmentButton.isVisible = false }

                            // Trickplay
                            previewScrubListener?.let { it.currentTrickplay = currentTrickplay }

                            playerGestureHelper?.let { it.currentTrickplay = currentTrickplay }

                            // Chapters
                            val playerControlView =
                                findViewById<PlayerControlView>(R.id.exo_controller)
                            if (currentChapters.isNotEmpty()) {
                                val numOfChapters = currentChapters.size
                                playerControlView.setExtraAdGroupMarkers(
                                    LongArray(numOfChapters) { index ->
                                        currentChapters[index].startPosition
                                    },
                                    BooleanArray(numOfChapters) { false },
                                )
                            } else {
                                playerControlView.setExtraAdGroupMarkers(null, null)
                            }

                            // File Loaded
                            if (fileLoaded) {
                                updateCameraCutoutAvoidance()
                                audioButton.isEnabled = true
                                audioButton.imageAlpha = 255
                                lockButton.isEnabled = true
                                lockButton.imageAlpha = 255
                                subtitleButton.isEnabled = hasSubtitleTracks
                                subtitleButton.imageAlpha = if (hasSubtitleTracks) 255 else 75
                                speedButton.isEnabled = true
                                speedButton.imageAlpha = 255
                                pipButton.isEnabled = true
                                pipButton.imageAlpha = 255
                            }
                        }
                    }
                }

                launch {
                    viewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is PlayerEvents.NavigateBack -> finishPlayback()
                            is PlayerEvents.IsPlayingChanged -> {
                                if (event.isPlaying) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                } else {
                                    window.clearFlags(
                                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                    )
                                }

                                if (appPreferences.getValue(appPreferences.playerPipGesture)) {
                                    try {
                                        setPictureInPictureParams(pipParams(event.isPlaying))
                                    } catch (_: IllegalArgumentException) {}
                                }
                            }
                        }
                    }
                }

                launch {
                    while (true) {
                        viewModel.updatePlaybackProgress()
                        delay(5000L)
                    }
                }

                if (
                    appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButton) ||
                        appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkip)
                ) {
                    launch {
                        while (true) {
                            viewModel.updateCurrentSegment()
                            delay(1000L)
                        }
                    }
                }
            }
        }

        audioButton.isEnabled = false
        audioButton.imageAlpha = 75

        lockButton.isEnabled = false
        lockButton.imageAlpha = 75

        subtitleButton.isEnabled = false
        subtitleButton.imageAlpha = 75

        speedButton.isEnabled = false
        speedButton.imageAlpha = 75

        if (isPipSupported) {
            pipButton.isEnabled = false
            pipButton.imageAlpha = 75
        } else {
            val pipSpace = binding.playerView.findViewById<Space>(R.id.space_pip)
            pipButton.isVisible = false
            pipSpace.isVisible = false
        }

        audioButton.setOnClickListener {
            TrackSelectionDialogFragment(C.TRACK_TYPE_AUDIO, viewModel)
                .show(supportFragmentManager, "trackselectiondialog")
        }

        val exoPlayerControlView = findViewById<FrameLayout>(R.id.player_controls)
        val lockedLayout = findViewById<FrameLayout>(R.id.locked_player_view)

        lockButton.setOnClickListener {
            exoPlayerControlView.visibility = View.GONE
            lockedLayout.visibility = View.VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            isControlsLocked = true
        }

        unlockButton.setOnClickListener {
            exoPlayerControlView.visibility = View.VISIBLE
            lockedLayout.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            isControlsLocked = false
        }

        subtitleButton.setOnClickListener {
            if (viewModel.uiState.value.hasSubtitleTracks) {
                TrackSelectionDialogFragment(C.TRACK_TYPE_TEXT, viewModel)
                    .show(supportFragmentManager, "trackselectiondialog")
            }
        }

        speedButton.setOnClickListener {
            SpeedSelectionDialogFragment(viewModel)
                .show(supportFragmentManager, "speedselectiondialog")
        }

        pipButton.setOnClickListener { pictureInPicture() }

        // Set marker color
        val timeBar = binding.playerView.findViewById<DefaultTimeBar>(R.id.exo_progress)
        timeBar.setAdMarkerColor(Color.WHITE)

        if (appPreferences.getValue(appPreferences.playerTrickplay)) {
            val imagePreview = binding.playerView.findViewById<ImageView>(R.id.image_preview)
            previewScrubListener = PreviewScrubListener(imagePreview, timeBar, viewModel.player)

            timeBar.addListener(previewScrubListener!!)
        }

        viewModel.initializePlayer(
            itemId = itemId,
            itemKind = itemKind ?: "",
            startFromBeginning = startFromBeginning,
        )
        binding.playerView.post { updateCameraCutoutAvoidance() }
        hideSystemUI()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind")
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning")

        resetSmartFillAnalysis(resetManualSelection = true)

        viewModel.initializePlayer(
            itemId = itemId,
            itemKind = itemKind ?: "",
            startFromBeginning = startFromBeginning,
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                appPreferences.getValue(appPreferences.playerPipGesture) &&
                viewModel.player.isPlaying &&
                !isControlsLocked
        ) {
            pictureInPicture()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(smartFillSample)
        smartFillGeneration++
        viewModel.player.removeListener(cutoutAvoidancePlayerListener)
        super.onDestroy()
    }

    private fun finishPlayback() {
        try {
            viewModel.player.clearVideoSurfaceView(
                binding.playerView.videoSurfaceView as SurfaceView
            )
        } catch (e: Exception) {
            Timber.e(e)
        }
        handler.removeCallbacks(skipButtonTimeout)
        finish()
    }

    private fun pipParams(
        enableAutoEnter: Boolean = viewModel.player.isPlaying
    ): PictureInPictureParams {
        val displayAspectRatio = Rational(binding.playerView.width, binding.playerView.height)

        val aspectRatio =
            binding.playerView.player?.videoSize?.let {
                Rational(
                    it.width.coerceAtMost((it.height * 2.39f).toInt()),
                    it.height.coerceAtMost((it.width * 2.39f).toInt()),
                )
            }

        val sourceRectHint =
            if (displayAspectRatio < aspectRatio!!) {
                val space =
                    ((binding.playerView.height -
                            (binding.playerView.width.toFloat() / aspectRatio.toFloat())) / 2)
                        .toInt()
                Rect(
                    0,
                    space,
                    binding.playerView.width,
                    (binding.playerView.width.toFloat() / aspectRatio.toFloat()).toInt() + space,
                )
            } else {
                val space =
                    ((binding.playerView.width -
                            (binding.playerView.height.toFloat() * aspectRatio.toFloat())) / 2)
                        .toInt()
                Rect(
                    space,
                    0,
                    (binding.playerView.height.toFloat() * aspectRatio.toFloat()).toInt() + space,
                    binding.playerView.height,
                )
            }

        val builder =
            PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .setSourceRectHint(sourceRectHint)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(enableAutoEnter)
        }

        return builder.build()
    }

    private fun pictureInPicture() {
        if (!isPipSupported) {
            return
        }

        try {
            enterPictureInPictureMode(pipParams())
        } catch (_: IllegalArgumentException) {}
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.isInPictureInPictureMode = isInPictureInPictureMode
        updateCameraCutoutAvoidance()
        when (isInPictureInPictureMode) {
            true -> {
                binding.playerView.useController = false
                skipSegmentButton.isVisible = false

                wasZoom = playerGestureHelper?.isZoomEnabled == true
                playerGestureHelper?.updateZoomMode(false)
                resetSmartFillAnalysis(resetManualSelection = false)

                // Brightness mode Auto
                window.attributes =
                    window.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
            }

            false -> {
                binding.playerView.useController = true
                playerGestureHelper?.updateZoomMode(wasZoom)
                updateAutomaticZoomMode()

                // Override auto brightness
                if (
                    appPreferences.getValue(appPreferences.playerGesturesVB) &&
                        appPreferences.getValue(appPreferences.playerGesturesBrightnessRemember)
                ) {
                    window.attributes =
                        window.attributes.apply {
                            screenBrightness =
                                appPreferences.getValue(appPreferences.playerBrightness)
                        }
                }
            }
        }
    }

    private fun applySubtitleAppearance() {
        val subtitleView =
            binding.playerView.findViewById<SubtitleView>(androidx.media3.ui.R.id.exo_subtitles)
        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setApplyEmbeddedFontSizes(false)
        subtitleView.setFixedTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            when (appPreferences.getValue(appPreferences.subtitleSize)) {
                Constants.SubtitleAppearance.SIZE_SMALL -> 16f
                Constants.SubtitleAppearance.SIZE_LARGE -> 24f
                Constants.SubtitleAppearance.SIZE_EXTRA_LARGE -> 28f
                else -> 20f
            },
        )
        subtitleView.setBottomPaddingFraction(
            when (appPreferences.getValue(appPreferences.subtitlePosition)) {
                Constants.SubtitleAppearance.POSITION_LOW -> 0.18f
                Constants.SubtitleAppearance.POSITION_LOWER_MIDDLE -> 0.28f
                Constants.SubtitleAppearance.POSITION_UPPER_MIDDLE -> 0.38f
                Constants.SubtitleAppearance.POSITION_MIDDLE -> 0.48f
                else -> 0.08f
            }
        )

        val foregroundColor =
            when (appPreferences.getValue(appPreferences.subtitleColor)) {
                Constants.SubtitleAppearance.COLOR_YELLOW -> Color.YELLOW
                else -> Color.WHITE
            }
        val backgroundColor =
            when (appPreferences.getValue(appPreferences.subtitleBackground)) {
                Constants.SubtitleAppearance.BACKGROUND_TRANSLUCENT -> 0x99000000.toInt()
                Constants.SubtitleAppearance.BACKGROUND_BLACK -> Color.BLACK
                else -> Color.TRANSPARENT
            }
        val edgeType =
            when (appPreferences.getValue(appPreferences.subtitleOutline)) {
                Constants.SubtitleAppearance.OUTLINE_NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
                Constants.SubtitleAppearance.OUTLINE_SHADOW ->
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                else -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            }
        subtitleView.setStyle(
            CaptionStyleCompat(
                foregroundColor,
                backgroundColor,
                Color.TRANSPARENT,
                edgeType,
                Color.BLACK,
                null,
            )
        )
    }

    fun updateCameraCutoutAvoidance() {
        if (!::binding.isInitialized) return

        updateAutomaticZoomMode()

        val adjustment =
            if (cutoutAvoidanceEnabled && !isInPictureInPictureMode) {
                calculateCameraCutoutAdjustment()
            } else {
                cameraCutoutDiagnostic =
                    if (isInPictureInPictureMode) {
                        "Cutout: inactive (picture-in-picture)"
                    } else {
                        "Cutout: disabled"
                    }
                CutoutAdjustment.NONE
            }

        applyCameraCutoutAdjustment(adjustment)
    }

    private fun updateAutomaticZoomMode() {
        val gestures = playerGestureHelper ?: return
        if (gestures.hasManualZoomSelection || isInPictureInPictureMode) {
            cancelSmartFillSampling()
            return
        }

        if (appPreferences.getValue(appPreferences.playerGesturesStartMaximized)) {
            cancelSmartFillSampling()
            smartFillApplied = false
            gestures.updateZoomMode(true)
            return
        }

        if (smartFillApplied) return

        gestures.updateZoomMode(false)
        if (
            appPreferences.getValue(appPreferences.playerSmartFill) &&
                viewModel.player is MPVPlayer
        ) {
            scheduleSmartFillAnalysis()
        } else {
            cancelSmartFillSampling()
        }
    }

    private fun scheduleSmartFillAnalysis() {
        if (smartFillAnalysisStarted || smartFillApplied || smartFillSampleInFlight) return
        val videoSize = viewModel.player.videoSize
        val surface = binding.playerView.videoSurfaceView as? SurfaceView ?: return
        if (
            videoSize == VideoSize.UNKNOWN ||
                videoSize.width <= 0 ||
                videoSize.height <= 0 ||
                surface.width <= 0 ||
                surface.height <= 0
        ) {
            return
        }

        smartFillAnalysisStarted = true
        handler.removeCallbacks(smartFillSample)
        handler.postDelayed(smartFillSample, SMART_FILL_INITIAL_SAMPLE_DELAY_MS)
    }

    private fun captureSmartFillSample() {
        val gestures = playerGestureHelper ?: return
        val surface = binding.playerView.videoSurfaceView as? SurfaceView ?: return
        if (
            isFinishing ||
                isDestroyed ||
                isInPictureInPictureMode ||
                gestures.hasManualZoomSelection ||
                gestures.isZoomEnabled ||
                smartFillApplied ||
                viewModel.player !is MPVPlayer ||
                !appPreferences.getValue(appPreferences.playerSmartFill)
        ) {
            cancelSmartFillSampling()
            return
        }

        if (!surface.holder.surface.isValid || surface.width <= 0 || surface.height <= 0) {
            scheduleNextSmartFillSample()
            return
        }

        val generation = smartFillGeneration
        val sampleHeight =
            (SMART_FILL_SAMPLE_WIDTH * surface.height.toFloat() / surface.width)
                .toInt()
                .coerceIn(SMART_FILL_MIN_SAMPLE_HEIGHT, SMART_FILL_MAX_SAMPLE_HEIGHT)
        val bitmap = createBitmap(SMART_FILL_SAMPLE_WIDTH, sampleHeight, Bitmap.Config.ARGB_8888)
        smartFillSampleInFlight = true
        smartFillSampleAttempts++

        PixelCopy.request(
            surface,
            bitmap,
            { result ->
                smartFillSampleInFlight = false
                if (generation != smartFillGeneration || isDestroyed) {
                    bitmap.recycle()
                    return@request
                }

                if (result == PixelCopy.SUCCESS) {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    ActivePictureDetector.detect(pixels, bitmap.width, bitmap.height)?.let {
                        smartFillSamples += it
                    }
                }
                bitmap.recycle()

                if (smartFillSamples.size >= SMART_FILL_REQUIRED_SAMPLES) {
                    evaluateSmartFill(surface, SMART_FILL_REQUIRED_SAMPLES)
                } else if (smartFillSampleAttempts < SMART_FILL_MAX_SAMPLE_ATTEMPTS) {
                    scheduleNextSmartFillSample()
                } else {
                    logSmartFillSkipped("no stable active picture")
                    cancelSmartFillSampling()
                }
            },
            handler,
        )
    }

    private fun scheduleNextSmartFillSample() {
        handler.removeCallbacks(smartFillSample)
        handler.postDelayed(smartFillSample, SMART_FILL_SAMPLE_INTERVAL_MS)
    }

    private fun evaluateSmartFill(surface: SurfaceView, sampleCount: Int) {
        val samples = smartFillSamples.takeLast(sampleCount)
        val sampleWidth = SMART_FILL_SAMPLE_WIDTH
        val sampleHeight =
            (SMART_FILL_SAMPLE_WIDTH * surface.height.toFloat() / surface.width)
                .toInt()
                .coerceIn(SMART_FILL_MIN_SAMPLE_HEIGHT, SMART_FILL_MAX_SAMPLE_HEIGHT)
        val horizontalVariation =
            maxOf(
                samples.maxOf { it.left } - samples.minOf { it.left },
                samples.maxOf { it.right } - samples.minOf { it.right },
            )
        val verticalVariation =
            maxOf(
                samples.maxOf { it.top } - samples.minOf { it.top },
                samples.maxOf { it.bottom } - samples.minOf { it.bottom },
            )
        if (
            horizontalVariation > sampleWidth * SMART_FILL_MAX_BOUND_VARIATION_FRACTION ||
                verticalVariation > sampleHeight * SMART_FILL_MAX_BOUND_VARIATION_FRACTION
        ) {
            if (smartFillSampleAttempts < SMART_FILL_MAX_SAMPLE_ATTEMPTS) {
                scheduleNextSmartFillSample()
            } else {
                logSmartFillSkipped("active picture did not stabilize")
                cancelSmartFillSampling()
            }
            return
        }

        val marginX = (sampleWidth * SMART_FILL_SAFETY_MARGIN_FRACTION).coerceAtLeast(1f).toInt()
        val marginY = (sampleHeight * SMART_FILL_SAFETY_MARGIN_FRACTION).coerceAtLeast(1f).toInt()
        val activeSample =
            Rect(
                (samples.minOf { it.left } - marginX).coerceAtLeast(0),
                (samples.minOf { it.top } - marginY).coerceAtLeast(0),
                (samples.maxOf { it.right } + marginX).coerceAtMost(sampleWidth),
                (samples.maxOf { it.bottom } + marginY).coerceAtMost(sampleHeight),
            )
        val activeRect =
            RectF(
                activeSample.left * surface.width / sampleWidth.toFloat(),
                activeSample.top * surface.height / sampleHeight.toFloat(),
                activeSample.right * surface.width / sampleWidth.toFloat(),
                activeSample.bottom * surface.height / sampleHeight.toFloat(),
            )
        val safeRect = smartFillSafeRect(surface)
        if (safeRect.width() <= 0f || safeRect.height() <= 0f) {
            logSmartFillSkipped("no usable cutout-safe area")
            cancelSmartFillSampling()
            return
        }

        val edgeToleranceX = safeRect.width() * SMART_FILL_EDGE_TOLERANCE_FRACTION
        val edgeToleranceY = safeRect.height() * SMART_FILL_EDGE_TOLERANCE_FRACTION
        val touchesLeftAndRight =
            activeRect.left <= safeRect.left + edgeToleranceX &&
                activeRect.right >= safeRect.right - edgeToleranceX
        val touchesTopAndBottom =
            activeRect.top <= safeRect.top + edgeToleranceY &&
                activeRect.bottom >= safeRect.bottom - edgeToleranceY
        if (touchesLeftAndRight || touchesTopAndBottom) {
            logSmartFillDecision(
                surface,
                safeRect,
                activeSample,
                touchesLeftAndRight,
                touchesTopAndBottom,
                1f,
                0.0,
                0.0,
                "already reaches an opposing edge pair",
            )
            cancelSmartFillSampling()
            return
        }

        val scaleX = safeRect.width() / activeRect.width()
        val scaleY = safeRect.height() / activeRect.height()
        val scale = min(scaleX, scaleY)
        if (scale < SMART_FILL_MIN_SCALE) {
            logSmartFillSkipped("available-space gain below threshold")
            cancelSmartFillSampling()
            return
        }

        val surfaceCenterX = surface.width / 2f
        val surfaceCenterY = surface.height / 2f
        val translatedCenterX = surfaceCenterX + scale * (activeRect.centerX() - surfaceCenterX)
        val translatedCenterY = surfaceCenterY + scale * (activeRect.centerY() - surfaceCenterY)
        val translationX = safeRect.centerX() - translatedCenterX
        val translationY = safeRect.centerY() - translatedCenterY

        val videoSize = viewModel.player.videoSize
        val videoAspectRatio =
            (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
        val fittedRect =
            fittedVideoRect(
                surface.width,
                surface.height,
                videoAspectRatio,
                surface.width.toFloat() / surface.height,
            )
        val panX = (translationX / (fittedRect.width() * scale)).toDouble()
        val panY = (translationY / (fittedRect.height() * scale)).toDouble()
        val zoom = ln(scale.toDouble()) / ln(2.0)

        if (playerGestureHelper?.updateSmartZoom(zoom, panX, panY) == true) {
            smartFillApplied = true
            smartFillAnalysisStarted = false
            handler.removeCallbacks(smartFillSample)
            logSmartFillDecision(
                surface,
                safeRect,
                activeSample,
                touchesLeftAndRight,
                touchesTopAndBottom,
                scale,
                panX,
                panY,
                "applied",
            )
        } else {
            cancelSmartFillSampling()
        }
    }

    private fun smartFillSafeRect(surface: SurfaceView): RectF {
        if (!cutoutAvoidanceEnabled || binding.playerView.width <= binding.playerView.height) {
            return RectF(0f, 0f, surface.width.toFloat(), surface.height.toFloat())
        }
        val cutout = binding.root.rootWindowInsets?.displayCutout
            ?: return RectF(0f, 0f, surface.width.toFloat(), surface.height.toFloat())
        val playerWidth = binding.playerView.width
        val unsafeLeft = cutoutUnsafeLeft(playerWidth, cutout.safeInsetLeft, cutout.boundingRects)
        val unsafeRight = cutoutUnsafeRight(playerWidth, cutout.safeInsetRight, cutout.boundingRects)
        val playerLocation = IntArray(2)
        val surfaceLocation = IntArray(2)
        binding.playerView.getLocationInWindow(playerLocation)
        surface.getLocationInWindow(surfaceLocation)
        val left =
            (playerLocation[0] + unsafeLeft - surfaceLocation[0])
                .toFloat()
                .coerceIn(0f, surface.width.toFloat())
        val right =
            (playerLocation[0] + playerWidth - unsafeRight - surfaceLocation[0])
                .toFloat()
                .coerceIn(left, surface.width.toFloat())
        return RectF(left, 0f, right, surface.height.toFloat())
    }

    private fun resetSmartFillAnalysis(resetManualSelection: Boolean) {
        smartFillGeneration++
        handler.removeCallbacks(smartFillSample)
        smartFillSamples.clear()
        smartFillApplied = false
        smartFillAnalysisStarted = false
        smartFillSampleInFlight = false
        smartFillSampleAttempts = 0
        if (resetManualSelection) {
            playerGestureHelper?.resetAutomaticZoomSelection()
        } else if (playerGestureHelper?.isSmartZoomEnabled == true) {
            playerGestureHelper?.updateZoomMode(false)
        }
    }

    private fun cancelSmartFillSampling() {
        smartFillGeneration++
        handler.removeCallbacks(smartFillSample)
        smartFillAnalysisStarted = false
        smartFillSampleInFlight = false
    }

    private fun logSmartFillSkipped(reason: String) {
        Timber.d(
            "Smart fill: decision=fit, reason=%s, attempts=%d, validSamples=%d",
            reason,
            smartFillSampleAttempts,
            smartFillSamples.size,
        )
    }

    private fun logSmartFillDecision(
        surface: SurfaceView,
        safeRect: RectF,
        activeSample: Rect,
        touchesLeftAndRight: Boolean,
        touchesTopAndBottom: Boolean,
        scale: Float,
        panX: Double,
        panY: Double,
        reason: String,
    ) {
        val videoSize = viewModel.player.videoSize
        Timber.d(
            "Smart fill: decision=%s, player=%dx%d, video=%dx%d par=%.3f, surface=%dx%d, safeRect=%s, activeSample=%s/%dx%d, touchesLR=%s, touchesTB=%s, scale=%.3f, pan=[%.4f,%.4f], attempts=%d",
            reason,
            binding.playerView.width,
            binding.playerView.height,
            videoSize.width,
            videoSize.height,
            videoSize.pixelWidthHeightRatio,
            surface.width,
            surface.height,
            safeRect,
            activeSample,
            SMART_FILL_SAMPLE_WIDTH,
            (SMART_FILL_SAMPLE_WIDTH * surface.height.toFloat() / surface.width)
                .toInt()
                .coerceIn(SMART_FILL_MIN_SAMPLE_HEIGHT, SMART_FILL_MAX_SAMPLE_HEIGHT),
            touchesLeftAndRight,
            touchesTopAndBottom,
            scale,
            panX,
            panY,
            smartFillSampleAttempts,
        )
    }

    private fun applyCameraCutoutAdjustment(adjustment: CutoutAdjustment) {
        if (
            binding.playerView.paddingLeft != adjustment.paddingLeft ||
                binding.playerView.paddingRight != adjustment.paddingRight
        ) {
            binding.playerView.setPadding(adjustment.paddingLeft, 0, adjustment.paddingRight, 0)
        }

        val contentFrame =
            binding.playerView.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)
                ?: return
        if (contentFrame.translationX != adjustment.translationX) {
            contentFrame.translationX = adjustment.translationX
        }

        val layoutParams = contentFrame.layoutParams as? FrameLayout.LayoutParams ?: return
        if (layoutParams.gravity != adjustment.gravity) {
            layoutParams.gravity = adjustment.gravity
            contentFrame.layoutParams = layoutParams
        }
    }

    private fun handleControllerVisibilityChanged(visibility: Int) {
        if (visibility == View.GONE) {
            hideSystemUI()
        }

        if (skipButtonTimeoutExpired && hasCurrentSegment) {
            skipSegmentButton.visibility = visibility
        }
    }

    private fun calculateCameraCutoutAdjustment(): CutoutAdjustment {
        val playerView = binding.playerView
        val width = playerView.width
        val height = playerView.height
        if (width <= height || width == 0 || height == 0) {
            cameraCutoutDiagnostic = "Cutout: inactive (portrait or no player size)"
            return CutoutAdjustment.NONE
        }

        val videoSize = viewModel.player.videoSize
        if (videoSize == VideoSize.UNKNOWN || videoSize.width <= 0 || videoSize.height <= 0) {
            cameraCutoutDiagnostic = "Cutout: waiting for video size"
            return CutoutAdjustment.NONE
        }

        val cutout =
            binding.root.rootWindowInsets?.displayCutout
                ?: run {
                    cameraCutoutDiagnostic =
                        "Cutout: no display cutout\n" +
                            "Video: ${videoSize.width}x${videoSize.height} par %.2f, player ${width}x$height"
                                .format(videoSize.pixelWidthHeightRatio)
                    return CutoutAdjustment.NONE
                }
        val unsafeLeft = cutoutUnsafeLeft(width, cutout.safeInsetLeft, cutout.boundingRects)
        val unsafeRight = cutoutUnsafeRight(width, cutout.safeInsetRight, cutout.boundingRects)
        if (unsafeLeft == 0 && unsafeRight == 0) {
            logCameraCutoutDecision(
                width = width,
                height = height,
                videoSize = videoSize,
                cutout = cutout,
                videoRect = null,
                adjustment = CutoutAdjustment.NONE,
                reason = "no unsafe horizontal cutout",
            )
            return CutoutAdjustment.NONE
        }

        val videoRect = renderedVideoRect(width, height, videoSize)
        val avoidLeft = unsafeLeft > 0 && videoRect.left < unsafeLeft
        val avoidRight = unsafeRight > 0 && videoRect.right > width - unsafeRight
        if (!avoidLeft && !avoidRight) {
            val adjustment = CutoutAdjustment.NONE
            logCameraCutoutDecision(
                width = width,
                height = height,
                videoSize = videoSize,
                cutout = cutout,
                videoRect = videoRect,
                adjustment = adjustment,
                reason = "video clear of cutout",
            )
            return adjustment
        }

        val adjustment =
            when {
            avoidLeft && avoidRight ->
                CutoutAdjustment(
                    paddingLeft = unsafeLeft,
                    paddingRight = unsafeRight,
                    strategy = "shrink both",
                    gravity = Gravity.CENTER,
                )

            avoidLeft -> {
                val shiftNeeded = unsafeLeft - videoRect.left
                val availableRightLetterbox = width - videoRect.right
                if (shiftNeeded <= availableRightLetterbox) {
                    CutoutAdjustment(translationX = shiftNeeded, strategy = "shift right")
                } else {
                    CutoutAdjustment(
                        paddingLeft = unsafeLeft,
                        strategy = "shrink anchor right",
                        gravity = Gravity.END or Gravity.CENTER_VERTICAL,
                    )
                }
            }

            avoidRight -> {
                val shiftNeeded = videoRect.right - (width - unsafeRight)
                val availableLeftLetterbox = videoRect.left
                if (shiftNeeded <= availableLeftLetterbox) {
                    CutoutAdjustment(translationX = -shiftNeeded, strategy = "shift left")
                } else {
                    CutoutAdjustment(
                        paddingRight = unsafeRight,
                        strategy = "shrink anchor left",
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL,
                    )
                }
            }

            else -> CutoutAdjustment.NONE
        }

        logCameraCutoutDecision(
            width = width,
            height = height,
            videoSize = videoSize,
            cutout = cutout,
            videoRect = videoRect,
            adjustment = adjustment,
            reason = "video overlaps cutout",
        )
        return adjustment
    }

    private fun logCameraCutoutDecision(
        width: Int,
        height: Int,
        videoSize: VideoSize,
        cutout: android.view.DisplayCutout,
        videoRect: RectF?,
        adjustment: CutoutAdjustment,
        reason: String,
    ) {
        cameraCutoutDiagnostic =
            "Cutout: ${adjustment.strategy} ($reason)\n" +
                "Video: ${videoSize.width}x${videoSize.height} par %.2f, player ${width}x$height\n"
                    .format(videoSize.pixelWidthHeightRatio) +
                "Unsafe: L${
                    cutoutUnsafeLeft(width, cutout.safeInsetLeft, cutout.boundingRects)
                } R${
                    cutoutUnsafeRight(width, cutout.safeInsetRight, cutout.boundingRects)
                }, pad L${adjustment.paddingLeft} R${adjustment.paddingRight}, shift %.1f"
                    .format(adjustment.translationX)

        Timber.d(
            "Camera cutout avoidance: reason=%s, strategy=%s, player=%dx%d, video=%dx%d par=%.3f, safeInsets=[l=%d,t=%d,r=%d,b=%d], bounds=%s, videoRect=%s, padding=[l=%d,r=%d], translationX=%.1f, gravity=%d",
            reason,
            adjustment.strategy,
            width,
            height,
            videoSize.width,
            videoSize.height,
            videoSize.pixelWidthHeightRatio,
            cutout.safeInsetLeft,
            cutout.safeInsetTop,
            cutout.safeInsetRight,
            cutout.safeInsetBottom,
            cutout.boundingRects.joinToString(),
            videoRect,
            adjustment.paddingLeft,
            adjustment.paddingRight,
            adjustment.translationX,
            adjustment.gravity,
        )
    }

    private fun renderedVideoRect(width: Int, height: Int, videoSize: VideoSize): RectF {
        val videoAspectRatio =
            (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
        val containerAspectRatio = width.toFloat() / height

        return if (playerGestureHelper?.isZoomEnabled == true) {
            zoomedVideoRect(width, height, videoAspectRatio, containerAspectRatio)
        } else {
            fittedVideoRect(width, height, videoAspectRatio, containerAspectRatio)
        }
    }

    private fun fittedVideoRect(
        width: Int,
        height: Int,
        videoAspectRatio: Float,
        containerAspectRatio: Float,
    ): RectF {
        return if (containerAspectRatio > videoAspectRatio) {
            val renderedWidth = height * videoAspectRatio
            val left = (width - renderedWidth) / 2f
            RectF(left, 0f, left + renderedWidth, height.toFloat())
        } else {
            val renderedHeight = width / videoAspectRatio
            val top = (height - renderedHeight) / 2f
            RectF(0f, top, width.toFloat(), top + renderedHeight)
        }
    }

    private fun zoomedVideoRect(
        width: Int,
        height: Int,
        videoAspectRatio: Float,
        containerAspectRatio: Float,
    ): RectF {
        return if (containerAspectRatio > videoAspectRatio) {
            val renderedHeight = width / videoAspectRatio
            val top = (height - renderedHeight) / 2f
            RectF(0f, top, width.toFloat(), top + renderedHeight)
        } else {
            val renderedWidth = height * videoAspectRatio
            val left = (width - renderedWidth) / 2f
            RectF(left, 0f, left + renderedWidth, height.toFloat())
        }
    }

    private fun cutoutUnsafeLeft(width: Int, safeInsetLeft: Int, boundingRects: List<Rect>): Int {
        val cutoutBounds =
            boundingRects
                .filter { it.left < width * CAMERA_CUTOUT_EDGE_FRACTION && it.centerX() < width / 2 }
                .maxOfOrNull { it.right }
                ?: 0
        return maxOf(safeInsetLeft, cutoutBounds)
    }

    private fun cutoutUnsafeRight(width: Int, safeInsetRight: Int, boundingRects: List<Rect>): Int {
        val cutoutBounds =
            boundingRects
                .filter {
                    it.right > width * (1 - CAMERA_CUTOUT_EDGE_FRACTION) &&
                        it.centerX() > width / 2
                }
                .maxOfOrNull { width - it.left }
                ?: 0
        return maxOf(safeInsetRight, cutoutBounds)
    }

    private data class CutoutAdjustment(
        val paddingLeft: Int = 0,
        val paddingRight: Int = 0,
        val translationX: Float = 0f,
        val strategy: String = "none",
        val gravity: Int = Gravity.CENTER,
    ) {
        companion object {
            val NONE = CutoutAdjustment()
        }
    }

    companion object {
        private const val CAMERA_CUTOUT_EDGE_FRACTION = 0.08f
        private const val SMART_FILL_SAMPLE_WIDTH = 256
        private const val SMART_FILL_MIN_SAMPLE_HEIGHT = 96
        private const val SMART_FILL_MAX_SAMPLE_HEIGHT = 256
        private const val SMART_FILL_REQUIRED_SAMPLES = 5
        private const val SMART_FILL_MAX_SAMPLE_ATTEMPTS = 12
        private const val SMART_FILL_INITIAL_SAMPLE_DELAY_MS = 700L
        private const val SMART_FILL_SAMPLE_INTERVAL_MS = 450L
        private const val SMART_FILL_EDGE_TOLERANCE_FRACTION = 0.015f
        private const val SMART_FILL_MAX_BOUND_VARIATION_FRACTION = 0.04f
        private const val SMART_FILL_SAFETY_MARGIN_FRACTION = 0.012f
        private const val SMART_FILL_MIN_SCALE = 1.025f
    }
}
