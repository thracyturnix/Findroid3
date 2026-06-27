package dev.jdtech.jellyfin.film.presentation.season

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.PreviousEpisodeCheck
import java.util.UUID

data class SeasonState(
    val season: FindroidSeason? = null,
    val episodes: List<FindroidEpisode> = emptyList(),
    val previousEpisodeCheck: PreviousEpisodeCheck? = null,
    val playbackRequest: SeasonPlaybackRequest? = null,
    val error: Exception? = null,
)

data class SeasonPlaybackRequest(val episodeId: UUID, val startFromBeginning: Boolean)
