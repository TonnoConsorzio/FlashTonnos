package com.example.ui.screens.deepdive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.di.AppContainer
import com.example.domain.models.DeepDiveCard
import com.example.domain.usecases.RecommendationEngineUseCase
import com.example.domain.usecases.TrackDwellTimeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel per lo schermo degli Approfondimenti (Deep Dive).
 * Gestisce il caricamento del feed personalizzato tramite l'algoritmo di raccomandazione,
 * il tracciamento del tempo di lettura (dwell time) e i filtri di argomento.
 */
class DeepDiveViewModel(
    private val recommendationUseCase: RecommendationEngineUseCase,
    private val trackDwellTimeUseCase: TrackDwellTimeUseCase,
    private val appContainer: AppContainer
) : ViewModel() {

    private val _feedState = MutableStateFlow<List<DeepDiveCard>>(emptyList())
    val feedState: StateFlow<List<DeepDiveCard>> = _feedState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _availableTopics = MutableStateFlow<List<String>>(emptyList())
    val availableTopics: StateFlow<List<String>> = _availableTopics.asStateFlow()

    private val _selectedTopic = MutableStateFlow<String?>(null)
    val selectedTopic: StateFlow<String?> = _selectedTopic.asStateFlow()

    init {
        loadFeed()
        loadAvailableTopics()
    }

    fun loadFeed() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Genera il feed personalizzato di 20 card
                val cards = recommendationUseCase.generateFeed(count = 20)
                
                // Filtra per argomento se selezionato
                val filteredCards = if (_selectedTopic.value != null) {
                    cards.filter { it.topic == _selectedTopic.value }
                } else {
                    cards
                }

                _feedState.value = filteredCards
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun loadAvailableTopics() {
        viewModelScope.launch {
            try {
                // Legge tutti i topic dalle card presenti in Room
                val allCards = appContainer.flashcardRepository.getAllDeepDiveCardsSnapshot()
                val topics = allCards.map { it.topic }.distinct().filter { it.isNotBlank() }
                _availableTopics.value = topics
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectTopic(topic: String?) {
        _selectedTopic.value = topic
        loadFeed()
    }

    /**
     * Registra il dwell time dell'utente e il feedback per una determinata card.
     */
    fun trackInteraction(
        card: DeepDiveCard,
        dwellTimeMs: Long,
        explicitFeedback: Int
    ) {
        viewModelScope.launch {
            try {
                trackDwellTimeUseCase.execute(
                    cardId = card.id,
                    topic = card.topic,
                    subtopic = card.subtopic,
                    tags = card.tags,
                    dwellTimeMs = dwellTimeMs,
                    explicitFeedback = explicitFeedback
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    class Factory(private val appContainer: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val recommendationUseCase = RecommendationEngineUseCase(appContainer.flashcardRepository)
            val trackDwellTimeUseCase = TrackDwellTimeUseCase(
                deepDiveDao = appContainer.deepDiveDao,
                githubApi = appContainer.githubApiService,
                appPreferences = appContainer.appPreferences
            )
            return DeepDiveViewModel(
                recommendationUseCase = recommendationUseCase,
                trackDwellTimeUseCase = trackDwellTimeUseCase,
                appContainer = appContainer
            ) as T
        }
    }
}
