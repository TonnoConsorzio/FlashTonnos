package com.example.domain.usecases

import android.content.Context
import android.util.Base64
import com.example.data.ai.Message
import com.example.data.ai.OpenRouterRequest
import com.example.data.ai.OpenRouterService
import com.example.data.github.GithubApiService
import com.example.data.github.GithubPutRequest
import com.example.data.local.DeepDiveDao
import com.example.data.local.DeepDiveMapper
import com.example.data.local.FlashcardMapper
import com.example.data.local.entities.TrackedFileEntity
import com.example.data.local.entities.FlashcardEntity
import com.example.data.local.entities.DeepDiveCardEntity
import com.example.data.preferences.AppPreferences
import com.example.domain.models.DeepDiveCard
import com.example.domain.models.Flashcard
import com.example.domain.models.CardIndexEntry
import com.example.domain.repository.FlashcardRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class GeneratedDeepDive(
    val hook: String,
    val body: String,
    val topic: String,
    val subtopic: String,
    val tags: List<String>
)

/**
 * Gestisce la generazione automatica incrementale di Flashcard (Vero/Falso, Scelta Multipla)
 * e di Deep Dive (Approfondimenti) per i file modificati o nuovi nel vault.
 */
class AutoGenerationUseCase(
    private val githubApi: GithubApiService,
    private val openRouterApi: OpenRouterService,
    private val deepDiveDao: DeepDiveDao,
    private val repository: FlashcardRepository,
    private val appPreferences: AppPreferences,
    private val context: Context
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    
    private val generatedListType = Types.newParameterizedType(List::class.java, GeneratedQA::class.java)
    private val generatedListAdapter = moshi.adapter<List<GeneratedQA>>(generatedListType)

    private val deepDiveListType = Types.newParameterizedType(List::class.java, GeneratedDeepDive::class.java)
    private val deepDiveListAdapter = moshi.adapter<List<GeneratedDeepDive>>(deepDiveListType)

    private val cardAdapter = moshi.adapter(Flashcard::class.java)
    private val deepDiveCardAdapter = moshi.adapter(DeepDiveCard::class.java)

    suspend fun execute(
        filesToProcess: List<FileToProcess>,
        onProgress: (String) -> Unit
    ) {
        val lang = appPreferences.selectedLanguageFlow.first()
        val owner = appPreferences.githubOwnerFlow.first()
        val repo = appPreferences.githubRepoFlow.first()
        val branch = appPreferences.githubBranchFlow.first()
        val rawToken = appPreferences.getGithubPat()
        val token = "Bearer $rawToken"
        val openRouterToken = "Bearer ${appPreferences.getOpenRouterKey()}"
        val model = appPreferences.openRouterModelFlow.first()
        
        val errorSummaries = mutableListOf<String>()
        
        val densityQA = appPreferences.densityQAFlow.first()
        val densityDD = appPreferences.densityDeepDiveFlow.first()
        val tfEnabled = appPreferences.generateTfEnabledFlow.first()
        val mcEnabled = appPreferences.generateMcEnabledFlow.first()
        val ddEnabled = appPreferences.generateDdEnabledFlow.first()

        val cardsFolder = appPreferences.githubCardsFolderFlow.first().trim('/')

        for ((index, file) in filesToProcess.withIndex()) {
            onProgress(
                if (lang == "it") "Elaborazione file ${index + 1}/${filesToProcess.size}: ${file.path}..."
                else "Processing file ${index + 1}/${filesToProcess.size}: ${file.path}..."
            )

            val flashcardsToSave = mutableListOf<com.example.data.local.entities.FlashcardEntity>()
            val deepDivesToSave = mutableListOf<com.example.data.local.entities.DeepDiveCardEntity>()
            val indexEntriesToSave = mutableListOf<com.example.domain.models.CardIndexEntry>()

            var openRouterErrorCount = 0
            var insufficientContent = false

            try {
                // 1. Scarica il contenuto del file
                val fileContentResponse = githubApi.getContent(token, owner, repo, file.path, branch)
                val base64Content = fileContentResponse.content?.replace("\n", "") ?: continue
                val markdownText = String(Base64.decode(base64Content, Base64.DEFAULT))

                // 2. Esegui chunking (suddivisione per heading)
                val chunks = chunkMarkdown(markdownText)
                
                var totalWordCount = 0
                for (chunk in chunks) {
                    val wc = chunk.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                    totalWordCount += wc
                }

                if (totalWordCount < 40) {
                    insufficientContent = true
                }

                onProgress(
                    if (lang == "it") "File suddiviso in ${chunks.size} sezioni di studio. Generazione in corso..."
                    else "File split into ${chunks.size} study sections. Starting generation..."
                )

                for ((chunkIdx, chunk) in chunks.withIndex()) {
                    val wordCount = chunk.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                    if (wordCount < 40) continue // Salta chunk troppo corti

                    val qaAmount = (wordCount / densityQA).coerceAtLeast(1)
                    val ddAmount = (wordCount / densityDD).coerceAtLeast(1)

                    // 2.1 Generazione QA (Vero/Falso e Scelta Multipla)
                    if (qaAmount > 0 && (tfEnabled || mcEnabled)) {
                        val typesToRequest = mutableListOf<String>()
                        if (tfEnabled) typesToRequest.add("true_false")
                        if (mcEnabled) typesToRequest.add("multiple_choice")
                        val requestedTypes = typesToRequest.joinToString(" e ")

                        onProgress(
                            if (lang == "it") "[Sezione ${chunkIdx + 1}/${chunks.size}] Generazione di $qaAmount flashcard ($requestedTypes)..."
                            else "[Section ${chunkIdx + 1}/${chunks.size}] Generating $qaAmount flashcards ($requestedTypes)..."
                        )

                        val prompt = buildQAPrompt(chunk, qaAmount, typesToRequest, lang)
                        try {
                            val response = openRouterApi.generateCards(
                                token = openRouterToken,
                                request = OpenRouterRequest(model = model, messages = listOf(Message("user", prompt)))
                            )
                            val jsonResponse = response.choices?.firstOrNull()?.message?.content ?: ""
                            val parsedCards = parseQAResponse(jsonResponse, lang)

                            val finalCards = parsedCards.map { gen ->
                                val cleanType = gen.type
                                val finalTopic = sanitizeTopic(gen.topic, file.path)
                                val finalSubtopic = gen.subtopic?.trim()?.takeIf { it.isNotEmpty() } ?: "Generale"

                                Flashcard(
                                    type = cleanType,
                                    question = gen.question.trim(),
                                    correct_answer = gen.correct_answer.trim(),
                                    options = gen.options.map { it.trim() },
                                    explanation = gen.explanation.trim(),
                                    source_file = file.path,
                                    source_excerpt = gen.source_excerpt.trim(),
                                    difficulty = gen.difficulty,
                                    topics = listOf(finalTopic, finalSubtopic),
                                    topic = finalTopic,
                                    subtopic = finalSubtopic
                                )
                            }.filter { validateCard(it) }

                            if (finalCards.isNotEmpty()) {
                                flashcardsToSave.addAll(finalCards.map { FlashcardMapper.toEntity(it) })
                                indexEntriesToSave.addAll(finalCards.map {
                                    CardIndexEntry(
                                        id = it.id,
                                        type = it.type,
                                        question = it.question,
                                        topic = it.topic,
                                        subtopic = it.subtopic
                                    )
                                })

                                // Carica su GitHub in parallelo
                                val totalCards = finalCards.size
                                val savedCardsCount = java.util.concurrent.atomic.AtomicInteger(0)
                                coroutineScope {
                                    finalCards.forEach { card ->
                                        launch {
                                            saveFlashcardToGithub(token, owner, repo, branch, cardsFolder, card)
                                            val current = savedCardsCount.incrementAndGet()
                                            onProgress(
                                                if (lang == "it") "Salvando su GitHub: $current/$totalCards card..."
                                                else "Saving to GitHub: $current/$totalCards cards..."
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            val errorMessage = if (e is retrofit2.HttpException) {
                                val body = try { e.response()?.errorBody()?.string() } catch (ignored: Exception) { null }
                                "HTTP ${e.code()}: ${e.message()} — ${body ?: ""}"
                            } else {
                                e.localizedMessage ?: "Errore sconosciuto"
                            }
                            val errorDetail = if (lang == "it") {
                                "[Sezione ${chunkIdx + 1}/${chunks.size}] Errore OpenRouter: $errorMessage"
                            } else {
                                "[Section ${chunkIdx + 1}/${chunks.size}] OpenRouter Error: $errorMessage"
                            }
                            onProgress(errorDetail)
                            errorSummaries.add("${file.path.substringAfterLast("/")} $errorDetail")
                            throw Exception(errorDetail, e)
                        }
                    }

                    // 2.2 Generazione Deep Dive
                    if (ddAmount > 0 && ddEnabled) {
                        onProgress(
                            if (lang == "it") "[Sezione ${chunkIdx + 1}/${chunks.size}] Generazione di $ddAmount Approfondimenti..."
                            else "[Section ${chunkIdx + 1}/${chunks.size}] Generating $ddAmount Deep Dives..."
                        )

                        val prompt = buildDeepDivePrompt(chunk, ddAmount, lang)
                        try {
                            val response = openRouterApi.generateCards(
                                token = openRouterToken,
                                request = OpenRouterRequest(model = model, messages = listOf(Message("user", prompt)))
                            )
                            val jsonResponse = response.choices?.firstOrNull()?.message?.content ?: ""
                            val parsedDeepDives = parseDeepDiveResponse(jsonResponse)

                            val finalDeepDives = parsedDeepDives.map { gen ->
                                val finalTopic = sanitizeTopic(gen.topic, file.path)
                                val finalSubtopic = gen.subtopic.trim().takeIf { it.isNotEmpty() } ?: "Generale"

                                DeepDiveCard(
                                    hook = gen.hook.trim(),
                                    body = gen.body.trim(),
                                    tags = gen.tags.map { it.trim() },
                                    source_file = file.path,
                                    source_excerpt = chunk.take(300),
                                    topic = finalTopic,
                                    subtopic = finalSubtopic
                                )
                            }.filter { validateDeepDive(it) }

                            if (finalDeepDives.isNotEmpty()) {
                                deepDivesToSave.addAll(finalDeepDives.map { DeepDiveMapper.toEntity(it) })
                                indexEntriesToSave.addAll(finalDeepDives.map {
                                    CardIndexEntry(
                                        id = it.id,
                                        type = "deep_dive",
                                        question = it.hook,
                                        topic = it.topic,
                                        subtopic = it.subtopic
                                    )
                                })

                                // Carica su GitHub in parallelo
                                val totalDD = finalDeepDives.size
                                val savedDDCount = java.util.concurrent.atomic.AtomicInteger(0)
                                coroutineScope {
                                    finalDeepDives.forEach { dd ->
                                        launch {
                                            saveDeepDiveToGithub(token, owner, repo, branch, cardsFolder, dd)
                                            val current = savedDDCount.incrementAndGet()
                                            onProgress(
                                                if (lang == "it") "Salvando su GitHub: $current/$totalDD card..."
                                                else "Saving to GitHub: $current/$totalDD cards..."
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            val errorMessage = if (e is retrofit2.HttpException) {
                                val body = try { e.response()?.errorBody()?.string() } catch (ignored: Exception) { null }
                                "HTTP ${e.code()}: ${e.message()} — ${body ?: ""}"
                            } else {
                                e.localizedMessage ?: "Errore sconosciuto"
                            }
                            val errorDetail = if (lang == "it") {
                                "[Sezione ${chunkIdx + 1}/${chunks.size}] Errore OpenRouter (Deep Dive): $errorMessage"
                            } else {
                                "[Section ${chunkIdx + 1}/${chunks.size}] OpenRouter Error (Deep Dive): $errorMessage"
                            }
                            onProgress(errorDetail)
                            errorSummaries.add("${file.path.substringAfterLast("/")} $errorDetail")
                            throw Exception(errorDetail, e)
                        }
                    }
                }

                if (insufficientContent) {
                    onProgress(
                        if (lang == "it") "⚠ ${file.path} — contenuto insufficiente, saltato"
                        else "⚠ ${file.path} — insufficient content, skipped"
                    )
                    kotlinx.coroutines.delay(2000)
                    continue
                }

                if (openRouterErrorCount > 0 && flashcardsToSave.isEmpty() && deepDivesToSave.isEmpty()) {
                    onProgress(
                        if (lang == "it") "✗ ${file.path} — Generazione fallita a causa di errori OpenRouter"
                        else "✗ ${file.path} — Generation failed due to OpenRouter errors"
                    )
                    kotlinx.coroutines.delay(2000)
                    continue
                }

                // 2.9 Delete existing local cards for this file to prevent duplicates
                repository.deleteLocalCardsBySourceFile(file.path)

                // 3. Salva in Room come singola transazione!
                deepDiveDao.saveGenerationResult(
                    trackedFile = TrackedFileEntity(
                        path = file.path,
                        lastSha = file.sha,
                        lastIndexedAt = System.currentTimeMillis()
                    ),
                    cards = flashcardsToSave,
                    deepDives = deepDivesToSave
                )
                
                // Salva elenco file tracciati su GitHub
                repository.saveTrackedFilesToGithub()

                // Salva indice delle card su GitHub
                if (indexEntriesToSave.isNotEmpty()) {
                    repository.updateCardsIndex(indexEntriesToSave)
                }

                onProgress(
                    if (lang == "it") "✓ ${file.path} — ${flashcardsToSave.size} flashcard + ${deepDivesToSave.size} approfondimenti generati"
                    else "✓ ${file.path} — ${flashcardsToSave.size} flashcards + ${deepDivesToSave.size} deep dives generated"
                )
                kotlinx.coroutines.delay(3000)
            } catch (e: Exception) {
                e.printStackTrace()
                val errMsg = e.localizedMessage ?: "Errore generico"
                onProgress(
                    if (lang == "it") "❌ Errore durante l'elaborazione di ${file.path}: $errMsg"
                    else "❌ Error processing ${file.path}: $errMsg"
                )
                errorSummaries.add("${file.path.substringAfterLast("/")}: $errMsg")
                kotlinx.coroutines.delay(3000)
            }
        }
        
        if (errorSummaries.isNotEmpty()) {
            val combinedErrors = errorSummaries.distinct().take(5).joinToString("\n")
            val summaryText = if (lang == "it") {
                "Generazione completata con errori OpenRouter:\n$combinedErrors"
            } else {
                "Generation finished with OpenRouter errors:\n$combinedErrors"
            }
            repository.setGenerationResult(summaryText)
        } else {
            repository.setGenerationResult(
                if (lang == "it") "Generazione completata con successo!"
                else "Generation completed successfully!"
            )
        }
    }

    private fun chunkMarkdown(text: String): List<String> {
        val lines = text.lines()
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()
        
        for (line in lines) {
            // Se incontra un'intestazione h1, h2, h3
            if (line.trim().startsWith("#")) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                    currentChunk = StringBuilder()
                }
            }
            currentChunk.append(line).append("\n")
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }
        
        // Se un chunk supera le 1000 parole, lo spezziamo per paragrafi
        val finalizedChunks = mutableListOf<String>()
        for (c in chunks) {
            val words = c.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (words.size > 1000) {
                val paragraphs = c.split("\n\n")
                var subChunk = StringBuilder()
                var subWords = 0
                for (p in paragraphs) {
                    val pWords = p.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                    if (subWords + pWords > 800) {
                        finalizedChunks.add(subChunk.toString())
                        subChunk = StringBuilder()
                        subWords = 0
                    }
                    subChunk.append(p).append("\n\n")
                    subWords += pWords
                }
                if (subChunk.isNotEmpty()) {
                    finalizedChunks.add(subChunk.toString())
                }
            } else {
                finalizedChunks.add(c)
            }
        }
        
        return finalizedChunks.filter { it.isNotBlank() }
    }

    private fun buildQAPrompt(chunk: String, amount: Int, types: List<String>, lang: String): String {
        val langInstruction = if (lang == "it") {
            "RISPONDI IN ITALIANO (question, correct_answer, options, explanation, topic, subtopic). true_false options: ['Vero', 'Falso']."
        } else {
            "ANSWER IN ENGLISH. true_false options: ['True', 'False']."
        }
        val typeConstraint = if (types.size == 1) "Genera solo: ${types[0]}" else "Genera mix true_false e multiple_choice"

        return """
            $langInstruction
            Crea $amount flashcards JSON dal testo:
            $typeConstraint
            Max parole: question 20, explanation 40.
            
            JSON format:
            [
              {
                "type": "true_false" o "multiple_choice",
                "question": "Domanda corta",
                "correct_answer": "Risposta",
                "options": ["Opzione A", "Opzione B", "Opzione C", "Opzione D"],
                "explanation": "Spiegazione brevissima",
                "difficulty": "easy"|"medium"|"hard",
                "source_excerpt": "Breve estratto",
                "topic": "Argomento macro",
                "subtopic": "Sotto-argomento"
              }
            ]
            Solo JSON valido, no markdown o commenti.

            TESTO:
            $chunk
        """.trimIndent()
    }

    private fun buildDeepDivePrompt(chunk: String, amount: Int, lang: String): String {
        val langInstruction = if (lang == "it") "RISPONDI IN ITALIANO (hook, body, topic, subtopic)." else "ANSWER IN ENGLISH."

        return """
            $langInstruction
            Crea $amount card Deep Dive dal testo.
            Max parole: hook 8 (titolo push), body 80 (spiegazione corta).
            
            JSON format:
            [
              {
                "hook": "Aggancio corto",
                "body": "Spiegazione sintetica",
                "topic": "Macro argomento",
                "subtopic": "Sotto-argomento",
                "tags": ["tag1", "tag2"]
              }
            ]
            Solo JSON valido, no markdown o commenti.

            TESTO:
            $chunk
        """.trimIndent()
    }

    private fun parseQAResponse(jsonResponse: String, lang: String): List<GeneratedQA> {
        val cleaned = cleanJson(jsonResponse)
        val list = try {
            generatedListAdapter.fromJson(cleaned) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // Sanificazione robusta delle risposte
        return list.map { card ->
            val cleanType = if (card.type == "true_false") "true_false" else "multiple_choice"
            var cleanOptions = card.options.map { it.trim() }.filter { it.isNotEmpty() }
            var cleanCorrect = card.correct_answer.trim()

            if (cleanType == "true_false") {
                cleanOptions = if (lang == "it") listOf("Vero", "Falso") else listOf("True", "False")
                val isTrue = cleanCorrect.equals("vero", ignoreCase = true) || 
                              cleanCorrect.equals("true", ignoreCase = true) || 
                              cleanCorrect.equals("v", ignoreCase = true) ||
                              cleanCorrect.equals("t", ignoreCase = true)
                cleanCorrect = if (isTrue) cleanOptions[0] else cleanOptions[1]
            } else {
                if (cleanOptions.size < 2) {
                    cleanOptions = listOf(cleanCorrect, "Opzione 2", "Opzione 3", "Opzione 4")
                }
                if (!cleanOptions.contains(cleanCorrect)) {
                    val mutable = cleanOptions.toMutableList()
                    mutable[0] = cleanCorrect
                    cleanOptions = mutable.shuffled()
                }
            }

            card.copy(
                type = cleanType,
                options = cleanOptions,
                correct_answer = cleanCorrect
            )
        }
    }

    private fun parseDeepDiveResponse(jsonResponse: String): List<GeneratedDeepDive> {
        val cleaned = cleanJson(jsonResponse)
        return try {
            deepDiveListAdapter.fromJson(cleaned) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun cleanJson(json: String): String {
        var cleaned = json.trim()
        val firstBracket = cleaned.indexOf('[')
        val lastBracket = cleaned.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            cleaned = cleaned.substring(firstBracket, lastBracket + 1)
        } else {
            if (cleaned.contains("```json")) {
                cleaned = cleaned.substringAfter("```json").substringBeforeLast("```")
            } else if (cleaned.contains("```")) {
                cleaned = cleaned.substringAfter("```").substringBeforeLast("```")
            }
        }
        return cleaned.trim()
    }

    private fun sanitizeTopic(topic: String?, filePath: String): String {
        val parts = filePath.split("/").map { it.trim() }.filter { it.isNotEmpty() }
        var extractedFolder = ""
        
        if (parts.size >= 2) {
            val last = parts.last()
            extractedFolder = if (last.endsWith(".md", ignoreCase = true)) {
                parts[parts.size - 2]
            } else {
                last
            }
        } else if (parts.isNotEmpty()) {
            val last = parts.last()
            extractedFolder = if (last.endsWith(".md", ignoreCase = true)) {
                last.substringBeforeLast(".md")
            } else {
                last
            }
        }
        
        var clean = if (extractedFolder.isNotBlank() && !extractedFolder.equals("appunti", ignoreCase = true)) {
            extractedFolder
        } else {
            val raw = topic?.trim() ?: ""
            var t = raw
            if (t.contains(":")) {
                t = t.substringAfter(":").trim()
            }
            if (t.contains("/")) {
                t = t.substringAfterLast("/").trim()
            }
            if (t.endsWith(".md", ignoreCase = true)) {
                t = t.substringBeforeLast(".md")
            }
            t
        }
        
        if (clean.isBlank() || clean.equals("appunti", ignoreCase = true)) {
            clean = "GENERALE"
        }
        
        return clean.trim().uppercase()
    }

    private suspend fun saveFlashcardToGithub(
        token: String, owner: String, repo: String, branch: String, cardsFolder: String, card: Flashcard
    ) {
        val path = "$cardsFolder/${card.id}.json"
        android.util.Log.d("GitHub", "Saving card ${card.id} to $path")
        try {
            val contentJson = cardAdapter.toJson(card)
            val contentBase64 = Base64.encodeToString(contentJson.toByteArray(), Base64.NO_WRAP)
            githubApi.putContent(
                token, owner, repo, path,
                GithubPutRequest("Auto-generated QA flashcard ${card.id}", contentBase64, null, branch)
            )
            android.util.Log.d("GitHub", "✓ Card ${card.id} saved")
        } catch (e: Exception) {
            android.util.Log.e("GitHub", "✗ Card ${card.id} failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun saveDeepDiveToGithub(
        token: String, owner: String, repo: String, branch: String, cardsFolder: String, dd: DeepDiveCard
    ) {
        val path = "$cardsFolder/${dd.id}.json"
        android.util.Log.d("GitHub", "Saving card ${dd.id} to $path")
        try {
            val contentJson = deepDiveCardAdapter.toJson(dd)
            val contentBase64 = Base64.encodeToString(contentJson.toByteArray(), Base64.NO_WRAP)
            githubApi.putContent(
                token, owner, repo, path,
                GithubPutRequest("Auto-generated Deep Dive ${dd.id}", contentBase64, null, branch)
            )
            android.util.Log.d("GitHub", "✓ Card ${dd.id} saved")
        } catch (e: Exception) {
            android.util.Log.e("GitHub", "✗ Card ${dd.id} failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun validateCard(card: Flashcard): Boolean {
        val questionWords = card.question.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
        val explanationWords = card.explanation.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
        return questionWords <= 30 && explanationWords <= 70
    }

    private fun validateDeepDive(card: DeepDiveCard): Boolean {
        val hookWords = card.hook.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
        val bodyWords = card.body.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
        return hookWords <= 12 && bodyWords <= 140
    }
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class GeneratedQA(
    val type: String,
    val question: String,
    val correct_answer: String,
    val options: List<String>,
    val explanation: String,
    val source_excerpt: String = "",
    val difficulty: String = "medium",
    val topic: String? = null,
    val subtopic: String? = null
)
