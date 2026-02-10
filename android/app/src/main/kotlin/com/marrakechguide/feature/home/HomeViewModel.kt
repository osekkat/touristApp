package com.marrakechguide.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marrakechguide.core.database.entity.FavoriteEntity
import com.marrakechguide.core.database.entity.RecentEntity
import com.marrakechguide.core.model.Phrase
import com.marrakechguide.core.model.Tip
import com.marrakechguide.core.repository.FavoritesRepository
import com.marrakechguide.core.repository.PhraseRepository
import com.marrakechguide.core.repository.RecentsRepository
import com.marrakechguide.core.repository.TipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Offline status indicator for the home screen.
 */
enum class OfflineStatus {
    READY,
    DOWNLOAD_RECOMMENDED,
    UPDATE_AVAILABLE
}

/**
 * An active route in progress.
 */
data class ActiveRoute(
    val id: String,
    val currentStopName: String,
    val nextStopName: String,
    val currentStep: Int,
    val totalSteps: Int
)

/**
 * A saved/favorited item displayed on the home screen.
 */
data class SavedItem(
    val id: String,
    val title: String,
    val type: String
)

/**
 * A recently viewed item displayed on the home screen.
 */
data class RecentItem(
    val id: String,
    val title: String,
    val type: String,
    val viewedAt: String
)

/**
 * UI state for the home screen.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val offlineStatus: OfflineStatus = OfflineStatus.READY,
    val activeRoute: ActiveRoute? = null,
    val tipOfTheDay: Tip? = null,
    val phraseOfTheDay: Phrase? = null,
    val savedItems: List<SavedItem> = emptyList(),
    val recentItems: List<RecentItem> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel for the Home Screen.
 * Loads and provides data for quick access features and daily rotating content.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tipRepository: TipRepository,
    private val phraseRepository: PhraseRepository,
    private val favoritesRepository: FavoritesRepository,
    private val recentsRepository: RecentsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    /**
     * Loads all home screen data.
     */
    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Load all data concurrently
                val tipDeferred = async { loadTipOfTheDay() }
                val phraseDeferred = async { loadPhraseOfTheDay() }
                val favoritesDeferred = async { loadSavedItems() }
                val recentsDeferred = async { loadRecentItems() }

                val tip = tipDeferred.await()
                val phrase = phraseDeferred.await()
                val favorites = favoritesDeferred.await()
                val recents = recentsDeferred.await()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        tipOfTheDay = tip,
                        phraseOfTheDay = phrase,
                        savedItems = favorites,
                        recentItems = recents,
                        offlineStatus = checkOfflineStatus(tip, phrase)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: "Failed to load data"
                    )
                }
            }
        }
    }

    /**
     * Refreshes home screen data (pull-to-refresh).
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadData()
        }
    }

    /**
     * Selects a tip for today based on date hash.
     */
    private suspend fun loadTipOfTheDay(): Tip? {
        val tips = tipRepository.getAllTipsOnce()
        if (tips.isEmpty()) return null

        // Use date to deterministically select a tip
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val index = dayOfYear % tips.size
        return tips[index]
    }

    /**
     * Selects a phrase for today based on date hash.
     */
    private suspend fun loadPhraseOfTheDay(): Phrase? {
        val phrases = phraseRepository.getAllPhrasesOnce()
        if (phrases.isEmpty()) return null

        // Use date to deterministically select a phrase (offset by 7 to differ from tip)
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) + 7
        val index = dayOfYear % phrases.size
        return phrases[index]
    }

    /**
     * Loads saved/favorited items.
     */
    private suspend fun loadSavedItems(): List<SavedItem> {
        val favorites = favoritesRepository.getFavoritesOnce()
        return favorites.take(10).map { favorite ->
            SavedItem(
                id = "${favorite.contentType}-${favorite.contentId}",
                title = favorite.contentId, // Would need to resolve actual title from content
                type = favorite.contentType.replaceFirstChar { it.uppercase() }
            )
        }
    }

    /**
     * Loads recently viewed items.
     */
    private suspend fun loadRecentItems(): List<RecentItem> {
        val recents = recentsRepository.getRecentsOnce(5)
        return recents.map { recent ->
            RecentItem(
                id = "${recent.contentType}-${recent.contentId}",
                title = recent.contentId, // Would need to resolve actual title from content
                type = recent.contentType.replaceFirstChar { it.uppercase() },
                viewedAt = recent.viewedAt
            )
        }
    }

    /**
     * Checks the offline status of the app.
     */
    private fun checkOfflineStatus(tip: Tip?, phrase: Phrase?): OfflineStatus {
        // TODO: Check actual content.db integrity and pack status
        // For now, assume offline ready if we have content
        return if (tip != null && phrase != null) {
            OfflineStatus.READY
        } else {
            OfflineStatus.DOWNLOAD_RECOMMENDED
        }
    }
}
