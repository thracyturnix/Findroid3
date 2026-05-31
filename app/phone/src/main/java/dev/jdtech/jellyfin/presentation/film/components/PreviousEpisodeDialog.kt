package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.PreviousEpisodeCheck

@Composable
fun PreviousEpisodeDialog(
    check: PreviousEpisodeCheck,
    onPlayPrevious: () -> Unit,
    onPlaySelected: () -> Unit,
    onDismiss: () -> Unit,
) {
    val previousLabel =
        episodeLabel(
            seasonNumber = check.previousSeasonNumber,
            episodeNumber = check.previousEpisodeNumber,
            currentSeasonNumber = check.currentEpisode.parentIndexNumber,
        )
    val currentLabel =
        episodeLabel(
            seasonNumber = check.currentEpisode.parentIndexNumber,
            episodeNumber = check.currentEpisode.indexNumber,
            currentSeasonNumber = check.currentEpisode.parentIndexNumber,
        )

    AlertDialog(
        title = {
            Text(
                text =
                    stringResource(
                        if (check.previousEpisode == null) {
                            CoreR.string.previous_episode_unavailable
                        } else {
                            CoreR.string.previous_episode_incomplete
                        }
                    )
            )
        },
        text = {
            Text(
                text =
                    if (check.previousEpisode == null) {
                        stringResource(CoreR.string.previous_episode_missing, previousLabel)
                    } else {
                        stringResource(
                            CoreR.string.previous_episode_completion,
                            previousLabel,
                            check.completionPercentage ?: 0,
                        )
                    }
            )
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            check.previousEpisode?.let {
                TextButton(onClick = onPlayPrevious) {
                    Text(text = stringResource(CoreR.string.play_episode, previousLabel))
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onPlaySelected) {
                    Text(text = stringResource(CoreR.string.play_episode_anyway, currentLabel))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(CoreR.string.cancel))
                }
            }
        },
    )
}

private fun episodeLabel(
    seasonNumber: Int,
    episodeNumber: Int,
    currentSeasonNumber: Int,
): String {
    return if (seasonNumber == currentSeasonNumber) {
        "Episode $episodeNumber"
    } else {
        "S%02dE%02d".format(seasonNumber, episodeNumber)
    }
}
