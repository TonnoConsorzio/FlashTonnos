package com.example.ui.screens.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.models.Flashcard
import com.example.domain.repository.FlashcardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class StudyViewModel(private val repository: FlashcardRepository) : ViewModel() {

    private val _rawStudyQueue = MutableStateFlow<List<Flashcard>>(emptyList())
    private val _studyQueue = MutableStateFlow<List<Flashcard>>(emptyList())
    val studyQueue: StateFlow<List<Flashcard>> = _studyQueue.asStateFlow()
    
    private val _selectedTopic = MutableStateFlow<String?>(null)
    val selectedTopic: StateFlow<String?> = _selectedTopic.asStateFlow()

    val availableTopics: StateFlow<List<String>> = _rawStudyQueue.map { cards ->
        cards.flatMap { it.topics }.distinct().filter { it.isNotBlank() }.sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTopic(topic: String?) {
        _selectedTopic.value = topic
    }

    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex.asStateFlow()
    
    private val _isFlipped = MutableStateFlow(false)
    val isFlipped: StateFlow<Boolean> = _isFlipped.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _syncResult = MutableStateFlow<String?>(null)
    val syncResult: StateFlow<String?> = _syncResult.asStateFlow()

    val demoInitializedFlow = repository.demoInitializedFlow

    fun clearSyncResult() {
        _syncResult.value = null
    }

    fun syncDeck() {
        _isSyncing.value = true
        _syncResult.value = null
        _syncStatus.value = "Connessione a GitHub..."
        viewModelScope.launch {
            try {
                val count = repository.syncFlashcardsFromGithub { status ->
                    _syncStatus.value = status
                }
                _syncResult.value = "✓ Sincronizzazione completata! $count card importate dal tuo repository."
            } catch (e: Exception) {
                _syncResult.value = "Errore: ${e.localizedMessage ?: "Errore generico"}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    init {
        viewModelScope.launch {
            combine(_rawStudyQueue, _selectedTopic) { cards, topic ->
                if (topic == null) cards else cards.filter { it.topics.contains(topic) }
            }.collect { filteredCards ->
                _studyQueue.value = filteredCards
                if (_currentCardIndex.value >= filteredCards.size) {
                    _currentCardIndex.value = 0
                }
            }
        }
        viewModelScope.launch {
            repository.getStudyQueue().collect { cards ->
                _rawStudyQueue.value = cards
            }
        }
    }

    fun submitAnswer(card: Flashcard, selectedOption: String) {
        val isCorrect = (selectedOption == card.correct_answer)
        val updatedCard = card.copy(
            times_shown = card.times_shown + 1,
            times_correct = card.times_correct + if (isCorrect) 1 else 0
        )
        _isFlipped.value = true
        
        repository.recordAnswer(isCorrect)
        
        viewModelScope.launch {
            repository.updateCard(updatedCard)
        }
    }

    fun nextCard() {
        if (_studyQueue.value.isNotEmpty()) {
            _currentCardIndex.value = (_currentCardIndex.value + 1) % _studyQueue.value.size
        }
        _isFlipped.value = false
    }

    fun skipCard() {
        nextCard()
    }

    fun postponeCard() {
        val queue = _studyQueue.value
        val index = _currentCardIndex.value
        if (queue.isNotEmpty() && index < queue.size) {
            val card = queue[index]
            val mutableQueue = queue.toMutableList()
            mutableQueue.removeAt(index)
            // Insert 4 positions later or at the end
            val insertIndex = (index + 4).coerceAtMost(mutableQueue.size)
            mutableQueue.add(insertIndex, card)
            _studyQueue.value = mutableQueue
            if (_currentCardIndex.value >= mutableQueue.size) {
                _currentCardIndex.value = 0
            }
        }
        _isFlipped.value = false
    }

    fun getGithubPat(): String = repository.getGithubPat()
    fun getOpenRouterKey(): String = repository.getOpenRouterKey()

    fun getGithubOwner(onResult: (String) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getGithubOwner())
        }
    }

    fun getGithubRepo(onResult: (String) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getGithubRepo())
        }
    }

    fun getGithubBranch(onResult: (String) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getGithubBranch())
        }
    }

    fun updateCredentials(
        pat: String,
        owner: String,
        repo: String,
        branch: String,
        openRouterKey: String
    ) {
        viewModelScope.launch {
            repository.updateCredentials(pat, owner, repo, branch, openRouterKey)
        }
    }

    fun initializeDemoDeck() {
        viewModelScope.launch {
            repository.initializeDemoDeck()
        }
    }

    class Factory(private val repository: FlashcardRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StudyViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return StudyViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
