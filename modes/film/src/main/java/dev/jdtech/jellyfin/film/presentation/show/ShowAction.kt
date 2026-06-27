package dev.jdtech.jellyfin.film.presentation.show

import dev.jdtech.jellyfin.models.FindroidItem
import java.util.UUID

sealed interface ShowAction {
    data class Play(val startFromBeginning: Boolean = false) : ShowAction

    data object PlayPreviousEpisode : ShowAction

    data object PlaySelectedEpisodeAnyway : ShowAction

    data object DismissPreviousEpisodeCheck : ShowAction

    data object PlaybackStarted : ShowAction

    data class PlayTrailer(val trailer: String) : ShowAction

    data object MarkAsPlayed : ShowAction

    data object UnmarkAsPlayed : ShowAction

    data object MarkAsFavorite : ShowAction

    data object UnmarkAsFavorite : ShowAction

    data class SelectSubtitleMode(val mode: String) : ShowAction

    data object OnBackClick : ShowAction

    data object OnHomeClick : ShowAction

    data class NavigateToItem(val item: FindroidItem) : ShowAction

    data class NavigateToPerson(val personId: UUID) : ShowAction
}
