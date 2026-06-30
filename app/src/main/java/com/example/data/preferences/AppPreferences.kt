package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {
    private val encryptedPrefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secret_shared_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        try {
            // If it failed because of keystore corruption/state change, try to delete the old preferences file
            // so we can recreate it, or fallback to standard SharedPreferences
            context.deleteSharedPreferences("secret_shared_prefs")
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "secret_shared_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (ex: Exception) {
            // Final fallback to unencrypted SharedPreferences if everything security-related fails
            context.getSharedPreferences("secret_shared_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    // DataStore keys
    private val GITHUB_OWNER = stringPreferencesKey("github_owner")
    private val GITHUB_REPO = stringPreferencesKey("github_repo")
    private val GITHUB_BRANCH = stringPreferencesKey("github_branch")
    private val OPENROUTER_MODEL = stringPreferencesKey("openrouter_model")
    private val SOURCE_FOLDERS = stringSetPreferencesKey("source_folders")
    private val SELECTED_THEME = intPreferencesKey("selected_theme")
    private val STUDY_MODE = stringPreferencesKey("study_mode")
    private val DEMO_INITIALIZED = booleanPreferencesKey("demo_initialized")
    private val DAILY_REMINDER = booleanPreferencesKey("daily_reminder")

    // Secure Prefs keys
    private val KEY_GITHUB_PAT = "github_pat"
    private val KEY_OPENROUTER_KEY = "openrouter_key"

    val githubOwnerFlow: Flow<String> = context.dataStore.data.map { it[GITHUB_OWNER] ?: "" }
    val githubRepoFlow: Flow<String> = context.dataStore.data.map { it[GITHUB_REPO] ?: "" }
    val githubBranchFlow: Flow<String> = context.dataStore.data.map { it[GITHUB_BRANCH] ?: "main" }
    val openRouterModelFlow: Flow<String> = context.dataStore.data.map { it[OPENROUTER_MODEL] ?: "openrouter/auto" }
    val sourceFoldersFlow: Flow<Set<String>> = context.dataStore.data.map { it[SOURCE_FOLDERS] ?: setOf("Appunti") }
    val selectedThemeFlow: Flow<Int> = context.dataStore.data.map { it[SELECTED_THEME] ?: 0 }
    val studyModeFlow: Flow<String> = context.dataStore.data.map { it[STUDY_MODE] ?: "classic" }
    val demoInitializedFlow: Flow<Boolean> = context.dataStore.data.map { it[DEMO_INITIALIZED] ?: false }
    val dailyReminderFlow: Flow<Boolean> = context.dataStore.data.map { it[DAILY_REMINDER] ?: false }

    suspend fun updateGithubOwner(owner: String) = context.dataStore.edit { it[GITHUB_OWNER] = owner }
    suspend fun updateGithubRepo(repo: String) = context.dataStore.edit { it[GITHUB_REPO] = repo }
    suspend fun updateGithubBranch(branch: String) = context.dataStore.edit { it[GITHUB_BRANCH] = branch }
    suspend fun updateOpenRouterModel(model: String) = context.dataStore.edit { it[OPENROUTER_MODEL] = model }
    suspend fun updateSourceFolders(folders: Set<String>) = context.dataStore.edit { it[SOURCE_FOLDERS] = folders }
    suspend fun updateSelectedTheme(theme: Int) = context.dataStore.edit { it[SELECTED_THEME] = theme }
    suspend fun updateStudyMode(mode: String) = context.dataStore.edit { it[STUDY_MODE] = mode }
    suspend fun updateDemoInitialized(initialized: Boolean) = context.dataStore.edit { it[DEMO_INITIALIZED] = initialized }
    suspend fun updateDailyReminder(enabled: Boolean) = context.dataStore.edit { it[DAILY_REMINDER] = enabled }

    fun getGithubPat(): String = encryptedPrefs.getString(KEY_GITHUB_PAT, "") ?: ""
    fun setGithubPat(pat: String) = encryptedPrefs.edit().putString(KEY_GITHUB_PAT, pat).apply()

    fun getOpenRouterKey(): String = encryptedPrefs.getString(KEY_OPENROUTER_KEY, "") ?: ""
    fun setOpenRouterKey(key: String) = encryptedPrefs.edit().putString(KEY_OPENROUTER_KEY, key).apply()

    // Streak and Record keys
    private val KEY_DAILY_STREAK = "daily_streak"
    private val KEY_LAST_STUDY_DATE = "last_study_date"
    private val KEY_MAX_DAILY_STREAK = "max_daily_streak"
    private val KEY_CURRENT_CORRECT_STREAK = "current_correct_streak"
    private val KEY_MAX_CORRECT_STREAK = "max_correct_streak"

    fun getDailyStreak(): Int = encryptedPrefs.getInt(KEY_DAILY_STREAK, 0)
    fun getMaxDailyStreak(): Int = encryptedPrefs.getInt(KEY_MAX_DAILY_STREAK, 0)
    fun getCurrentCorrectStreak(): Int = encryptedPrefs.getInt(KEY_CURRENT_CORRECT_STREAK, 0)
    fun getMaxCorrectStreak(): Int = encryptedPrefs.getInt(KEY_MAX_CORRECT_STREAK, 0)
    fun getLastStudyDate(): String = encryptedPrefs.getString(KEY_LAST_STUDY_DATE, "") ?: ""

    fun recordAnswer(isCorrect: Boolean) {
        // 1. Handle correct streak
        val currentCorrect = encryptedPrefs.getInt(KEY_CURRENT_CORRECT_STREAK, 0)
        val maxCorrect = encryptedPrefs.getInt(KEY_MAX_CORRECT_STREAK, 0)
        
        if (isCorrect) {
            val newCorrect = currentCorrect + 1
            encryptedPrefs.edit().putInt(KEY_CURRENT_CORRECT_STREAK, newCorrect).apply()
            if (newCorrect > maxCorrect) {
                encryptedPrefs.edit().putInt(KEY_MAX_CORRECT_STREAK, newCorrect).apply()
            }
        } else {
            encryptedPrefs.edit().putInt(KEY_CURRENT_CORRECT_STREAK, 0).apply()
        }
        
        // 2. Handle daily study streak
        val today = java.time.LocalDate.now().toString() // "YYYY-MM-DD"
        val lastStudy = encryptedPrefs.getString(KEY_LAST_STUDY_DATE, "") ?: ""
        val dailyStreak = encryptedPrefs.getInt(KEY_DAILY_STREAK, 0)
        val maxDaily = encryptedPrefs.getInt(KEY_MAX_DAILY_STREAK, 0)
        
        if (lastStudy != today) {
            val yesterday = java.time.LocalDate.now().minusDays(1).toString()
            val newStreak = if (lastStudy == yesterday) {
                dailyStreak + 1
            } else {
                1
            }
            encryptedPrefs.edit()
                .putString(KEY_LAST_STUDY_DATE, today)
                .putInt(KEY_DAILY_STREAK, newStreak)
                .apply()
            
            if (newStreak > maxDaily) {
                encryptedPrefs.edit().putInt(KEY_MAX_DAILY_STREAK, newStreak).apply()
            }
        }
    }
}
