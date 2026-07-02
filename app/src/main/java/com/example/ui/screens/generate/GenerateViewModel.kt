package com.example.ui.screens.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.repository.FlashcardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TrackedFileStats(
    val path: String,
    val lastSha: String,
    val lastIndexedAt: Long,
    val flashcardCount: Int,
    val deepDiveCount: Int
)

class GenerateViewModel(private val repository: FlashcardRepository) : ViewModel() {
    
    val selectedLanguage: StateFlow<String> = repository.selectedLanguageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = "en"
    )
    
    val isGenerating: StateFlow<Boolean> = repository.isGenerating
    val generationResult: StateFlow<String?> = repository.generationResult
    val liveProgress: StateFlow<String> = repository.generationProgress

    private val _markdownFiles = MutableStateFlow<List<String>>(emptyList())
    val markdownFiles: StateFlow<List<String>> = _markdownFiles.asStateFlow()

    val trackedFiles: StateFlow<List<com.example.data.local.entities.TrackedFileEntity>> = repository.getAllTrackedFilesFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val flashcardCount: StateFlow<Int> = repository.countActiveFlashcards().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val deepDiveCount: StateFlow<Int> = repository.countActiveDeepDives().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private fun normalizePath(path: String): String {
        return path.trim('/').replace("\\", "/").lowercase()
    }

    val trackedFileStats: StateFlow<List<TrackedFileStats>> = combine(
        trackedFiles,
        repository.getAllFlashcardsFlow(),
        repository.getAllDeepDiveCardsFlow()
    ) { files, flashcards, deepDives ->
        files.map { file ->
            val fcCount = flashcards.count { normalizePath(it.source_file) == normalizePath(file.path) }
            val ddCount = deepDives.count { normalizePath(it.source_file) == normalizePath(file.path) }
            TrackedFileStats(
                path = file.path,
                lastSha = file.lastSha,
                lastIndexedAt = file.lastIndexedAt,
                flashcardCount = fcCount,
                deepDiveCount = ddCount
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedFile = MutableStateFlow<String?>(null)
    val selectedFile: StateFlow<String?> = _selectedFile.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        scanRepository()
    }

    fun triggerAutoGeneration() {
        repository.startAutoGenerationBackground(force = true)
    }

    fun regenerateFile(path: String) {
        repository.startRegeneratingSingleFile(path)
    }

    fun cancelGeneration() {
        repository.cancelGeneration()
    }

    fun selectFile(file: String?) {
        _selectedFile.value = file
    }

    fun scanRepository() {
        _isScanning.value = true
        repository.setGenerationResult(null)
        repository.setGenerationProgress("Ricerca file .md nelle cartelle configurate...")
        viewModelScope.launch {
            try {
                val files = repository.fetchMarkdownFilesFromConfiguredFolders()
                _markdownFiles.value = files
                if (files.isNotEmpty()) {
                    // Preselect first file if none is selected
                    if (_selectedFile.value == null || !files.contains(_selectedFile.value!!)) {
                        _selectedFile.value = files.first()
                    }
                    repository.setGenerationProgress("Trovati ${files.size} file Markdown nel repository.")
                } else {
                    _selectedFile.value = null
                    repository.setGenerationProgress("Nessun file .md rilevato nelle cartelle configurate o nel repository.")
                }
            } catch (e: Exception) {
                repository.setGenerationProgress("Errore durante la scansione: ${e.localizedMessage}")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun generateCards(amount: Int, type: String) {
        val fileToUse = _selectedFile.value
        if (fileToUse == null) {
            repository.setGenerationResult("Seleziona prima un file sorgente Markdown (.md) valido.")
            return
        }
        repository.startGeneratingCards(fileToUse, amount, type)
    }

    fun generateAllCardsMassively(type: String) {
        repository.startGeneratingAllCardsMassively(type)
    }

    class Factory(private val repository: FlashcardRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GenerateViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return GenerateViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
