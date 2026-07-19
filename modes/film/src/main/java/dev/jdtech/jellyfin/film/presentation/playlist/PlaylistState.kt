package dev.jdtech.jellyfin.film.presentation.playlist

import dev.jdtech.jellyfin.models.FindroidItem

data class PlaylistState(
    val items: List<FindroidItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: Exception? = null,
)
