package com.example.domain.repository

import android.util.Base64
import android.util.Log
import com.example.data.github.GithubApiService
import com.example.data.local.CardDao
import com.example.data.local.DeepDiveDao
import com.example.data.local.IndexEntryDao
import com.example.data.local.entities.toEntity
import com.example.data.local.FlashcardMapper
import com.example.data.local.DeepDiveMapper
import com.example.data.preferences.AppPreferences
import com.example.domain.models.FlashTonnosIndex
import com.example.domain.models.Flashcard
import com.example.domain.models.DeepDiveCard
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class SyncRepository(
    private val githubApi: GithubApiService,
    private val cardDao: CardDao,
    private val deepDiveDao: DeepDiveDao,
    private val indexDao: IndexEntryDao,
    private val prefs: AppPreferences
) {

    sealed class SyncState {
        object Idle : SyncState()
        data class Syncing(val step: String, val progress: Float) : SyncState()
        data class Success(
            val newFlashcards: Int,
            val newDeepDives: Int,
            val totalFlashcards: Int,
            val totalDeepDives: Int
        ) : SyncState()
        data class Error(val message: String, val technical: String) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    suspend fun sync(force: Boolean = false) {
        _syncState.value = SyncState.Syncing("Connessione a GitHub...", 0f)

        val owner = prefs.getGitHubOwner()
        val repo = prefs.getGitHubRepo()
        val token = prefs.getGitHubToken()
        val branch = prefs.githubBranchFlow.first()

        if (owner.isBlank() || repo.isBlank() || token.isBlank()) {
            _syncState.value = SyncState.Error(
                message = "Repository non configurata",
                technical = "owner='$owner' repo='$repo' token_blank=${token.isBlank()}"
            )
            return
        }

        val authHeader = "Bearer $token"

        // 1. Scarica index.json
        _syncState.value = SyncState.Syncing("Scarico indice contenuti...", 0.1f)
        val index = try {
            val contentResponse = githubApi.getContent(authHeader, owner, repo, "FlashTonnos/index.json", branch)
            val decodedJson = decodeBase64(contentResponse.content ?: "")
            moshi.adapter(FlashTonnosIndex::class.java).fromJson(decodedJson)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(
                message = "Impossibile scaricare l'indice dei contenuti",
                technical = "${e::class.simpleName}: ${e.message}"
            )
            return
        }

        if (index == null) {
            _syncState.value = SyncState.Error(
                message = "Indice dei contenuti vuoto o non valido",
                technical = "Moshi decoded null"
            )
            return
        }

        var newFlashcards = 0
        var newDeepDives = 0

        // 2. Filtra gli elementi che sono cambiati rispetto allo SHA in cache (a meno che non sia forzato)
        val entriesToSync = if (force) {
            index.entries
        } else {
            index.entries.filter { entry ->
                val cached = indexDao.getByFolder(entry.folder)
                cached?.sourceSha != entry.sourceSha
            }
        }

        val totalToSync = entriesToSync.size
        if (totalToSync > 0) {
            _syncState.value = SyncState.Syncing("Avvio download parallelo dei contenuti...", 0.15f)
            
            try {
                coroutineScope {
                    val deferreds = entriesToSync.mapIndexed { indexInList, entry ->
                        async {
                            val progress = 0.15f + (0.8f * (indexInList.toFloat() / totalToSync))
                            _syncState.value = SyncState.Syncing(
                                "Scarico: ${entry.subtopic} (${indexInList + 1}/$totalToSync)",
                                progress
                            )
                            
                            // Download flashcards.json in parallelo
                            val cards = try {
                                val flashcardsJsonRes = githubApi.getContent(
                                    authHeader, owner, repo,
                                    "FlashTonnos/content/${entry.folder}/flashcards.json",
                                    branch
                                )
                                val flashcardsJson = decodeBase64(flashcardsJsonRes.content ?: "")
                                val listType = Types.newParameterizedType(List::class.java, Flashcard::class.java)
                                moshi.adapter<List<Flashcard>>(listType).fromJson(flashcardsJson) ?: emptyList()
                            } catch (e: Exception) {
                                Log.e("FlashTonnos", "Error loading flashcards for ${entry.folder}: ${e.message}")
                                emptyList()
                            }

                            // Download deepdives.json in parallelo
                            val dives = try {
                                val deepDivesJsonRes = githubApi.getContent(
                                    authHeader, owner, repo,
                                    "FlashTonnos/content/${entry.folder}/deepdives.json",
                                    branch
                                )
                                val deepDivesJson = decodeBase64(deepDivesJsonRes.content ?: "")
                                val listType = Types.newParameterizedType(List::class.java, DeepDiveCard::class.java)
                                moshi.adapter<List<DeepDiveCard>>(listType).fromJson(deepDivesJson) ?: emptyList()
                            } catch (e: Exception) {
                                Log.e("FlashTonnos", "Error loading deepdives for ${entry.folder}: ${e.message}")
                                emptyList()
                            }

                            Triple(entry, cards, dives)
                        }
                    }

                    val results = deferreds.awaitAll()

                    // Salva tutto nel database locale
                    results.forEach { (entry, cards, dives) ->
                        val mappedCards = cards.map { card ->
                            val t = if (card.topic.isBlank()) entry.topic else card.topic
                            val st = if (card.subtopic.isBlank()) entry.subtopic else card.subtopic
                            val sf = if (card.sourceFile.isBlank()) entry.sourceFile else card.sourceFile

                            val updatedCard = card.copy(
                                topic = t,
                                subtopic = st,
                                sourceFile = sf
                            )
                            FlashcardMapper.toEntity(updatedCard)
                        }
                        cardDao.deleteCardsBySourceFile(entry.sourceFile)
                        cardDao.insertAll(mappedCards)
                        newFlashcards += mappedCards.size

                        val mappedDives = dives.map { dive ->
                            val t = if (dive.topic.isBlank()) entry.topic else dive.topic
                            val st = if (dive.subtopic.isBlank()) entry.subtopic else dive.subtopic
                            val sf = if (dive.sourceFile.isBlank()) entry.sourceFile else dive.sourceFile

                            val updatedDive = dive.copy(
                                topic = t,
                                subtopic = st,
                                sourceFile = sf
                            )
                            DeepDiveMapper.toEntity(updatedDive)
                        }
                        deepDiveDao.deleteCardsBySourceFile(entry.sourceFile)
                        deepDiveDao.insertAllCards(mappedDives)
                        newDeepDives += mappedDives.size

                        // Aggiorna cache dell'indice
                        indexDao.upsert(entry.toEntity())
                    }
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(
                    message = "Errore durante il download dei contenuti",
                    technical = "${e::class.simpleName}: ${e.message}"
                )
                return
            }
        }

        // Recupera i totali reali correnti presenti nel database
        val totalFlashcards = cardDao.countActive().first()
        val totalDeepDives = deepDiveDao.countActive().first()

        _syncState.value = SyncState.Success(
            newFlashcards = newFlashcards,
            newDeepDives = newDeepDives,
            totalFlashcards = totalFlashcards,
            totalDeepDives = totalDeepDives
        )
    }

    private fun decodeBase64(encoded: String): String {
        val cleaned = encoded.replace("\n", "").replace(" ", "")
        return String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8)
    }
}
