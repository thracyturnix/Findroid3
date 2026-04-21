package dev.jdtech.jellyfin.film.presentation.home

import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.Server
import java.util.UUID

data class HomeSectionVisibility(
    val libraries: Boolean = true,
    val continueWatching: Boolean = true,
    val nextUp: Boolean = true,
    val latestByViewId: Map<UUID, Boolean> = emptyMap(),
)

data class HomeState(
    val server: Server? = null,
    val libraries: List<FindroidCollection> = emptyList(),
    val selectedLibrary: FindroidCollection? = null,
    val defaultStartLibraryId: String? = null,
    val suggestionsSection: HomeItem.Suggestions? = null,
    val resumeSection: HomeItem.Section? = null,
    val nextUpSection: HomeItem.Section? = null,
    val views: List<HomeItem.ViewItem> = emptyList(),
    val sectionVisibility: HomeSectionVisibility = HomeSectionVisibility(),
    val isLoading: Boolean = false,
    val error: Exception? = null,
)
