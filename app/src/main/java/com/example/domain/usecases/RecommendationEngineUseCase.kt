package com.example.domain.usecases

import com.example.domain.models.DeepDiveCard
import com.example.domain.models.DeepDiveInteraction
import com.example.domain.repository.FlashcardRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/**
 * Gestisce l'algoritmo di raccomandazione locale stile "For You".
 * Funziona interamente offline sul dispositivo per garantire fluidità istantanea.
 */
class RecommendationEngineUseCase(
    private val repository: FlashcardRepository
) {

    /**
     * Genera un elenco ordinato di DeepDiveCard per il feed basato sulle interazioni passate dell'utente.
     * 
     * @param count Numero di card da preparare nel buffer (es. 20)
     */
    suspend fun generateFeed(count: Int = 20): List<DeepDiveCard> {
        val allCards = repository.getAllDeepDiveCardsSnapshot()
        if (allCards.isEmpty()) {
            return emptyList()
        }

        val interactions = repository.getAllDeepDiveInteractions()

        // Caso COLD START: se l'utente ha meno di 10 interazioni, restituiamo un feed casuale
        if (interactions.size < 10) {
            // Raggruppa per topic per calcolare quanti elementi ha ogni topic
            val cardsByTopic = allCards.groupBy { it.topic }
            val totalCards = allCards.size.toFloat()

            // Pesiamo la probabilità basata sul numero di card per topic
            return allCards.shuffled()
                .sortedByDescending { card ->
                    val topicCount = cardsByTopic[card.topic]?.size ?: 0
                    topicCount / totalCards // Maggiore probabilità statistica ai topic più ricchi
                }
                .take(count)
        }

        // Calcoliamo i punteggi di interesse per ciascun topic e tag basandoci sugli ultimi 30 giorni
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)
        val recentInteractions = interactions.filter { it.timestamp >= thirtyDaysAgo }

        // Punteggi aggregati per Topic e Tag
        val topicScores = mutableMapOf<String, MutableList<Double>>()
        val tagScores = mutableMapOf<String, MutableList<Double>>()

        for (inter in recentInteractions) {
            val wordCount = repository.getDeepDiveBodyWordCount(inter.cardId)
            val estimatedReadTimeMs = (wordCount.toFloat() / 200f * 60000f).toLong().coerceAtLeast(1500L)
            
            // interest_score = clamp(dwell_time_ms / estimated_read_time_ms, 0.0, 1.5)
            val interestScore = (inter.dwellTimeMs.toDouble() / estimatedReadTimeMs.toDouble()).coerceIn(0.0, 1.5)
            
            // explicit_feedback: +1 (like), -1 (dislike), 0
            val rawInteractionScore = interestScore + (inter.explicitFeedback * 0.5)

            // Fattore di decadimento temporale: si dimezza ogni 7 giorni
            val daysAgo = ((now - inter.timestamp) / (24.0 * 60.0 * 60.0 * 1000.0)).coerceAtLeast(0.0)
            val decayFactor = 0.5.pow(daysAgo / 7.0)

            val finalInterScore = rawInteractionScore * decayFactor

            // Accumula per topic
            if (inter.topic.isNotBlank()) {
                topicScores.getOrPut(inter.topic) { mutableListOf() }.add(finalInterScore)
            }
            // Accumula per tag
            for (tag in inter.tags) {
                if (tag.isNotBlank()) {
                    tagScores.getOrPut(tag) { mutableListOf() }.add(finalInterScore)
                }
            }
        }

        // Calcola la media dei punteggi per ciascun topic e tag
        val finalTopicRanking = topicScores.mapValues { (_, scores) -> scores.average() }
        val finalTagRanking = tagScores.mapValues { (_, scores) -> scores.average() }

        // 1. Calcola le quote basate sull'algoritmo (65% personalizzato, 20% esplorazione, 15% evergreen)
        val limitPersonalized = (count * 0.65).toInt().coerceAtLeast(1)
        val limitExploration = (count * 0.20).toInt().coerceAtLeast(1)
        val limitEvergreen = (count - limitPersonalized - limitExploration).coerceAtLeast(1)

        // Prepariamo i sottoinsiemi
        // 1.1 Personalized (basato su topic/tag caldi)
        // Ordiniamo i topic caldi
        val hotTopics = finalTopicRanking.entries.sortedByDescending { it.value }.map { it.key }
        val hotTags = finalTagRanking.entries.sortedByDescending { it.value }.map { it.key }

        val personalizedCards = allCards.filter { card ->
            card.topic in hotTopics || card.tags.any { it in hotTags }
        }.sortedWith(compareBy<DeepDiveCard> { card ->
            // Meno volte mostrate prima, oppure mostrate da più tempo
            card.timesShown
        }.thenByDescending { card ->
            val topicScore = finalTopicRanking[card.topic] ?: 0.0
            val maxTagScore = card.tags.maxOfOrNull { finalTagRanking[it] ?: 0.0 } ?: 0.0
            topicScore.coerceAtLeast(maxTagScore)
        }).take(limitPersonalized)

        // 1.2 Exploration (topic/tag freddi o mai visti)
        val exploredTopics = finalTopicRanking.keys
        val allAvailableTopics = allCards.map { it.topic }.distinct()
        val unexploredTopics = allAvailableTopics.filterNot { it in exploredTopics }

        val explorationCards = allCards.filter { card ->
            if (unexploredTopics.isNotEmpty()) {
                card.topic in unexploredTopics
            } else {
                // se tutti i topic sono già stati esplorati, prendiamo quelli con punteggio più basso
                val lowScoreTopics = finalTopicRanking.entries.sortedBy { it.value }.map { it.key }.take(3)
                card.topic in lowScoreTopics
            }
        }.shuffled().take(limitExploration)

        // 1.3 Evergreen (le card con maggior dwell time storico complessivo)
        val evergreenInteractions = interactions.groupBy { it.cardId }
            .mapValues { (_, list) -> list.sumOf { it.dwellTimeMs } }
            .entries.sortedByDescending { it.value }
            .map { it.key }

        val evergreenCards = allCards.filter { card ->
            card.id in evergreenInteractions
        }.sortedByDescending { card ->
            evergreenInteractions.indexOf(card.id)
        }.take(limitEvergreen)

        // 2. Unisci, mescola e restituisci
        val mergedList = (personalizedCards + explorationCards + evergreenCards).distinctBy { it.id }
        
        // Se non abbiamo raggiunto il numero desiderato, riempiamo con card casuali non presenti nel mergedList
        if (mergedList.size < count) {
            val remaining = allCards.filterNot { c -> mergedList.any { it.id == c.id } }.shuffled()
            return (mergedList + remaining).take(count)
        }

        return mergedList.shuffled()
    }
}
