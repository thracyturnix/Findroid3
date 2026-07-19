package dev.jdtech.jellyfin.film.presentation.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistViewModel @Inject constructor(private val repository: JellyfinRepository) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistState())
    val state = _state.asStateFlow()

    private lateinit var playlistId: UUID

    fun loadItems(playlistId: UUID) {
        this.playlistId = playlistId
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true, error = null))
            try {
                _state.emit(
                    _state.value.copy(
                        items = repository.getPlaylistItems(playlistId),
                        isLoading = false,
                    )
                )
            } catch (e: Exception) {
                _state.emit(_state.value.copy(isLoading = false, error = e))
            }
        }
    }

    fun onAction(action: PlaylistAction) {
        if (action is PlaylistAction.OnRetryClick && ::playlistId.isInitialized) {
            loadItems(playlistId)
        }
    }
}
