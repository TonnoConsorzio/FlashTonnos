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
import com.example.data.preferences.AppPreferences
import com.example.domain.models.DeepDiveCard
import com.example.domain.models.Flashcard
import com.example.domain.repository.FlashcardRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
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

            try {
                // 1. Scarica il contenuto del file
                val fileContentResponse = githubApi.getContent(token, owner, repo, file.path, branch)
                val base64Content = fileContentResponse.content?.replace("\n", "") ?: continue
                val markdownText = String(Base64.decode(base64Content, Base64.DEFAULT))

                // 2. Esegui chunking (suddivisione per heading)
                val chunks = chunkMarkdown(markdownText)
                
                onProgress(
                    if (lang == "it") "File suddiviso in ${chunks.size} chunk. Generazione in corso..."
                    else "File split into ${chunks.size} chunks. Starting generation..."
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
                            if (lang == "it") "[Chunk ${chunkIdx + 1}/${chunks.size}] Generazione di $qaAmount flashcard ($requestedTypes)..."
                            else "[Chunk ${chunkIdx + 1}/${chunks.size}] Generating $qaAmount flashcards ($requestedTypes)..."
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
                            }

                            // Salva in Room
                            repository.insertAllCards(finalCards)
                            
                            // Carica su GitHub
                            for (card in finalCards) {
                                saveFlashcardToGithub(token, owner, repo, branch, cardsFolder, card)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // 2.2 Generazione Deep Dive
                    if (ddAmount > 0 && ddEnabled) {
                        onProgress(
                            if (lang == "it") "[Chunk ${chunkIdx + 1}/${chunks.size}] Generazione di $ddAmount Approfondimenti..."
                            else "[Chunk ${chunkIdx + 1}/${chunks.size}] Generating $ddAmount Deep Dives..."
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
                            }

                            // Salva in Room
                            deepDiveDao.insertAllCards(finalDeepDives.map { DeepDiveMapper.toEntity(it) })

                            // Carica su GitHub
                            for (dd in finalDeepDives) {
                                saveDeepDiveToGithub(token, owner, repo, branch, cardsFolder, dd)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // 3. Aggiorna tracciamento file completato
                deepDiveDao.insertTrackedFile(
                    TrackedFileEntity(
                        path = file.path,
                        lastSha = file.sha,
                        lastIndexedAt = System.currentTimeMillis()
                    )
                )
                
                // Salva elenco file tracciati su GitHub
                repository.saveTrackedFilesToGithub()

                onProgress(
                    if (lang == "it") "✓ File ${file.path} completato e tracciato correttamente!"
                    else "✓ File ${file.path} successfully completed and tracked!"
                )
            } catch (e: Exception) {
                e.printStackTrace()
                onProgress(
                    if (lang == "it") "❌ Errore durante l'elaborazione di ${file.path}: ${e.localizedMessage}"
                    else "❌ Error processing ${file.path}: ${e.localizedMessage}"
                )
            }
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
            """
            ATTENZIONE REQUISITO FONDAMENTALE DI LINGUA:
            L'utente ha selezionato la lingua ITALIANA.
            TUTTI i testi generati, inclusi i campi "question", "correct_answer", "options", "explanation", "topic" e "subtopic" DEVONO essere scritti rigorosamente in lingua ITALIANA.
            È ASSOLUTAMENTE VIETATO usare l'inglese o mischiare inglese e italiano nelle risposte o spiegazioni. Tutto deve essere in italiano fluido, naturale e corretto.
            Per le card di tipo "true_false", l'array "options" deve contenere esattamente ["Vero", "Falso"] e il campo "correct_answer" deve essere esattamente uno di questi due valori.
            """.trimIndent()
        } else {
            """
            LANGUAGE REQUIREMENT:
            The user has selected the ENGLISH language.
            The question, all options, correct answer, explanation, and tags MUST be strictly in ENGLISH.
            For "true_false", the "options" must be exactly ["True", "False"] and "correct_answer" must be one of them.
            """.trimIndent()
        }

        val typeConstraint = if (types.size == 1) {
            "Genera ESCLUSIVAMENTE flashcard di tipo \"${types[0]}\"."
        } else {
            "Genera un mix bilanciato di tipo \"true_false\" e \"multiple_choice\"."
        }

        return """
            Sei un creatore di flashcard didattiche altamente professionali.
            
            $langInstruction

            Analizza il seguente testo ed estrai esattamente $amount flashcard strutturate.
            $typeConstraint

            Restituisci un array JSON di oggetti strutturati esattamente così:
            [
              {
                "type": "true_false" o "multiple_choice",
                "question": "Domanda o affermazione in italiano",
                "correct_answer": "La risposta corretta",
                "options": ["Opzione A", "Opzione B", "Opzione C", "Opzione D"],
                "explanation": "Spiegazione sintetica del perché sia corretta",
                "difficulty": "easy" | "medium" | "hard",
                "source_excerpt": "Breve frase estratta dal testo correlata",
                "topic": "Concetto macro principale (es. Matematica, Reti)",
                "subtopic": "Sotto-argomento specifico (es. Derivate, IP)"
              }
            ]

            TESTO SORGENTE:
            $chunk

            Rispondi SOLO con il JSON valido. Nessun testo introduttivo o blocchi markdown di formattazione.
        """.trimIndent()
    }

    private fun buildDeepDivePrompt(chunk: String, amount: Int, lang: String): String {
        val langInstruction = if (lang == "it") {
            """
            ATTENZIONE REQUISITO FONDAMENTALE DI LINGUA:
            Scrivi rigorosamente ed esclusivamente in lingua ITALIANA.
            Tutti i testi del JSON, inclusi "hook", "body", "topic" e "subtopic", devono essere scritti in italiano scorrevole, naturale e privo di errori.
            Non usare assolutamente l'inglese, eccetto per i termini tecnici inevitabili.
            """.trimIndent()
        } else {
            "Write strictly in ENGLISH. All texts must be natural, fluent, and correct."
        }

        return """
            Sei un creatore di pillole educative per un feed verticale stile TikTok.
            
            $langInstruction

            Analizza il seguente testo ed estrai esattamente $amount card di Approfondimento (deep_dive).
            Le card devono essere informative, d'impatto, adatte ad essere lette rapidamente in un feed a scorrimento.

            Ogni card deve avere:
            - "hook": una frase di aggancio iniziale d'effetto, estremamente concisa (massimo 60 caratteri). Deve incuriosire!
            - "body": un testo di approfondimento fluido e coinvolgente (tra 80 e 180 parole). Deve spiegare un concetto interessante in modo cristallino.
            - "topic": argomento macro principale (es. Algoritmi, Storia, Biologia).
            - "subtopic": sotto-argomento specifico.
            - "tags": una lista di 1-3 parole chiave corte.

            Restituisci un array JSON di oggetti strutturati esattamente così:
            [
              {
                "hook": "string (max 60 chars)",
                "body": "string (80-180 words)",
                "topic": "string",
                "subtopic": "string",
                "tags": ["tag1", "tag2"]
              }
            ]

            TESTO SORGENTE:
            $chunk

            Rispondi SOLO con il JSON valido. Nessun testo introduttivo o blocchi markdown di formattazione.
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
        try {
            val path = "$cardsFolder/${card.id}.json"
            val existingSha = try {
                githubApi.getContent(token, owner, repo, path, branch).sha
            } catch (e: Exception) {
                null
            }
            val contentJson = cardAdapter.toJson(card)
            val contentBase64 = Base64.encodeToString(contentJson.toByteArray(), Base64.NO_WRAP)
            githubApi.putContent(
                token, owner, repo, path,
                GithubPutRequest("Auto-generated QA flashcard ${card.id}", contentBase64, existingSha, branch)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun saveDeepDiveToGithub(
        token: String, owner: String, repo: String, branch: String, cardsFolder: String, dd: DeepDiveCard
    ) {
        try {
            val path = "$cardsFolder/${dd.id}.json"
            val existingSha = try {
                githubApi.getContent(token, owner, repo, path, branch).sha
            } catch (e: Exception) {
                null
            }
            val contentJson = deepDiveCardAdapter.toJson(dd)
            val contentBase64 = Base64.encodeToString(contentJson.toByteArray(), Base64.NO_WRAP)
            githubApi.putContent(
                token, owner, repo, path,
                GithubPutRequest("Auto-generated Deep Dive ${dd.id}", contentBase64, existingSha, branch)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
