package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.PreviousEpisodeCheck

internal fun previousEpisodeCheck(
    currentEpisode: FindroidEpisode,
    episodes: List<FindroidEpisode>,
): PreviousEpisodeCheck? {
    if (
        currentEpisode.parentIndexNumber <= 0 ||
            currentEpisode.indexNumber <= 0 ||
            currentEpisode.playbackPositionTicks > 0 ||
            currentEpisode.played
    ) {
        return null
    }

    val regularEpisodes =
        episodes.filter { it.parentIndexNumber > 0 && it.indexNumber > 0 }.distinctBy { it.id }
    if (regularEpisodes.size <= 1) return null

    val previousSeasonNumber: Int
    val previousEpisodeNumber: Int
    if (currentEpisode.indexNumber > 1) {
        previousSeasonNumber = currentEpisode.parentIndexNumber
        previousEpisodeNumber = currentEpisode.indexNumber - 1
    } else {
        val previousSeasonEpisodes =
            regularEpisodes.filter { it.parentIndexNumber < currentEpisode.parentIndexNumber }
        val previousSeason =
            previousSeasonEpisodes.maxOfOrNull { it.parentIndexNumber } ?: return null
        previousSeasonNumber = previousSeason
        previousEpisodeNumber =
            previousSeasonEpisodes
                .filter { it.parentIndexNumber == previousSeason }
                .maxOf { it.indexNumberEnd ?: it.indexNumber }
    }

    val previousEpisode =
        regularEpisodes.firstOrNull {
            it.parentIndexNumber == previousSeasonNumber &&
                previousEpisodeNumber in it.indexNumber..(it.indexNumberEnd ?: it.indexNumber)
        }
    if (previousEpisode?.played == true) return null

    val completionPercentage =
        previousEpisode?.let {
            if (it.runtimeTicks <= 0) {
                0
            } else {
                ((it.playbackPositionTicks * 100) / it.runtimeTicks).toInt().coerceIn(0, 100)
            }
        }
    if (completionPercentage != null && completionPercentage >= 90) return null

    return PreviousEpisodeCheck(
        currentEpisode = currentEpisode,
        previousEpisode = previousEpisode?.takeUnless { it.missing || !it.canPlay },
        previousSeasonNumber = previousSeasonNumber,
        previousEpisodeNumber = previousEpisodeNumber,
        completionPercentage = completionPercentage,
    )
}
