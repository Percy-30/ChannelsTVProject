package com.streamingtv.channelstv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamingtv.channelstv.data.Channel
import com.streamingtv.channelstv.data.Event
import com.streamingtv.channelstv.data.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * All available category tabs. "Agenda" is special (shows events, not channels).
 */
enum class Category(val displayName: String) {
    AGENDA("Agenda"),
    DEPORTES("Deportes"),
    NOTICIAS("Noticias"),
    PELICULAS("Peliculas"),
    INFANTIL("Infantil"),
    VARIADOS("Variados")
}

data class HomeUiState(
    val channels: List<Channel> = emptyList(),
    val events: List<Event> = emptyList(),
    val selectedCategory: Category = Category.AGENDA,
    val isLoading: Boolean = true,
    val error: String? = null
)

class MainViewModel : ViewModel() {

    private val repository = FirebaseRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadChannels()
        loadEvents()
    }

    private fun loadChannels() {
        viewModelScope.launch {
            repository.getChannels()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
                .collect { channels ->
                    _uiState.value = _uiState.value.copy(
                        channels = channels,
                        isLoading = false
                    )
                }
        }
    }

    private fun loadEvents() {
        viewModelScope.launch {
            repository.getEvents()
                .catch { /* ignore, agenda is non-critical */ }
                .collect { events ->
                    _uiState.value = _uiState.value.copy(events = events)
                }
        }
    }

    fun selectCategory(category: Category) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    /** Channels filtered by the currently selected category */
    fun getChannelsForCategory(category: Category): List<Channel> {
        return _uiState.value.channels.filter { channel ->
            channel.category.equals(category.displayName, ignoreCase = true)
        }
    }
}
