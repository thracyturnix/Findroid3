package dev.jdtech.jellyfin.film.presentation.show

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.PreviousEpisodeCheck
import dev.jdtech.jellyfin.settings.domain.Constants
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

data class ShowState(
    val show: FindroidShow? = null,
    val nextUp: FindroidEpisode? = null,
    val seasons: List<FindroidSeason> = emptyList(),
    val actors: List<FindroidItemPerson> = emptyList(),
    val director: FindroidItemPerson? = null,
    val writers: List<FindroidItemPerson> = emptyList(),
    val subtitleMode: String = Constants.ShowSubtitleMode.AUTO,
    val previousEpisodeCheck: PreviousEpisodeCheck? = null,
    val playbackRequest: ShowPlaybackRequest? = null,
    val error: Exception? = null,
)

data class ShowPlaybackRequest(
    val itemId: UUID,
    val itemKind: BaseItemKind,
    val startFromBeginning: Boolean,
)
