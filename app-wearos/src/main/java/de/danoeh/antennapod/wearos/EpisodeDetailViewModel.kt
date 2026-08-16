package de.danoeh.antennapod.wearos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths
import de.danoeh.antennapod.wearos.sync.WearDataRepository
import de.danoeh.antennapod.wearos.sync.WearMessageSender
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EpisodeDetailUiState(
    val item: FeedItem,
    val position: Int = 0,
    val duration: Int = 0,
    val isCurrentlyPlaying: Boolean = false,
    val hasStartedPlaying: Boolean = false
)

class EpisodeDetailViewModel(application: Application, private val episode: FeedItem) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        EpisodeDetailUiState(
            item = episode,
            position = episode.getMedia()?.getPosition() ?: 0,
            duration = episode.getMedia()?.getDuration() ?: 0,
            hasStartedPlaying = WearDataRepository.nowPlaying.value
                ?.takeIf { it.item.getId() == episode.getId() }?.isPlaying == true
        )
    )
    val uiState: StateFlow<EpisodeDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            while (true) {
                WearMessageSender.send(getApplication(), WearDataPaths.NOW_PLAYING)
                delay(1.seconds)
            }
        }

        viewModelScope.launch {
            WearDataRepository.nowPlaying.collect { nowPlaying ->
                val liveData = nowPlaying?.takeIf { it.item.getId() == episode.getId() }
                val position = liveData?.item?.getMedia()?.getPosition() ?: episode.getMedia()?.getPosition() ?: 0
                val duration = liveData?.item?.getMedia()?.getDuration()?.takeIf { it > 0 } ?: episode.getMedia()?.getDuration() ?: 0
                val isCurrentlyPlaying = liveData?.isPlaying == true
                _uiState.update {
                    it.copy(
                        position = position,
                        duration = duration,
                        isCurrentlyPlaying = isCurrentlyPlaying,
                        hasStartedPlaying = it.hasStartedPlaying || isCurrentlyPlaying
                    )
                }
            }
        }
    }

    fun play() {
        viewModelScope.launch(Dispatchers.IO) {
            WearMessageSender.send(getApplication(), WearDataPaths.playPath(episode.getId()))
        }
    }

    fun pause() {
        viewModelScope.launch(Dispatchers.IO) { WearMessageSender.send(getApplication(), WearDataPaths.PAUSE) }
    }

    fun skipForward() {
        viewModelScope.launch(Dispatchers.IO) { WearMessageSender.send(getApplication(), WearDataPaths.SKIP_FORWARD) }
    }

    fun skipBackward() {
        viewModelScope.launch(Dispatchers.IO) { WearMessageSender.send(getApplication(), WearDataPaths.SKIP_BACKWARD) }
    }

    fun openOnPhone() {
        viewModelScope.launch(Dispatchers.IO) {
            WearMessageSender.send(getApplication(), WearDataPaths.openOnPhonePath(episode.getId()))
        }
    }

    companion object {
        fun factory(episode: FeedItem): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EpisodeDetailViewModel(
                    checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY)),
                    episode
                )
            }
        }
    }
}
