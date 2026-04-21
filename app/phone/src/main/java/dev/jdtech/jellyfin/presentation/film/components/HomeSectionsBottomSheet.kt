package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.presentation.theme.spacings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSectionsBottomSheet(
    librariesVisible: Boolean,
    continueWatchingVisible: Boolean,
    nextUpVisible: Boolean,
    views: List<HomeItem.ViewItem>,
    latestVisibility: Map<java.util.UUID, Boolean>,
    onLibrariesVisibilityChange: (Boolean) -> Unit,
    onContinueWatchingVisibilityChange: (Boolean) -> Unit,
    onNextUpVisibilityChange: (Boolean) -> Unit,
    onLatestVisibilityChange: (HomeItem.ViewItem, Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(),
    ) {
        LazyColumn(
            contentPadding =
                PaddingValues(
                    start = MaterialTheme.spacings.medium,
                    top = MaterialTheme.spacings.extraSmall,
                    end = MaterialTheme.spacings.medium,
                    bottom = MaterialTheme.spacings.default,
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(CoreR.string.home_sections),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            item(key = "libraries") {
                HomeSectionToggleRow(
                    title = stringResource(CoreR.string.libraries),
                    checked = librariesVisible,
                    onCheckedChange = onLibrariesVisibilityChange,
                )
            }
            item(key = "continue") {
                HomeSectionToggleRow(
                    title = stringResource(FilmR.string.continue_watching),
                    checked = continueWatchingVisible,
                    onCheckedChange = onContinueWatchingVisibilityChange,
                )
            }
            item(key = "next_up") {
                HomeSectionToggleRow(
                    title = stringResource(FilmR.string.next_up),
                    checked = nextUpVisible,
                    onCheckedChange = onNextUpVisibilityChange,
                )
            }
            items(items = views, key = { it.id }) { view ->
                HomeSectionToggleRow(
                    title = stringResource(FilmR.string.latest_library, view.view.name),
                    checked = latestVisibility[view.id] ?: true,
                    onCheckedChange = { onLatestVisibilityChange(view, it) },
                )
            }
        }
    }
}

@Composable
private fun HomeSectionToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = { Text(text = title) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}
