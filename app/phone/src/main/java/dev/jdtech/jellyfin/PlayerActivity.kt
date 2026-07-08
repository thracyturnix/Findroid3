package dev.jdtech.jellyfin

import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.databinding.ActivityPlayerBinding
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

    private lateinit var skipSegmentButton: Button

    private val cutoutAvoidancePlayerListener =
        object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                updateCameraCutoutAvoidance()
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
        binding.playerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
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

                // Brightness mode Auto
                window.attributes =
                    window.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
            }

            false -> {
                binding.playerView.useController = true
                playerGestureHelper?.updateZoomMode(wasZoom)

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
        if (gestures.hasManualZoomSelection || isInPictureInPictureMode) return

        val shouldZoom =
            appPreferences.getValue(appPreferences.playerGesturesStartMaximized) ||
                (appPreferences.getValue(appPreferences.playerSmartFill) && shouldSmartFill())

        gestures.updateZoomMode(shouldZoom)
    }

    private fun shouldSmartFill(): Boolean {
        val playerView = binding.playerView
        val width = playerView.width
        val height = playerView.height
        if (width <= height || width == 0 || height == 0) return false

        val videoSize = viewModel.player.videoSize
        if (videoSize == VideoSize.UNKNOWN || videoSize.width <= 0 || videoSize.height <= 0) {
            return false
        }

        val videoAspectRatio =
            (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
        val containerAspectRatio = width.toFloat() / height
        if (containerAspectRatio <= videoAspectRatio) return false

        val zoomedHeight = width / videoAspectRatio
        val verticalCropFraction = (zoomedHeight - height) / zoomedHeight
        return verticalCropFraction in 0f..SMART_FILL_MAX_VERTICAL_CROP_FRACTION
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
        private const val SMART_FILL_MAX_VERTICAL_CROP_FRACTION = 0.20f
    }
}
