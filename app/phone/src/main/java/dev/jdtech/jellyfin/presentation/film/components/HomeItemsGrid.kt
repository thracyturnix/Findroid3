package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.spacings
import androidx.compose.material3.MaterialTheme

private const val HOME_SECTION_COLUMNS = 2

@Composable
fun HomeItemsGrid(
    items: List<FindroidItem>,
    direction: Direction,
    onItemClick: (FindroidItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
    ) {
        items.chunked(HOME_SECTION_COLUMNS).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            ) {
                rowItems.forEach { item ->
                    ItemCard(
                        item = item,
                        direction = direction,
                        onClick = onItemClick,
                        useFixedWidth = false,
                        modifier = Modifier.weight(1f),
                    )
                }

                repeat(HOME_SECTION_COLUMNS - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
