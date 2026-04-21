package dev.jdtech.jellyfin.presentation.film.components

import android.net.Uri
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.presentation.theme.spacings

private const val HOME_LIBRARY_COLUMNS = 4

@Composable
fun HomeLibrariesGrid(
    libraries: List<FindroidCollection>,
    selectedLibrary: FindroidCollection?,
    defaultStartLibraryId: String?,
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
                    Box(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier =
                                Modifier.clip(MaterialTheme.shapes.small).combinedClickable(
                                    onClick = { onAction(HomeAction.OnLibraryClick(library)) },
                                    onLongClick = {
                                        onAction(HomeAction.OnLibraryLongClick(library))
                                    },
                                ),
                        ) {
                            HomeLibraryPoster(library = library, modifier = Modifier.fillMaxWidth())
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

                        DropdownMenu(
                            expanded = selectedLibrary?.id == library.id,
                            onDismissRequest = { onAction(HomeAction.DismissLibraryMenu) },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (defaultStartLibraryId == library.id.toString()) {
                                            stringResource(CoreR.string.default_start_screen)
                                        } else {
                                            stringResource(CoreR.string.make_default_start_screen)
                                        }
                                    )
                                },
                                onClick = {
                                    onAction(HomeAction.SetDefaultStartLibrary(library))
                                },
                            )
                        }
                    }
                }

                repeat(HOME_LIBRARY_COLUMNS - rowLibraries.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HomeLibraryPoster(library: FindroidCollection, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var imageUri = library.images.primary

    if (imageUri?.scheme == null) {
        imageUri =
            Uri.Builder()
                .appendEncodedPath("${context.filesDir}")
                .appendEncodedPath(imageUri?.path)
                .build()
    }

    AsyncImage(
        model = imageUri,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier =
            modifier
                .aspectRatio(1.77f)
                .clip(MaterialTheme.shapes.small)
                .padding(bottom = MaterialTheme.spacings.extraSmall)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer),
    )
}
