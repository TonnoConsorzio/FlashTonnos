package com.example.domain.repository

import android.content.Context
import com.example.data.github.GithubApiService
import com.example.data.local.CardDao
import com.example.data.local.DeepDiveDao
import com.example.data.local.FlashcardMapper
import com.example.data.local.DeepDiveMapper
import com.example.data.local.entities.DeepDiveInteractionEntity
import com.example.data.preferences.AppPreferences
import com.example.domain.models.Flashcard
import com.example.domain.models.DeepDiveCard
import com.example.domain.models.DeepDiveInteraction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

class FlashcardRepository(
    private val githubApi: GithubApiService,
    private val cardDao: CardDao,
    private val deepDiveDao: DeepDiveDao,
    private val appPreferences: AppPreferences,
    private val context: Context
) {

    val selectedLanguageFlow: Flow<String> = appPreferences.selectedLanguageFlow
    val demoInitializedFlow: Flow<Boolean> = appPreferences.demoInitializedFlow
    val studyModeFlow: Flow<String> = appPreferences.studyModeFlow

    fun getGithubPat(): String = appPreferences.getGithubPat()
    suspend fun getGithubOwner(): String = appPreferences.githubOwnerFlow.first()
    suspend fun getGithubRepo(): String = appPreferences.githubRepoFlow.first()
    suspend fun getGithubBranch(): String = appPreferences.githubBranchFlow.first()

    suspend fun updateCredentials(pat: String, owner: String, repo: String, branch: String) {
        appPreferences.setGithubPat(pat)
        appPreferences.updateGithubOwner(owner)
        appPreferences.updateGithubRepo(repo)
        appPreferences.updateGithubBranch(branch)
    }

    fun countActiveFlashcards(): Flow<Int> = cardDao.countActiveFlashcards()
    fun countActiveDeepDives(): Flow<Int> = deepDiveDao.countActiveDeepDives()

    fun getStudyQueue(): Flow<List<Flashcard>> = cardDao.getStudyQueue().map { list ->
        list.map { FlashcardMapper.toDomain(it) }
    }

    suspend fun getStudyQueueSnapshot(): List<Flashcard> {
        return cardDao.getStudyQueue().first().map { FlashcardMapper.toDomain(it) }
    }

    suspend fun getAllDeepDiveCardsSnapshot(): List<DeepDiveCard> {
        return deepDiveDao.getAllCards().first().map { DeepDiveMapper.toDomain(it) }
    }

    suspend fun getAllDeepDiveInteractions(): List<DeepDiveInteraction> {
        return deepDiveDao.getAllInteractions().map { DeepDiveMapper.toDomain(it) }
    }

    fun getAllDeepDiveCardsFlow(): Flow<List<DeepDiveCard>> {
        return deepDiveDao.getAllCards().map { list ->
            list.map { DeepDiveMapper.toDomain(it) }
        }
    }

    fun getAllFlashcardsFlow(): Flow<List<Flashcard>> {
        return cardDao.getAllCards().map { list ->
            list.map { FlashcardMapper.toDomain(it) }
        }
    }

    suspend fun updateCard(card: Flashcard) {
        cardDao.update(FlashcardMapper.toEntity(card))
    }

    suspend fun updateDeepDiveCard(card: DeepDiveCard) {
        deepDiveDao.updateCard(DeepDiveMapper.toEntity(card))
    }

    suspend fun deleteCardById(id: String) {
        cardDao.deleteCardById(id)
    }

    suspend fun deleteDeepDiveCardById(id: String) {
        deepDiveDao.deleteCardById(id)
    }

    fun recordAnswer(isCorrect: Boolean) {
        appPreferences.recordAnswer(isCorrect)
    }

    fun getCurrentCorrectStreak(): Int = appPreferences.getCurrentCorrectStreak()

    suspend fun clearAllCards() {
        cardDao.clearAll()
        deepDiveDao.clearCards()
    }

    suspend fun resetStudyStatistics() {
        // Reset local database times shown/correct for all cards
        val allCards = cardDao.getAllCards().first()
        val resetCards = allCards.map {
            it.copy(timesShown = 0, timesCorrect = 0, lastShown = null)
        }
        cardDao.insertAll(resetCards)

        val allDeepDives = deepDiveDao.getAllCards().first()
        val resetDives = allDeepDives.map {
            it.copy(timesShown = 0, lastShown = null)
        }
        deepDiveDao.insertAllCards(resetDives)
        
        deepDiveDao.clearInteractions()
    }

    suspend fun verifyGithubConnection(): String? {
        val owner = appPreferences.getGitHubOwner()
        val repo = appPreferences.getGitHubRepo()
        val token = appPreferences.getGitHubToken()
        val branch = appPreferences.githubBranchFlow.first()
        if (owner.isBlank() || repo.isBlank() || token.isBlank()) {
            return "Credenziali GitHub non configurate."
        }
        return try {
            githubApi.getRootContents("Bearer $token", owner, repo, branch)
            null // Success
        } catch (e: Exception) {
            "${e::class.simpleName}: ${e.message}"
        }
    }

    suspend fun getStats(): Map<String, Any> {
        val cards = cardDao.getAllCards().first()
        val dives = deepDiveDao.getAllCards().first()
        val interactions = deepDiveDao.getAllInteractions()

        val totalCards = cards.size
        val correctAnswers = cards.sumOf { it.timesCorrect }
        val shownAnswers = cards.sumOf { it.timesShown }
        val accuracy = if (shownAnswers > 0) (correctAnswers.toFloat() / shownAnswers * 100).toInt() else 0

        val totalDives = dives.size
        val totalDwellTime = interactions.sumOf { it.dwellTimeMs }

        return mapOf(
            "total_cards" to totalCards,
            "accuracy" to accuracy,
            "total_dives" to totalDives,
            "total_dwell_time_ms" to totalDwellTime,
            "daily_streak" to appPreferences.getDailyStreak(),
            "max_daily_streak" to appPreferences.getMaxDailyStreak()
        )
    }

    suspend fun getDeepDiveBodyWordCount(cardId: String): Int {
        val card = deepDiveDao.getCardById(cardId) ?: return 0
        return card.body.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    }

    suspend fun trackDeepDiveDwellTime(cardId: String, dwellTimeMs: Long) {
        val cardEntity = deepDiveDao.getCardById(cardId) ?: return
        val card = DeepDiveMapper.toDomain(cardEntity)
        
        // Update local card times and shown timestamp
        val updatedCard = card.copy(
            dwellTimeMs = card.dwellTimeMs + dwellTimeMs,
            timesShown = card.timesShown + 1,
            lastShown = System.currentTimeMillis()
        )
        deepDiveDao.updateCard(DeepDiveMapper.toEntity(updatedCard))

        // Save interaction
        val interaction = DeepDiveInteractionEntity(
            cardId = cardId,
            topic = card.topic,
            subtopic = card.subtopic,
            tagsJson = "[]",
            dwellTimeMs = dwellTimeMs,
            timestamp = System.currentTimeMillis(),
            explicitFeedback = 0
        )
        deepDiveDao.insertInteraction(interaction)
    }

    suspend fun initializeDemoDeck(): Int {
        val demoCards = mutableListOf<Flashcard>()
        
        val baseTrueFalse = listOf(
            Triple("La velocità della luce nel vuoto è di circa 300.000 km/s.", "Vero", "La velocità della luce è esattamente 299.792,458 km/s, solitamente approssimata a 300.000 km/s."),
            Triple("L'isola di Creta si trova nel Mar Adriatico.", "Falso", "L'isola di Creta si trova nel Mar Egeo, a sud del Mar Egeo nel Mar Mediterraneo."),
            Triple("Il DNA è composto da cinque basi azotate diverse.", "Falso", "Il DNA è composto da quattro basi azotate principali: Adenina (A), Timina (T), Citosina (C) e Guanina (G)."),
            Triple("Il primo computer elettronico programmabile si chiamava ENIAC.", "Vero", "L'ENIAC (Electronic Numerical Integrator and Computer) è stato completato nel 1945 ed è considerato il primo computer elettronico programmabile per scopi generali."),
            Triple("La catena montuosa delle Ande si trova in Africa.", "Falso", "La catena montuosa delle Ande si trova interamente nell'America del Sud ed è la catena montuosa continentale più lunga del mondo.")
        )
        
        for (i in 0 until 50) {
            val base = baseTrueFalse[i % baseTrueFalse.size]
            val indexStr = if (i >= baseTrueFalse.size) " (Variazione ${i / baseTrueFalse.size + 1})" else ""
            demoCards.add(
                Flashcard(
                    id = java.util.UUID.randomUUID().toString(),
                    type = "true_false",
                    question = "${base.first}$indexStr",
                    correctAnswer = base.second,
                    options = listOf("Vero", "Falso"),
                    explanation = base.third,
                    sourceFile = "Demo_TrueFalse.md",
                    sourceExcerpt = base.first,
                    difficulty = if (i % 3 == 0) "easy" else if (i % 3 == 1) "medium" else "hard"
                )
            )
        }
        
        val baseMultipleChoice = listOf(
            Triple("Quale pianeta del sistema solare è noto come il Pianeta Rosso?", "Marte", listOf("Marte", "Venere", "Giove", "Saturno")),
            Triple("Chi ha dipinto la famosa opera 'La Gioconda'?", "Leonardo da Vinci", listOf("Leonardo da Vinci", "Michelangelo", "Raffaello", "Sandro Botticelli")),
            Triple("Quale elemento chimico ha il simbolo 'O'?", "Ossigeno", listOf("Ossigeno", "Oro", "Elio", "Osmio")),
            Triple("In quale anno è stato lanciato il primo iPhone di Apple?", "2007", listOf("2007", "2005", "2008", "2010")),
            Triple("Qual è la capitale ufficiale del Giappone?", "Tokyo", listOf("Tokyo", "Kyoto", "Osaka", "Hiroshima"))
        )
        
        for (i in 0 until 50) {
            val base = baseMultipleChoice[i % baseMultipleChoice.size]
            val indexStr = if (i >= baseMultipleChoice.size) " (Test ${i / baseMultipleChoice.size + 1})" else ""
            demoCards.add(
                Flashcard(
                    id = java.util.UUID.randomUUID().toString(),
                    type = "multiple_choice",
                    question = "${base.first}$indexStr",
                    correctAnswer = base.second,
                    options = base.third,
                    explanation = "La risposta corretta è ${base.second}. Questa è una card demo del sistema FlashTonnos per facilitare lo studio attivo e la memorizzazione.",
                    sourceFile = "Demo_MultipleChoice.md",
                    sourceExcerpt = base.first,
                    difficulty = if (i % 3 == 0) "easy" else if (i % 3 == 1) "medium" else "hard"
                )
            )
        }
        
        val baseCuriosities = listOf(
            Triple("Sapevi che il miele non scade mai?", "È vero", "Grazie alla sua bassa umidità e alta acidità, i batteri non possono proliferare nel miele. Sono stati trovati barattoli di miele risalenti a 3000 anni fa nelle tombe egizie ancora perfettamente commestibili!"),
            Triple("Il famoso paradosso del compleanno", "23 persone", "In un gruppo di sole 23 persone, la probabilità che almeno due compiano gli anni lo stesso giorno supera il 50%! Con 57 persone, la probabilità sale al 99%."),
            Triple("L'origine del termine informatico 'Bug'", "Una falena reale", "Nel 1947, Grace Hopper trovò una vera falena incastrata nel relè del computer Harvard Mark II, causando un malfunzionamento. Lo registrò sul diario come 'primo caso reale di insetto (bug) trovato'."),
            Triple("Perché lo spazio profondo è nero?", "Paradosso di Olbers", "Se l'universo fosse infinito e statico, ogni linea di vista dovrebbe terminare su una stella, rendendo il cielo notturno luminosissimo. Lo spazio è nero perché l'universo si sta espandendo e ha un'età finita."),
            Triple("L'invenzione casuale del microonde", "Scioglimento di un cioccolato", "L'ingegnere Percy Spencer scoprì il microonde mentre lavorava sui radar: notò che una barretta di cioccolato che aveva in tasca si era completamente sciolta passando vicino a un magnetron attivo!")
        )
        
        for (i in 0 until 50) {
            val base = baseCuriosities[i % baseCuriosities.size]
            val indexStr = if (i >= baseCuriosities.size) " (Pillola ${i / baseCuriosities.size + 1})" else ""
            demoCards.add(
                Flashcard(
                    id = java.util.UUID.randomUUID().toString(),
                    type = "multiple_choice",
                    question = "${base.first}$indexStr",
                    correctAnswer = base.second,
                    options = listOf(base.second, "Altra opzione A", "Altra opzione B", "Altra opzione C").shuffled(),
                    explanation = base.third,
                    sourceFile = "Demo_Curiosities.md",
                    sourceExcerpt = base.first,
                    difficulty = if (i % 3 == 0) "easy" else if (i % 3 == 1) "medium" else "hard"
                )
            )
        }
        
        cardDao.insertAll(demoCards.map { FlashcardMapper.toEntity(it) })
        appPreferences.updateDemoInitialized(true)
        return demoCards.size
    }
}
