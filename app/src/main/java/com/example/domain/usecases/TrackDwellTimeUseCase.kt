package com.example.domain.usecases

import android.util.Base64
import com.example.data.github.GithubApiService
import com.example.data.github.GithubPutRequest
import com.example.data.local.DeepDiveDao
import com.example.data.local.DeepDiveMapper
import com.example.data.preferences.AppPreferences
import com.example.domain.models.DeepDiveInteraction
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Registra il tempo di permanenza (dwell time) e il feedback esplicito
 * per una card nel database locale e sincronizza periodicamente su GitHub.
 */
class TrackDwellTimeUseCase(
    private val deepDiveDao: DeepDiveDao,
    private val githubApi: GithubApiService,
    private val appPreferences: AppPreferences
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, DeepDiveInteraction::class.java)
    private val listAdapter = moshi.adapter<List<DeepDiveInteraction>>(listType)

    suspend fun execute(
        cardId: String,
        topic: String,
        subtopic: String,
        tags: List<String>,
        dwellTimeMs: Long,
        explicitFeedback: Int
    ) {
        // 1. Salva l'interazione in locale in modo istantaneo
        val interaction = DeepDiveInteraction(
            cardId = cardId,
            topic = topic,
            subtopic = subtopic,
            tags = tags,
            dwellTimeMs = dwellTimeMs,
            explicitFeedback = explicitFeedback
        )
        deepDiveDao.insertInteraction(DeepDiveMapper.toEntity(interaction))

        // 2. Incrementa il contatore delle visualizzazioni della card
        val cardEntity = deepDiveDao.getCardById(cardId)
        if (cardEntity != null) {
            val updated = cardEntity.copy(
                timesShown = cardEntity.timesShown + 1,
                lastShown = Instant.now().toString()
            )
            deepDiveDao.updateCard(updated)
        }

        // 3. Caricamento in batch su GitHub (ogni 20 interazioni totali registrate)
        val allInteractions = deepDiveDao.getAllInteractions()
        if (allInteractions.size % 20 == 0) {
            uploadInteractionsToGithub(allInteractions.map { DeepDiveMapper.toDomain(it) })
        }
    }

    suspend fun uploadInteractionsToGithub(interactions: List<DeepDiveInteraction>) {
        try {
            val owner = appPreferences.githubOwnerFlow.first()
            val repo = appPreferences.githubRepoFlow.first()
            val branch = appPreferences.githubBranchFlow.first()
            val rawToken = appPreferences.getGithubPat()
            val token = "Bearer $rawToken"
            
            if (owner.isBlank() || repo.isBlank() || rawToken.isBlank()) return

            val cardsFolder = appPreferences.githubCardsFolderFlow.first().trim('/')
            val yearMonth = java.time.LocalDate.now().toString().substring(0, 7) // Formato "YYYY-MM"
            val path = "$cardsFolder/stats/deep_dive_interactions_$yearMonth.json"

            val existingSha = try {
                githubApi.getContent(token, owner, repo, path, branch).sha
            } catch (e: Exception) {
                null
            }

            val jsonContent = listAdapter.toJson(interactions)
            val base64Content = Base64.encodeToString(jsonContent.toByteArray(), Base64.NO_WRAP)

            githubApi.putContent(
                token, owner, repo, path,
                GithubPutRequest(
                    message = "Aggiornamento statistiche interazioni deep dive per $yearMonth",
                    content = base64Content,
                    sha = existingSha,
                    branch = branch
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
