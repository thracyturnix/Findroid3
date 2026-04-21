package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.presentation.theme.spacings

private const val HOME_LIBRARY_COLUMNS = 4

@Composable
fun HomeLibrariesGrid(
    libraries: List<FindroidCollection>,
    itemsPadding: PaddingValues,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (libraries.isEmpty()) return

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(itemsPadding),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(CoreR.string.libraries),
            style = MaterialTheme.typography.titleMedium,
        )

        libraries.chunked(HOME_LIBRARY_COLUMNS).forEach { rowLibraries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            ) {
                rowLibraries.forEach { library ->
                    Column(
                        modifier =
                            Modifier.weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onAction(HomeAction.OnLibraryClick(library)) },
                    ) {
                        ItemPoster(
                            item = library,
                            direction = Direction.VERTICAL,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
                        Text(
                            text = library.name,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                repeat(HOME_LIBRARY_COLUMNS - rowLibraries.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
