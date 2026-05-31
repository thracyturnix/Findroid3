package dev.jdtech.jellyfin.film.presentation.episode

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.PreviousEpisodeCheck
import dev.jdtech.jellyfin.models.VideoMetadata
import java.util.UUID

data class EpisodeState(
    val episode: FindroidEpisode? = null,
    val videoMetadata: VideoMetadata? = null,
    val actors: List<FindroidItemPerson> = emptyList(),
    val displayExtraInfo: Boolean = false,
    val previousEpisodeCheck: PreviousEpisodeCheck? = null,
    val playbackRequest: EpisodePlaybackRequest? = null,
    val error: Exception? = null,
)

data class EpisodePlaybackRequest(val episodeId: UUID, val startFromBeginning: Boolean)
