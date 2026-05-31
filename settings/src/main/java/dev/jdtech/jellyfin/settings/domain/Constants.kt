package dev.jdtech.jellyfin.settings.domain

object Constants {
    // Player - Media Segments
    object PlayerMediaSegmentsAutoSkip {
        const val ALWAYS = "always"
        const val PIP = "pip"
    }

    object ShowSubtitleMode {
        const val AUTO = "auto"
        const val OFF = "off"
        const val ENGLISH = "english"
        const val ENGLISH_FORCED = "english_forced"
    }

    object UnknownAudioSubtitleMode {
        const val OFF = "off"
        const val ENGLISH = "english"
    }

    object SubtitleAppearance {
        const val SIZE_SMALL = "small"
        const val SIZE_NORMAL = "normal"
        const val SIZE_LARGE = "large"
        const val SIZE_EXTRA_LARGE = "extra_large"

        const val COLOR_WHITE = "white"
        const val COLOR_YELLOW = "yellow"

        const val OUTLINE_NONE = "none"
        const val OUTLINE_SHADOW = "shadow"
        const val OUTLINE_OUTLINE = "outline"

        const val BACKGROUND_OFF = "off"
        const val BACKGROUND_TRANSLUCENT = "translucent"
        const val BACKGROUND_BLACK = "black"

        const val POSITION_BOTTOM = "bottom"
        const val POSITION_LOW = "low"
        const val POSITION_LOWER_MIDDLE = "lower_middle"
        const val POSITION_UPPER_MIDDLE = "upper_middle"
        const val POSITION_MIDDLE = "middle"
    }

    // Network
    const val NETWORK_DEFAULT_REQUEST_TIMEOUT = 30_000L
    const val NETWORK_DEFAULT_CONNECT_TIMEOUT = 6_000L
    const val NETWORK_DEFAULT_SOCKET_TIMEOUT = 10_000L
}
