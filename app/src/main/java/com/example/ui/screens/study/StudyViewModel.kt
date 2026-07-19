package com.example.ui.screens.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.models.Flashcard
import com.example.domain.repository.FlashcardRepository
import com.example.domain.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StudyViewModel(
    private val repository: FlashcardRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _rawStudyQueue = MutableStateFlow<List<Flashcard>>(emptyList())
    private val _studyQueue = MutableStateFlow<List<Flashcard>>(emptyList())
    val studyQueue: StateFlow<List<Flashcard>> = _studyQueue.asStateFlow()
    
    private val _selectedTopic = MutableStateFlow<String?>(null)
    val selectedTopic: StateFlow<String?> = _selectedTopic.asStateFlow()

    val availableTopics: StateFlow<List<String>> = _rawStudyQueue.map { cards ->
        cards.map { it.topic }.distinct().filter { it.isNotBlank() }.sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTopic(topic: String?) {
        _selectedTopic.value = topic
        loadStudyQueue()
    }

    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex.asStateFlow()

    fun setCurrentCardIndex(index: Int) {
        if (index in _studyQueue.value.indices) {
            _currentCardIndex.value = index
            _isFlipped.value = false
        }
    }
    
    private val _isFlipped = MutableStateFlow(false)
    val isFlipped: StateFlow<Boolean> = _isFlipped.asStateFlow()

    private val _currentStreak = MutableStateFlow(0)
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _syncResult = MutableStateFlow<String?>(null)
    val syncResult: StateFlow<String?> = _syncResult.asStateFlow()

    val demoInitializedFlow = repository.demoInitializedFlow
    val selectedLanguage: StateFlow<String> = repository.selectedLanguageFlow.stateIn(viewModelScope, SharingStarted.Lazily, "en")
    val studyMode: StateFlow<String> = repository.studyModeFlow.stateIn(viewModelScope, SharingStarted.Lazily, "classic")

    private val _isStudying = MutableStateFlow(false)
    val isStudying: StateFlow<Boolean> = _isStudying.asStateFlow()

    fun setStudying(studying: Boolean) {
        _isStudying.value = studying
        if (studying) {
            loadStudyQueue()
        }
    }

    fun clearSyncResult() {
        _syncResult.value = null
    }

    private fun translateStatus(status: String, lang: String): String {
        if (lang == "it") return status
        
        return when {
            status.startsWith("Connessione") -> "Connecting to GitHub..."
            status.startsWith("Scarico indice") -> "Downloading index..."
            status.startsWith("Scarico:") -> status.replace("Scarico:", "Downloading:")
            else -> status
        }
    }

    fun syncDeck() {
        val lang = selectedLanguage.value
        _syncResult.value = null
        viewModelScope.launch {
            syncRepository.sync()
        }
    }

    fun loadStudyQueue(explicitMode: String? = null) {
        viewModelScope.launch {
            val rawCards = repository.getStudyQueueSnapshot()
            val topic = _selectedTopic.value
            val currentMode = explicitMode ?: repository.studyModeFlow.first()

            var filtered = if (topic == null) {
                rawCards
            } else {
                rawCards.filter { it.topic == topic }
            }

            // Filter based on study modes
            filtered = when (currentMode) {
                "true_false" -> filtered.filter { it.type == "true_false" }
                "multiple_choice" -> filtered.filter { it.type == "multiple_choice" }
                else -> filtered // classic includes all
            }

            // Randomize/shuffle the questions so they are from different topics and not repetitive
            filtered = filtered.shuffled()

            _studyQueue.value = filtered
            if (_currentCardIndex.value >= filtered.size) {
                _currentCardIndex.value = 0
            }
        }
    }

    init {
        _currentStreak.value = repository.getCurrentCorrectStreak()

        // Collect raw study queue for calculating available topics list dynamically
        viewModelScope.launch {
            repository.getStudyQueue().collect { cards ->
                _rawStudyQueue.value = cards
                if (cards.isEmpty()) {
                    _studyQueue.value = emptyList()
                    _currentCardIndex.value = 0
                } else if (_studyQueue.value.isEmpty()) {
                    loadStudyQueue()
                }
            }
        }

        // Collect study mode changes to refresh study queue dynamically
        viewModelScope.launch {
            repository.studyModeFlow.collect { mode ->
                loadStudyQueue(mode)
            }
        }

        // Collect syncRepository syncState to drive StudyScreen syncing flow smoothly
        viewModelScope.launch {
            syncRepository.syncState.collect { state ->
                when (state) {
                    is SyncRepository.SyncState.Idle -> {
                        _isSyncing.value = false
                    }
                    is SyncRepository.SyncState.Syncing -> {
                        _isSyncing.value = true
                        _syncStatus.value = translateStatus(state.step, selectedLanguage.value)
                    }
                    is SyncRepository.SyncState.Success -> {
                        _isSyncing.value = false
                        val totalNew = state.newFlashcards + state.newDeepDives
                        _syncResult.value = if (selectedLanguage.value == "it") {
                            if (totalNew == 0) {
                                "✓ Tutto aggiornato! Totale nel deck: ${state.totalFlashcards} flashcard e ${state.totalDeepDives} approfondimenti."
                            } else {
                                "✓ Sincronizzazione completata! $totalNew nuovi elementi importati. Totale nel deck: ${state.totalFlashcards} flashcard, ${state.totalDeepDives} approfondimenti."
                            }
                        } else {
                            if (totalNew == 0) {
                                "✓ Everything up to date! Total in deck: ${state.totalFlashcards} flashcards and ${state.totalDeepDives} deep dives."
                            } else {
                                "✓ Sync completed! $totalNew new items imported. Total in deck: ${state.totalFlashcards} flashcards, ${state.totalDeepDives} deep-dives."
                            }
                        }
                        loadStudyQueue()
                    }
                    is SyncRepository.SyncState.Error -> {
                        _isSyncing.value = false
                        _syncResult.value = if (selectedLanguage.value == "it") {
                            "Errore: ${state.message}"
                        } else {
                            "Error: ${state.message}"
                        }
                    }
                }
            }
        }
    }

    fun submitAnswer(card: Flashcard, selectedOption: String) {
        val isCorrect = if (card.type == "true_false") {
            val selVero = selectedOption == "Vero" || selectedOption == "True" || selectedOption == "V" || selectedOption == "T"
            val corrVero = card.correctAnswer == "Vero" || card.correctAnswer == "True" || card.correctAnswer == "V" || card.correctAnswer == "T"
            selVero == corrVero
        } else {
            selectedOption == card.correctAnswer
        }
        val updatedCard = card.copy(
            timesShown = card.timesShown + 1,
            timesCorrect = card.timesCorrect + if (isCorrect) 1 else 0
        )
        
        // Update in memory list immediately so current card does not shift or reorder during review
        val currentQueue = _studyQueue.value.toMutableList()
        val index = currentQueue.indexOfFirst { it.id == card.id }
        if (index != -1) {
            currentQueue[index] = updatedCard
            _studyQueue.value = currentQueue
        }
        
        _isFlipped.value = true
        
        repository.recordAnswer(isCorrect)
        _currentStreak.value = repository.getCurrentCorrectStreak()
        
        viewModelScope.launch {
            repository.updateCard(updatedCard)
        }
    }

    fun discardCard(card: Flashcard) {
        viewModelScope.launch {
            repository.deleteCardById(card.id)
            val currentQueue = _studyQueue.value.toMutableList()
            val index = currentQueue.indexOfFirst { it.id == card.id }
            if (index != -1) {
                currentQueue.removeAt(index)
                _studyQueue.value = currentQueue
                if (_currentCardIndex.value >= currentQueue.size) {
                    _currentCardIndex.value = 0
                }
            }
            _isFlipped.value = false
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
        branch: String
    ) {
        viewModelScope.launch {
            repository.updateCredentials(pat, owner, repo, branch)
        }
    }

    fun initializeDemoDeck() {
        viewModelScope.launch {
            repository.initializeDemoDeck()
            loadStudyQueue()
        }
    }

    class Factory(
        private val repository: FlashcardRepository,
        private val syncRepository: SyncRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StudyViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return StudyViewModel(repository, syncRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
