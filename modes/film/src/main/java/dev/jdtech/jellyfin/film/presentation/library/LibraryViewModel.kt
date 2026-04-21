package dev.jdtech.jellyfin.film.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind

@HiltViewModel
class LibraryViewModel
@Inject
constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    lateinit var parentId: UUID
    lateinit var libraryType: CollectionType

    lateinit var sortBy: SortBy
    lateinit var sortOrder: SortOrder
    var showUnplayedOnly = false

    fun setup(parentId: UUID, libraryType: CollectionType) {
        this.parentId = parentId
        this.libraryType = libraryType
    }

    fun loadItems() {
        val itemType =
            when (libraryType) {
                CollectionType.Movies -> listOf(BaseItemKind.MOVIE)
                CollectionType.TvShows -> listOf(BaseItemKind.SERIES)
                CollectionType.BoxSets -> listOf(BaseItemKind.BOX_SET)
                CollectionType.Mixed,
                CollectionType.Folders ->
                    listOf(BaseItemKind.FOLDER, BaseItemKind.MOVIE, BaseItemKind.SERIES)
                else -> null
            }

        val recursive = itemType == null || !itemType.contains(BaseItemKind.FOLDER)

        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true, error = null))

            initSorting()

            try {
                val items =
                    jellyfinRepository
                        .getItemsPaging(
                            parentId = parentId,
                            includeTypes = itemType,
                            recursive = recursive,
                            sortBy =
                                if (
                                    libraryType == CollectionType.TvShows &&
                                        sortBy == SortBy.DATE_PLAYED
                                )
                                    SortBy.SERIES_DATE_PLAYED
                                else sortBy, // Jellyfin uses a different enum for sorting series by
                            // data played
                            sortOrder = sortOrder,
                            isUnplayed = showUnplayedOnly,
                        )
                        .cachedIn(viewModelScope)
                _state.emit(_state.value.copy(items = items))
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    private suspend fun initSorting() {
        if (!::sortBy.isInitialized || !::sortOrder.isInitialized) {
            sortBy = SortBy.fromString(appPreferences.getValue(appPreferences.sortBy))
            sortOrder = SortOrder.fromString(appPreferences.getValue(appPreferences.sortOrder))
            showUnplayedOnly = appPreferences.getValue(appPreferences.unplayedFilter)
            _state.emit(
                _state.value.copy(
                    sortBy = sortBy,
                    sortOrder = sortOrder,
                    showUnplayedOnly = showUnplayedOnly,
                )
            )
        }
    }

    private fun setSorting(sortBy: SortBy, sortOrder: SortOrder) {
        this.sortBy = sortBy
        this.sortOrder = sortOrder
        viewModelScope.launch {
            _state.emit(_state.value.copy(sortBy = sortBy, sortOrder = sortOrder))
            appPreferences.setValue(appPreferences.sortBy, sortBy.toString())
            appPreferences.setValue(appPreferences.sortOrder, sortOrder.toString())
        }
    }

    private fun canShowPlayedMenu(item: FindroidItem): Boolean {
        return item is FindroidMovie || item is FindroidShow
    }

    private suspend fun requestRefresh() {
        _state.emit(_state.value.copy(refreshVersion = _state.value.refreshVersion + 1))
    }

    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.ChangeSorting -> {
                if (action.sortBy != this.sortBy || action.sortOrder != this.sortOrder) {
                    setSorting(sortBy = action.sortBy, sortOrder = action.sortOrder)
                    loadItems()
                }
            }
            is LibraryAction.ToggleUnplayedFilter -> {
                showUnplayedOnly = !showUnplayedOnly
                viewModelScope.launch {
                    _state.emit(_state.value.copy(showUnplayedOnly = showUnplayedOnly))
                    appPreferences.setValue(appPreferences.unplayedFilter, showUnplayedOnly)
                }
                loadItems()
            }
            is LibraryAction.OnItemLongClick -> {
                if (canShowPlayedMenu(action.item)) {
                    viewModelScope.launch {
                        _state.emit(_state.value.copy(selectedItem = action.item))
                    }
                }
            }
            is LibraryAction.DismissItemMenu -> {
                viewModelScope.launch {
                    _state.emit(_state.value.copy(selectedItem = null))
                }
            }
            is LibraryAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    jellyfinRepository.markAsPlayed(action.item.id)
                    _state.emit(_state.value.copy(selectedItem = null))
                    requestRefresh()
                }
            }
            is LibraryAction.MarkAsUnplayed -> {
                viewModelScope.launch {
                    jellyfinRepository.markAsUnplayed(action.item.id)
                    _state.emit(_state.value.copy(selectedItem = null))
                    requestRefresh()
                }
            }
            else -> Unit
        }
    }
}
