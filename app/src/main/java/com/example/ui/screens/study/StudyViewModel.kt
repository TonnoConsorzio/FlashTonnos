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
        loadStudyQueue()
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
    val selectedLanguage: StateFlow<String> = repository.selectedLanguageFlow.stateIn(viewModelScope, SharingStarted.Lazily, "en")

    val isStudying: StateFlow<Boolean> = repository.isStudying

    fun setStudying(studying: Boolean) {
        repository.setStudying(studying)
    }

    fun clearSyncResult() {
        _syncResult.value = null
    }

    private fun translateStatus(status: String, lang: String): String {
        if (lang == "it") return status
        
        return when {
            status.startsWith("Verifica connessione") -> "Verifying connection to GitHub repository..."
            status.startsWith("Lettura cartella") -> {
                val folder = status.substringAfter("'").substringBefore("'")
                "Reading folder '$folder' on GitHub..."
            }
            status.contains("non esiste ancora") -> {
                val folder = status.substringAfter("'").substringBefore("'")
                "The folder '$folder' does not exist yet on GitHub. It will be created when you generate new cards."
            }
            status.contains("Nessuna flashcard trovata") -> {
                val folder = status.substringAfter("'").substringBefore("'")
                "No flashcards found on GitHub in folder '$folder'."
            }
            status.startsWith("Rilevate") -> {
                val count = status.substringAfter("Rilevate ").substringBefore(" card")
                "Detected $count cards on GitHub. Syncing..."
            }
            status.startsWith("Scaricamento card") -> {
                val progress = status.substringAfter("(").substringBefore(")")
                val name = status.substringAfter("): ")
                "Downloading card ($progress): $name"
            }
            status.contains("Database locale sincronizzato") -> {
                val count = status.substringAfter("sincronizzato! ").substringBefore(" flashcard")
                "Local database synced! $count flashcards loaded."
            }
            else -> status
        }
    }

    fun syncDeck() {
        val lang = selectedLanguage.value
        _isSyncing.value = true
        _syncResult.value = null
        _syncStatus.value = if (lang == "it") "Connessione a GitHub..." else "Connecting to GitHub..."
        viewModelScope.launch {
            try {
                val count = repository.syncFlashcardsFromGithub { status ->
                    _syncStatus.value = translateStatus(status, lang)
                }
                _syncResult.value = if (lang == "it") {
                    "✓ Sincronizzazione completata! $count card importate dal tuo repository."
                } else {
                    "✓ Sync completed! $count cards imported from your repository."
                }
                loadStudyQueue()
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: "Errore generico"
                _syncResult.value = if (lang == "it") {
                    "Errore: $errMsg"
                } else {
                    val englishError = errMsg
                        .replace("Credenziali incomplete! Configura GitHub prima di sincronizzare.", "Incomplete credentials! Configure GitHub before syncing.")
                        .replace("Repository o Branch non trovati", "Repository or Branch not found")
                        .replace("Errore di rete o connessione", "Network or connection error")
                    "Error: $englishError"
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun loadStudyQueue() {
        viewModelScope.launch {
            val rawCards = repository.getStudyQueueSnapshot()
            val topic = _selectedTopic.value
            val filtered = if (topic == null) {
                rawCards
            } else {
                rawCards.filter { it.topic == topic || it.topics.contains(topic) }
            }
            _studyQueue.value = filtered
            if (_currentCardIndex.value >= filtered.size) {
                _currentCardIndex.value = 0
            }
        }
    }

    init {
        // Collect raw study queue for calculating available topics list dynamically
        viewModelScope.launch {
            repository.getStudyQueue().collect { cards ->
                _rawStudyQueue.value = cards
                // If local memory study queue is empty, load it once initially
                if (_studyQueue.value.isEmpty()) {
                    loadStudyQueue()
                }
            }
        }
    }

    fun submitAnswer(card: Flashcard, selectedOption: String) {
        val isCorrect = if (card.type == "true_false") {
            val selVero = selectedOption == "Vero" || selectedOption == "True" || selectedOption == "V" || selectedOption == "T"
            val corrVero = card.correct_answer == "Vero" || card.correct_answer == "True" || card.correct_answer == "V" || card.correct_answer == "T"
            selVero == corrVero
        } else {
            selectedOption == card.correct_answer
        }
        val updatedCard = card.copy(
            times_shown = card.times_shown + 1,
            times_correct = card.times_correct + if (isCorrect) 1 else 0
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
            loadStudyQueue()
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
