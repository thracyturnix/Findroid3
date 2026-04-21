package dev.jdtech.jellyfin.film.presentation.home

import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidItem
import java.util.UUID

sealed interface HomeAction {
    data class OnItemClick(val item: FindroidItem) : HomeAction

    data class OnLibraryClick(val library: FindroidCollection) : HomeAction

    data class OnLibraryLongClick(val library: FindroidCollection) : HomeAction

    data class SetDefaultStartLibrary(val library: FindroidCollection) : HomeAction

    data object DismissLibraryMenu : HomeAction

    data object OnRetryClick : HomeAction

    data object OnSearchClick : HomeAction

    data object OnSettingsClick : HomeAction

    data object OnManageServers : HomeAction

    data class SetLibrariesVisibility(val visible: Boolean) : HomeAction

    data class SetContinueWatchingVisibility(val visible: Boolean) : HomeAction

    data class SetNextUpVisibility(val visible: Boolean) : HomeAction

    data class SetLatestVisibility(val viewId: UUID, val visible: Boolean) : HomeAction
}
