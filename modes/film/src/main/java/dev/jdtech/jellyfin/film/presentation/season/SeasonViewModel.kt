package dev.jdtech.jellyfin.film.presentation.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ItemFields

@HiltViewModel
class SeasonViewModel @Inject constructor(private val repository: JellyfinRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(SeasonState())
    val state = _state.asStateFlow()

    lateinit var seasonId: UUID
    private var requestedStartFromBeginning = false

    fun loadSeason(seasonId: UUID) {
        this.seasonId = seasonId
        viewModelScope.launch {
            try {
                val season = repository.getSeason(seasonId)
                val episodes =
                    repository.getEpisodes(
                        seriesId = season.seriesId,
                        seasonId = seasonId,
                        fields = listOf(ItemFields.OVERVIEW),
                    )
                _state.emit(_state.value.copy(season = season, episodes = episodes))
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    fun onAction(action: SeasonAction) {
        when (action) {
            is SeasonAction.Play -> {
                viewModelScope.launch {
                    requestedStartFromBeginning = action.startFromBeginning
                    val episode = _state.value.episodes.firstOrNull { !it.missing } ?: return@launch
                    val previousEpisodeCheck = repository.getPreviousEpisodeCheck(episode.id)
                    if (previousEpisodeCheck == null) {
                        requestPlayback(episode.id, action.startFromBeginning)
                    } else {
                        _state.emit(_state.value.copy(previousEpisodeCheck = previousEpisodeCheck))
                    }
                }
            }
            is SeasonAction.PlayPreviousEpisode -> {
                _state.value.previousEpisodeCheck?.previousEpisode?.let {
                    requestPlayback(it.id, false)
                }
            }
            is SeasonAction.PlaySelectedEpisodeAnyway -> {
                _state.value.previousEpisodeCheck?.currentEpisode?.let {
                    requestPlayback(it.id, requestedStartFromBeginning)
                }
            }
            is SeasonAction.DismissPreviousEpisodeCheck -> {
                _state.value = _state.value.copy(previousEpisodeCheck = null)
            }
            is SeasonAction.PlaybackStarted -> {
                _state.value = _state.value.copy(playbackRequest = null)
            }
            is SeasonAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(seasonId)
                    loadSeason(seasonId)
                }
            }
            is SeasonAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(seasonId)
                    loadSeason(seasonId)
                }
            }
            is SeasonAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(seasonId)
                    loadSeason(seasonId)
                }
            }
            is SeasonAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(seasonId)
                    loadSeason(seasonId)
                }
            }
            else -> Unit
        }
    }

    private fun requestPlayback(episodeId: UUID, startFromBeginning: Boolean) {
        _state.value =
            _state.value.copy(
                previousEpisodeCheck = null,
                playbackRequest = SeasonPlaybackRequest(episodeId, startFromBeginning),
            )
    }
}
