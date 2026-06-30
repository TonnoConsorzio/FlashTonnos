package com.example.ui.screens.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.repository.FlashcardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GenerateViewModel(private val repository: FlashcardRepository) : ViewModel() {
    
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    
    private val _generationResult = MutableStateFlow<String?>(null)
    val generationResult: StateFlow<String?> = _generationResult.asStateFlow()

    private val _liveProgress = MutableStateFlow("")
    val liveProgress: StateFlow<String> = _liveProgress.asStateFlow()

    private val _markdownFiles = MutableStateFlow<List<String>>(emptyList())
    val markdownFiles: StateFlow<List<String>> = _markdownFiles.asStateFlow()

    private val _selectedFile = MutableStateFlow<String?>(null)
    val selectedFile: StateFlow<String?> = _selectedFile.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        scanRepository()
    }

    fun selectFile(file: String?) {
        _selectedFile.value = file
    }

    fun scanRepository() {
        _isScanning.value = true
        _generationResult.value = null
        _liveProgress.value = "Ricerca file .md nelle cartelle configurate..."
        viewModelScope.launch {
            try {
                val files = repository.fetchMarkdownFilesFromConfiguredFolders()
                _markdownFiles.value = files
                if (files.isNotEmpty()) {
                    // Preselect first file if none is selected
                    if (_selectedFile.value == null || !files.contains(_selectedFile.value!!)) {
                        _selectedFile.value = files.first()
                    }
                    _liveProgress.value = "Trovati ${files.size} file Markdown nel repository."
                } else {
                    _selectedFile.value = null
                    _liveProgress.value = "Nessun file .md rilevato nelle cartelle configurate o nel repository."
                }
            } catch (e: Exception) {
                _liveProgress.value = "Errore durante la scansione: ${e.localizedMessage}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun generateCards(amount: Int, type: String) {
        val fileToUse = _selectedFile.value
        if (fileToUse == null) {
            _generationResult.value = "Seleziona prima un file sorgente Markdown (.md) valido."
            return
        }

        _isGenerating.value = true
        _generationResult.value = null
        _liveProgress.value = "Avvio generazione dal file: $fileToUse..."
        viewModelScope.launch {
            try {
                val generatedCount = repository.generateCards(fileToUse, amount, type) { status ->
                    _liveProgress.value = status
                }
                _generationResult.value = "✓ $generatedCount nuove card generate correttamente dal file: $fileToUse"
            } catch (e: Exception) {
                _generationResult.value = "Errore: ${e.localizedMessage}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun generateAllCardsMassively(type: String) {
        _isGenerating.value = true
        _generationResult.value = null
        _liveProgress.value = "Scansione dei file .md nelle cartelle configurate..."
        viewModelScope.launch {
            try {
                val files = repository.fetchMarkdownFilesFromConfiguredFolders()
                if (files.isEmpty()) {
                    _generationResult.value = "Nessun file .md trovato nelle cartelle configurate. Controlla le impostazioni."
                    _isGenerating.value = false
                    return@launch
                }
                
                _liveProgress.value = "Trovati ${files.size} file .md. Avvio generazione di 5 flashcard per ciascuno..."
                var totalGenerated = 0
                for ((index, file) in files.withIndex()) {
                    _liveProgress.value = "[File ${index + 1}/${files.size}] Elaborazione di: $file..."
                    try {
                        val count = repository.generateCards(sourceFile = file, amount = 5, type = type) { status ->
                            _liveProgress.value = "[File ${index + 1}/${files.size}] $file:\n$status"
                        }
                        totalGenerated += count
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                _generationResult.value = "✓ Generazione massiva completata! Generate $totalGenerated nuove flashcard da ${files.size} file Markdown."
            } catch (e: Exception) {
                _generationResult.value = "Errore durante la generazione massiva: ${e.localizedMessage}"
            } finally {
                _isGenerating.value = false
            }
        }
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
