package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.PreviousEpisodeCheck
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.spacings
import androidx.compose.material3.MaterialTheme

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
    BaseDialog(
        title =
            stringResource(
                if (check.previousEpisode == null) {
                    CoreR.string.previous_episode_unavailable
                } else {
                    CoreR.string.previous_episode_incomplete
                }
            ),
        onDismiss = onDismiss,
    ) { contentPadding ->
        Text(
            modifier = Modifier.padding(contentPadding),
            text =
                if (check.previousEpisode == null) {
                    stringResource(CoreR.string.previous_episode_missing, previousLabel)
                } else {
                    stringResource(
                        CoreR.string.previous_episode_completion,
                        previousLabel,
                        check.completionPercentage ?: 0,
                    )
                },
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
        Column(
            modifier = Modifier.padding(contentPadding).fillMaxWidth(),
        ) {
            check.previousEpisode?.let {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onPlayPrevious) {
                    Text(text = stringResource(CoreR.string.play_episode, previousLabel))
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onPlaySelected) {
                Text(text = stringResource(CoreR.string.play_episode_anyway, currentLabel))
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
            TextButton(modifier = Modifier.fillMaxWidth(), onClick = onDismiss) {
                Text(text = stringResource(CoreR.string.cancel))
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
    }
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
