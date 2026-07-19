package dev.jdtech.jellyfin.film.presentation.playlist

import dev.jdtech.jellyfin.models.FindroidItem

sealed interface PlaylistAction {
    data class OnItemClick(val item: FindroidItem) : PlaylistAction

    data object OnBackClick : PlaylistAction

    data object OnRetryClick : PlaylistAction
}
