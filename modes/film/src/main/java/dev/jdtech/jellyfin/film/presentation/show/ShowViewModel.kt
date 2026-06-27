package dev.jdtech.jellyfin.film.presentation.show

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PersonKind

@HiltViewModel
class ShowViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(ShowState())
    val state = _state.asStateFlow()

    lateinit var showId: UUID
    private var requestedStartFromBeginning = false

    fun loadShow(showId: UUID) {
        this.showId = showId
        viewModelScope.launch {
            try {
                val show = repository.getShow(showId)
                val nextUp = getNextUp(showId)
                val seasons = repository.getSeasons(showId)
                val actors = getActors(show)
                val director = getDirector(show)
                val writers = getWriters(show)
                _state.emit(
                    _state.value.copy(
                        show = show,
                        nextUp = nextUp,
                        seasons = seasons,
                        actors = actors,
                        director = director,
                        writers = writers,
                        subtitleMode =
                            appPreferences.getValue(
                                appPreferences.showSubtitleMode(showId.toString())
                            ),
                    )
                )
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    private suspend fun getNextUp(showId: UUID): FindroidEpisode? {
        val nextUpItems = repository.getNextUp(showId)
        return nextUpItems.getOrNull(0)
    }

    private suspend fun getActors(item: FindroidShow): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.ACTOR }
        }
    }

    private suspend fun getDirector(item: FindroidShow): FindroidItemPerson? {
        return withContext(Dispatchers.Default) {
            item.people.firstOrNull { it.type == PersonKind.DIRECTOR }
        }
    }

    private suspend fun getWriters(item: FindroidShow): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.WRITER }
        }
    }

    fun onAction(action: ShowAction) {
        when (action) {
            is ShowAction.Play -> {
                viewModelScope.launch {
                    requestedStartFromBeginning = action.startFromBeginning
                    val nextUpEpisode = repository.getNextUp(showId).firstOrNull()
                    if (nextUpEpisode == null) {
                        requestPlayback(showId, BaseItemKind.SERIES, action.startFromBeginning)
                    } else {
                        val previousEpisodeCheck =
                            repository.getPreviousEpisodeCheck(nextUpEpisode.id)
                        if (previousEpisodeCheck == null) {
                            requestPlayback(
                                nextUpEpisode.id,
                                BaseItemKind.EPISODE,
                                action.startFromBeginning,
                            )
                        } else {
                            _state.emit(
                                _state.value.copy(previousEpisodeCheck = previousEpisodeCheck)
                            )
                        }
                    }
                }
            }
            is ShowAction.PlayPreviousEpisode -> {
                _state.value.previousEpisodeCheck?.previousEpisode?.let {
                    requestPlayback(it.id, BaseItemKind.EPISODE, false)
                }
            }
            is ShowAction.PlaySelectedEpisodeAnyway -> {
                _state.value.previousEpisodeCheck?.currentEpisode?.let {
                    requestPlayback(it.id, BaseItemKind.EPISODE, requestedStartFromBeginning)
                }
            }
            is ShowAction.DismissPreviousEpisodeCheck -> {
                _state.value = _state.value.copy(previousEpisodeCheck = null)
            }
            is ShowAction.PlaybackStarted -> {
                _state.value = _state.value.copy(playbackRequest = null)
            }
            is ShowAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.SelectSubtitleMode -> {
                appPreferences.setValue(
                    appPreferences.showSubtitleMode(showId.toString()),
                    action.mode,
                )
                viewModelScope.launch { _state.emit(_state.value.copy(subtitleMode = action.mode)) }
            }
            else -> Unit
        }
    }

    private fun requestPlayback(
        itemId: UUID,
        itemKind: BaseItemKind,
        startFromBeginning: Boolean,
    ) {
        _state.value =
            _state.value.copy(
                previousEpisodeCheck = null,
                playbackRequest = ShowPlaybackRequest(itemId, itemKind, startFromBeginning),
            )
    }
}
