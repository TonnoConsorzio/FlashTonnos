package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppPreferences
import com.example.domain.repository.FlashcardRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: AppPreferences,
    private val repository: FlashcardRepository
) : ViewModel() {

    val githubOwner: StateFlow<String> = preferences.githubOwnerFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val githubRepo: StateFlow<String> = preferences.githubRepoFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val githubBranch: StateFlow<String> = preferences.githubBranchFlow.stateIn(viewModelScope, SharingStarted.Lazily, "main")
    val openRouterModel: StateFlow<String> = preferences.openRouterModelFlow.stateIn(viewModelScope, SharingStarted.Lazily, "openrouter/auto")
    val selectedTheme: StateFlow<Int> = preferences.selectedThemeFlow.stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val studyMode: StateFlow<String> = preferences.studyModeFlow.stateIn(viewModelScope, SharingStarted.Lazily, "classic")
    val sourceFolders: StateFlow<Set<String>> = preferences.sourceFoldersFlow.stateIn(viewModelScope, SharingStarted.Lazily, setOf("Appunti"))
    val dailyReminder: StateFlow<Boolean> = preferences.dailyReminderFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    
    private val _isVerifying = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isVerifying: StateFlow<Boolean> = _isVerifying
    
    private val _verificationResult = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val verificationResult: StateFlow<String?> = _verificationResult

    fun verifyGithubConnection() {
        _isVerifying.value = true
        _verificationResult.value = null
        viewModelScope.launch {
            val result = repository.verifyGithubConnection()
            _isVerifying.value = false
            _verificationResult.value = result ?: "Connessione riuscita con successo! 🎉"
        }
    }

    fun clearVerificationResult() {
        _verificationResult.value = null
    }
    
    fun getGithubPat() = preferences.getGithubPat()
    fun getOpenRouterKey() = preferences.getOpenRouterKey()

    fun updateGithubPat(pat: String) = preferences.setGithubPat(pat)
    fun updateOpenRouterKey(key: String) = preferences.setOpenRouterKey(key)

    fun updateGithubOwner(owner: String) {
        viewModelScope.launch { preferences.updateGithubOwner(owner) }
    }
    fun updateGithubRepo(repo: String) {
        viewModelScope.launch { preferences.updateGithubRepo(repo) }
    }
    fun updateGithubBranch(branch: String) {
        viewModelScope.launch { preferences.updateGithubBranch(branch) }
    }
    fun updateOpenRouterModel(model: String) {
        viewModelScope.launch { preferences.updateOpenRouterModel(model) }
    }
    fun updateSelectedTheme(theme: Int) {
        viewModelScope.launch { preferences.updateSelectedTheme(theme) }
    }
    fun updateStudyMode(mode: String) {
        viewModelScope.launch { preferences.updateStudyMode(mode) }
    }
    fun updateSourceFolders(folders: Set<String>) {
        viewModelScope.launch { preferences.updateSourceFolders(folders) }
    }

    fun updateDailyReminder(enabled: Boolean, context: android.content.Context) {
        viewModelScope.launch {
            preferences.updateDailyReminder(enabled)
            com.example.data.receiver.ReminderReceiver.scheduleReminder(context, enabled)
        }
    }

    fun clearAllCards(onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.clearAllCards()
            onSuccess()
        }
    }

    class Factory(
        private val preferences: AppPreferences,
        private val repository: FlashcardRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(preferences, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
