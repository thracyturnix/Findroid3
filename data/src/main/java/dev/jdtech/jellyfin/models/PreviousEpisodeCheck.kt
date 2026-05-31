package dev.jdtech.jellyfin.models

data class PreviousEpisodeCheck(
    val currentEpisode: FindroidEpisode,
    val previousEpisode: FindroidEpisode?,
    val previousSeasonNumber: Int,
    val previousEpisodeNumber: Int,
    val completionPercentage: Int?,
)
