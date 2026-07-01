package com.example.domain.repository

import android.util.Base64
import com.example.data.ai.Message
import com.example.data.ai.OpenRouterRequest
import com.example.data.ai.OpenRouterService
import com.example.data.github.GithubApiService
import com.example.data.github.GithubPutRequest
import com.example.data.local.CardDao
import com.example.data.local.DeepDiveDao
import com.example.data.local.FlashcardMapper
import com.example.data.preferences.AppPreferences
import com.example.domain.models.Flashcard
import com.example.domain.models.DeepDiveCard
import com.example.domain.models.DeepDiveInteraction
import com.example.data.local.DeepDiveMapper
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FlashcardRepository(
    private val githubApi: GithubApiService,
    private val openRouterApi: OpenRouterService,
    private val cardDao: CardDao,
    private val deepDiveDao: DeepDiveDao,
    private val appPreferences: AppPreferences,
    private val context: android.content.Context
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, Flashcard::class.java)
    private val listAdapter = moshi.adapter<List<Flashcard>>(listType)
    private val cardAdapter = moshi.adapter(Flashcard::class.java)
    private val deepDiveCardAdapter = moshi.adapter(DeepDiveCard::class.java)
    private val generatedListType = Types.newParameterizedType(List::class.java, GeneratedFlashcard::class.java)
    private val generatedListAdapter = moshi.adapter<List<GeneratedFlashcard>>(generatedListType)

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generationProgress = MutableStateFlow("")
    val generationProgress: StateFlow<String> = _generationProgress.asStateFlow()

    private val _generationResult = MutableStateFlow<String?>(null)
    val generationResult: StateFlow<String?> = _generationResult.asStateFlow()

    val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        repositoryScope.launch {
            kotlinx.coroutines.delay(2000)
            val autoEnabled = appPreferences.autoGenerateEnabledFlow.first()
            if (autoEnabled) {
                runAutoGeneration()
            }
        }
    }

    suspend fun getAllTrackedFiles(): List<com.example.data.local.entities.TrackedFileEntity> {
        return deepDiveDao.getAllTrackedFiles()
    }

    suspend fun runAutoGeneration() {
        if (_isGenerating.value) return
        _isGenerating.value = true
        _generationProgress.value = "Rilevamento file..."
        
        try {
            val detectUseCase = com.example.domain.usecases.DetectChangedFilesUseCase(
                githubApi = githubApi,
                deepDiveDao = deepDiveDao,
                appPreferences = appPreferences
            )
            
            val generateUseCase = com.example.domain.usecases.AutoGenerationUseCase(
                githubApi = githubApi,
                openRouterApi = openRouterApi,
                deepDiveDao = deepDiveDao,
                repository = this,
                appPreferences = appPreferences,
                context = context
            )
            
            val filesToProcess = detectUseCase.execute()
            if (filesToProcess.isEmpty()) {
                _generationProgress.value = "Tutti i file sono aggiornati!"
                kotlinx.coroutines.delay(2000)
                _generationProgress.value = "Idle"
            } else {
                _generationProgress.value = "Trovati ${filesToProcess.size} file da elaborare..."
                generateUseCase.execute(filesToProcess) { progress ->
                    _generationProgress.value = progress
                }
                _generationProgress.value = "Generazione completata!"
                kotlinx.coroutines.delay(3000)
                _generationProgress.value = "Aggiornato"
            }
        } catch (e: Exception) {
            _generationProgress.value = "Errore: ${e.localizedMessage}"
            e.printStackTrace()
        } finally {
            _isGenerating.value = false
        }
    }

    private val _isStudying = MutableStateFlow(false)
    val isStudying: StateFlow<Boolean> = _isStudying.asStateFlow()

    fun setStudying(studying: Boolean) {
        _isStudying.value = studying
    }

    fun setGenerationResult(result: String?) {
        _generationResult.value = result
    }

    fun setGenerationProgress(progress: String) {
        _generationProgress.value = progress
    }

    fun startGeneratingCards(sourceFile: String, amount: Int, type: String) {
        _isGenerating.value = true
        _generationResult.value = null
        repositoryScope.launch {
            val lang = appPreferences.selectedLanguageFlow.first()
            _generationProgress.value = if (lang == "it") "Avvio generazione dal file: $sourceFile..." else "Starting generation from file: $sourceFile..."
            try {
                val generatedCount = generateCards(sourceFile, amount, type) { status ->
                    _generationProgress.value = status
                }
                _generationResult.value = if (lang == "it") {
                    "✓ $generatedCount nuove card generate correttamente dal file: $sourceFile"
                } else {
                    "✓ $generatedCount new cards successfully generated from file: $sourceFile"
                }
            } catch (e: Exception) {
                _generationResult.value = if (lang == "it") {
                    "Errore: ${e.localizedMessage ?: "Errore generico"}"
                } else {
                    "Error: ${e.localizedMessage ?: "Generic error"}"
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun startGeneratingAllCardsMassively(type: String) {
        _isGenerating.value = true
        _generationResult.value = null
        repositoryScope.launch {
            val lang = appPreferences.selectedLanguageFlow.first()
            _generationProgress.value = if (lang == "it") "Scansione dei file .md nelle cartelle configurate..." else "Scanning .md files in configured folders..."
            try {
                val files = fetchMarkdownFilesFromConfiguredFolders()
                if (files.isEmpty()) {
                    _generationResult.value = if (lang == "it") {
                        "Nessun file .md trovato nelle cartelle configurate. Controlla le impostazioni."
                    } else {
                        "No .md files found in configured folders. Please check your settings."
                    }
                    _isGenerating.value = false
                    return@launch
                }
                
                _generationProgress.value = if (lang == "it") {
                    "Trovati ${files.size} file .md. Avvio generazione di 5 flashcard per ciascuno..."
                } else {
                    "Found ${files.size} .md files. Starting generation of 5 flashcards for each..."
                }
                var totalGenerated = 0
                for ((index, file) in files.withIndex()) {
                    _generationProgress.value = if (lang == "it") {
                        "[File ${index + 1}/${files.size}] Elaborazione di: $file..."
                    } else {
                        "[File ${index + 1}/${files.size}] Processing: $file..."
                    }
                    try {
                        val count = generateCards(sourceFile = file, amount = 5, type = type) { status ->
                            _generationProgress.value = if (lang == "it") {
                                "[File ${index + 1}/${files.size}] $file:\n$status"
                            } else {
                                "[File ${index + 1}/${files.size}] $file:\n$status"
                            }
                        }
                        totalGenerated += count
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                _generationResult.value = if (lang == "it") {
                    "✓ Generazione massiva completata! Generate $totalGenerated nuove flashcard da ${files.size} file Markdown."
                } else {
                    "✓ Massive generation completed! Generated $totalGenerated new flashcards from ${files.size} Markdown files."
                }
            } catch (e: Exception) {
                _generationResult.value = if (lang == "it") {
                    "Errore durante la generazione massiva: ${e.localizedMessage ?: "Errore generico"}"
                } else {
                    "Error during massive generation: ${e.localizedMessage ?: "Generic error"}"
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun getStudyQueue(): Flow<List<Flashcard>> = cardDao.getStudyQueue().map { list ->
        list.map { FlashcardMapper.toDomain(it) }
    }

    suspend fun getStudyQueueSnapshot(): List<Flashcard> {
        val list = cardDao.getStudyQueue().first()
        return list.map { FlashcardMapper.toDomain(it) }
    }

    suspend fun getAllDeepDiveCardsSnapshot(): List<DeepDiveCard> {
        val list = deepDiveDao.getAllCards().first()
        return list.map { DeepDiveMapper.toDomain(it) }
    }

    suspend fun getAllDeepDiveInteractions(): List<DeepDiveInteraction> {
        val list = deepDiveDao.getAllInteractions()
        return list.map { DeepDiveMapper.toDomain(it) }
    }

    fun getAllDeepDiveCardsFlow(): Flow<List<DeepDiveCard>> {
        return deepDiveDao.getAllCards().map { list ->
            list.map { DeepDiveMapper.toDomain(it) }
        }
    }

    fun getAllFlashcardsFlow(): Flow<List<com.example.domain.models.Flashcard>> {
        return cardDao.getAllCards().map { list ->
            list.map { FlashcardMapper.toDomain(it) }
        }
    }

    suspend fun getDeepDiveBodyWordCount(cardId: String): Int {
        val card = deepDiveDao.getCardById(cardId) ?: return 100
        return card.body.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    }

    val demoInitializedFlow: Flow<Boolean> = appPreferences.demoInitializedFlow
    val selectedLanguageFlow: Flow<String> = appPreferences.selectedLanguageFlow
    
    suspend fun getStats(): Map<String, Any> {
        val allCards = cardDao.getAllCards().first()
        val total = allCards.size
        var correct = 0
        var totalAttempts = 0
        var easy = 0
        var medium = 0
        var hard = 0
        
        allCards.forEach { card ->
            correct += card.timesCorrect
            totalAttempts += card.timesShown
            when (card.difficulty) {
                "easy" -> easy++
                "medium" -> medium++
                "hard" -> hard++
            }
        }
        
        val interactions = deepDiveDao.getAllInteractions().map { DeepDiveMapper.toDomain(it) }
        val totalDwellTimeSec = interactions.sumOf { it.dwellTimeMs } / 1000
        val positiveCount = interactions.count { it.explicitFeedback == 1 }
        val negativeCount = interactions.count { it.explicitFeedback == -1 }
        
        val topicDwellTimes = interactions.groupBy { it.topic }
            .mapValues { group -> group.value.sumOf { it.dwellTimeMs } / 1000 }
            .filter { it.key.isNotBlank() }

        val tagDwellTimes = mutableMapOf<String, Long>()
        interactions.forEach { interaction ->
            val tagsList = interaction.tags.map { it.trim() }.filter { it.isNotBlank() }
            tagsList.forEach { tag ->
                tagDwellTimes[tag] = (tagDwellTimes[tag] ?: 0L) + (interaction.dwellTimeMs / 1000)
            }
        }

        val accuracy = if (totalAttempts > 0) (correct.toFloat() / totalAttempts * 100).toInt() else 0
        return mapOf(
            "total" to total,
            "correct" to correct,
            "incorrect" to (totalAttempts - correct),
            "accuracy" to accuracy,
            "easy" to easy,
            "medium" to medium,
            "hard" to hard,
            "daily_streak" to appPreferences.getDailyStreak(),
            "max_daily_streak" to appPreferences.getMaxDailyStreak(),
            "current_correct_streak" to appPreferences.getCurrentCorrectStreak(),
            "max_correct_streak" to appPreferences.getMaxCorrectStreak(),
            "dd_total_dwell_sec" to totalDwellTimeSec,
            "dd_positive_count" to positiveCount,
            "dd_negative_count" to negativeCount,
            "dd_topic_dwell" to topicDwellTimes,
            "dd_tag_dwell" to tagDwellTimes
        )
    }

    suspend fun updateCard(card: Flashcard) {
        cardDao.update(FlashcardMapper.toEntity(card))
        saveCardToGithub(card)
        saveStatsToGithub()
    }

    suspend fun insertAllCards(cards: List<Flashcard>) {
        cardDao.insertAll(cards.map { FlashcardMapper.toEntity(it) })
    }

    fun recordAnswer(isCorrect: Boolean) {
        appPreferences.recordAnswer(isCorrect)
    }

    private suspend fun getCardsFolder(): String {
        val folder = appPreferences.githubCardsFolderFlow.first().trim()
        return if (folder.isEmpty()) "flashcards" else folder.trim('/')
    }

    suspend fun saveStatsToGithub() {
        try {
            val owner = appPreferences.githubOwnerFlow.first()
            val repo = appPreferences.githubRepoFlow.first()
            val branch = appPreferences.githubBranchFlow.first()
            val token = "Bearer ${appPreferences.getGithubPat()}"
            if (owner.isBlank() || repo.isBlank() || token.isBlank()) return
            
            val cardsFolder = getCardsFolder()
            val path = "$cardsFolder/statistics.json"
            
            // Check if exists
            val existingSha = try {
                githubApi.getContent(token, owner, repo, path, branch).sha
            } catch (e: Exception) {
                null
            }

            val statsMap = getStats()
            val statsObj = StatsJson(
                total = statsMap["total"] as? Int ?: 0,
                correct = statsMap["correct"] as? Int ?: 0,
                incorrect = statsMap["incorrect"] as? Int ?: 0,
                accuracy = statsMap["accuracy"] as? Int ?: 0,
                easy = statsMap["easy"] as? Int ?: 0,
                medium = statsMap["medium"] as? Int ?: 0,
                hard = statsMap["hard"] as? Int ?: 0,
                last_updated = java.time.Instant.now().toString()
            )
            
            val statsAdapter = moshi.adapter(StatsJson::class.java)
            val contentJson = statsAdapter.toJson(statsObj)
            val contentBase64 = Base64.encodeToString(contentJson.toByteArray(), Base64.NO_WRAP)
            
            githubApi.putContent(
                token, owner, repo, path,
                GithubPutRequest(
                    message = "Update statistics",
                    content = contentBase64,
                    sha = existingSha,
                    branch = branch
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveTrackedFilesToGithub() {
        try {
            val owner = appPreferences.githubOwnerFlow.first()
            val repo = appPreferences.githubRepoFlow.first()
            val branch = appPreferences.githubBranchFlow.first()
            val token = "Bearer ${appPreferences.getGithubPat()}"
            if (owner.isBlank() || repo.isBlank() || token.isBlank()) return
            
            val cardsFolder = getCardsFolder()
            val path = "$cardsFolder/tracked_files.json"
            
            val existingSha = try {
                githubApi.getContent(token, owner, repo, path, branch).sha
            } catch (e: Exception) {
                null
            }

            val trackedList = getAllTrackedFiles()
            val trackedJsonType = Types.newParameterizedType(List::class.java, com.example.data.local.entities.TrackedFileEntity::class.java)
            val trackedJsonAdapter = moshi.adapter<List<com.example.data.local.entities.TrackedFileEntity>>(trackedJsonType)
            val contentJson = trackedJsonAdapter.toJson(trackedList)
            val contentBase64 = Base64.encodeToString(contentJson.toByteArray(), Base64.NO_WRAP)
            
            githubApi.putContent(
                token, owner, repo, path,
                GithubPutRequest(
                    message = "Update tracked files list",
                    content = contentBase64,
                    sha = existingSha,
                    branch = branch
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private suspend fun saveCardToGithub(card: Flashcard) {
        try {
            val owner = appPreferences.githubOwnerFlow.first()
            val repo = appPreferences.githubRepoFlow.first()
            val branch = appPreferences.githubBranchFlow.first()
            val token = "Bearer ${appPreferences.getGithubPat()}"
            val cardsFolder = getCardsFolder()
            val path = "$cardsFolder/${card.id}.json"
            
            // Check if exists
            val existingSha = try {
                githubApi.getContent(token, owner, repo, path, branch).sha
            } catch (e: Exception) {
                null
            }

            val contentJson = cardAdapter.toJson(card)
            val contentBase64 = Base64.encodeToString(contentJson.toByteArray(), Base64.NO_WRAP)
            
            githubApi.putContent(
                token, owner, repo, path,
                GithubPutRequest(
                    message = "Update flashcard ${card.id}",
                    content = contentBase64,
                    sha = existingSha,
                    branch = branch
                )
            )
        } catch (e: Exception) {
            e.printStackTrace() // Handle properly in real app
        }
    }

    private suspend fun fetchMarkdownFilesRecursive(
        token: String,
        owner: String,
        repo: String,
        folder: String,
        branch: String
    ): List<String> {
        return try {
            val contents = if (folder.isBlank()) {
                githubApi.getRootContents(token, owner, repo, branch)
            } else {
                githubApi.getDirectoryContents(token, owner, repo, folder, branch)
            }
            val filesList = mutableListOf<String>()
            val cardsFolder = getCardsFolder()
            for (item in contents) {
                if (item.type == "file" && item.name.endsWith(".md", ignoreCase = true)) {
                    filesList.add(item.path)
                } else if (item.type == "dir") {
                    // Skip system folder of flashcards if scanning from root
                    if (folder.isBlank() && item.name.equals(cardsFolder, ignoreCase = true)) {
                        continue
                    }
                    val subFiles = fetchMarkdownFilesRecursive(token, owner, repo, item.path, branch)
                    filesList.addAll(subFiles)
                }
            }
            filesList
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchMarkdownFiles(folder: String): List<String> {
        val owner = appPreferences.githubOwnerFlow.first()
        val repo = appPreferences.githubRepoFlow.first()
        val branch = appPreferences.githubBranchFlow.first()
        val token = "Bearer ${appPreferences.getGithubPat()}"
        
        val connectionError = verifyGithubConnection()
        if (connectionError != null) {
            throw IllegalArgumentException(connectionError)
        }
        
        return try {
            fetchMarkdownFilesRecursive(token, owner, repo, folder, branch)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchMarkdownFilesFromConfiguredFolders(): List<String> {
        val folders = appPreferences.sourceFoldersFlow.first()
        if (folders.isEmpty()) {
            return fetchMarkdownFiles("")
        }
        val allFiles = mutableListOf<String>()
        for (folder in folders) {
            try {
                val files = fetchMarkdownFiles(folder)
                allFiles.addAll(files)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (allFiles.isEmpty()) {
            return fetchMarkdownFiles("")
        }
        return allFiles.distinct()
    }

    suspend fun generateCards(
        sourceFile: String,
        amount: Int,
        type: String,
        onProgress: (String) -> Unit = {}
    ): Int {
        val maxBatchSize = 15
        if (amount > maxBatchSize) {
            var totalGenerated = 0
            val batches = (amount + maxBatchSize - 1) / maxBatchSize
            for (batchIdx in 0 until batches) {
                val batchAmount = if (batchIdx == batches - 1) {
                    amount - (batchIdx * maxBatchSize)
                } else {
                    maxBatchSize
                }
                onProgress("Avvio batch ${batchIdx + 1}/$batches (generando $batchAmount card)...")
                val batchResult = generateCardsSingleBatch(
                    sourceFile = sourceFile,
                    amount = batchAmount,
                    type = type,
                    batchIndex = batchIdx + 1,
                    totalBatches = batches,
                    onProgress = onProgress
                )
                totalGenerated += batchResult
            }
            return totalGenerated
        } else {
            return generateCardsSingleBatch(
                sourceFile = sourceFile,
                amount = amount,
                type = type,
                batchIndex = 1,
                totalBatches = 1,
                onProgress = onProgress
            )
        }
    }

    private suspend fun generateCardsSingleBatch(
        sourceFile: String,
        amount: Int,
        type: String,
        batchIndex: Int,
        totalBatches: Int,
        onProgress: (String) -> Unit = {}
    ): Int {
        try {
            onProgress("[$batchIndex/$totalBatches] Recupero configurazioni...")
            val owner = appPreferences.githubOwnerFlow.first()
            val repo = appPreferences.githubRepoFlow.first()
            val branch = appPreferences.githubBranchFlow.first()
            val token = "Bearer ${appPreferences.getGithubPat()}"
            
            onProgress("[$batchIndex/$totalBatches] Lettura file sorgente: $sourceFile...")
            val fileContentResponse = githubApi.getContent(token, owner, repo, sourceFile, branch)
            val base64Content = fileContentResponse.content?.replace("\n", "") ?: return 0
            val markdownText = String(Base64.decode(base64Content, Base64.DEFAULT))

            // Load agent.md dynamically
            val agentInstructions = try {
                context.assets.open("agent.md").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                "Generate structured educational flashcards."
            }

            val studyMode = appPreferences.studyModeFlow.first()
            val lang = appPreferences.selectedLanguageFlow.first()

            val langInstruction = if (lang == "it") {
                """
                L'utente ha selezionato la lingua ITALIANA.
                Sia la domanda, sia tutte le opzioni di risposta, sia la risposta corretta, sia la spiegazione, sia i tag/topics DEVONO essere scritti rigorosamente in lingua ITALIANA.
                Non mischiare inglese e italiano nelle risposte o spiegazioni.
                Per le card di tipo "true_false", le opzioni in "options" devono essere esattamente ["Vero", "Falso"] e il "correct_answer" deve essere esattamente uno di questi due valori.
                """.trimIndent()
            } else {
                """
                The user has selected the ENGLISH language.
                The question, all answer options, the correct answer, the explanation, and the tag/topics MUST be generated strictly in ENGLISH.
                Do not output Italian answers or explanations.
                For "true_false" type cards, the "options" array must contain exactly ["True", "False"] and the "correct_answer" must be exactly one of these two values.
                """.trimIndent()
            }

            val prompt = """
                SYSTEM INSTRUCTIONS:
                $agentInstructions

                USER TASK:
                Current Study Mode Requested: $studyMode (classic, questions, or curiosities)
                Please read the markdown text below and generate $amount items of type "$type" matching this study mode.
                
                CRITICAL INSTRUCTIONS FOR QUALITY AND LOCALIZATION:
                $langInstruction

                1. OPTIONS LENGTH: Every string in the "options" array MUST be extremely short, clear, and concise (maximum 5-8 words). DO NOT write long paragraphs or complex sentences in the options.
                2. CORRECT ANSWER MATCH: The "correct_answer" string MUST match EXACTLY (character-for-character, including casing and spelling) one of the strings inside the "options" array.
                3. QUESTIONS CLARITY: Questions must be logical, grammatically flawless, direct, and unambiguous in the requested language. No misleading or convoluted phrasing.
                4. TOPICS SPECIFICATION: The "topics" array must contain 1 to 3 short conceptual single-word tags describing the subject matter (e.g., ["Sistemista"], ["AI"], ["Database"], ["Reti"], ["Programmazione"], ["Inglese"]). DO NOT put folder paths, filenames, or file extensions (like "Appunti", ".md", etc.) in the topics.
                5. FOR "true_false" TYPE: The "options" array must contain exactly two values: ${if (lang == "it") "[\"Vero\", \"Falso\"]" else "[\"True\", \"False\"]"}. The "correct_answer" MUST be exactly one of those.

                SOURCE TEXT:
                $markdownText

                Remember: Reply with a raw JSON array matching the instructions in the system prompt. No markdown wrapper blocks.
            """.trimIndent()

            onProgress("[$batchIndex/$totalBatches] Chiamata ad OpenRouter via ${appPreferences.openRouterModelFlow.first()}...")
            val openRouterToken = "Bearer ${appPreferences.getOpenRouterKey()}"
            val model = appPreferences.openRouterModelFlow.first()
            
            val response = openRouterApi.generateCards(
                token = openRouterToken,
                request = OpenRouterRequest(
                    model = model,
                    messages = listOf(Message("user", prompt))
                )
            )

            val jsonResponse = response.choices?.firstOrNull()?.message?.content ?: return 0
            
            // Extremely robust cleaning to isolate the JSON array
            var cleanedJson = jsonResponse.trim()
            val firstBracket = cleanedJson.indexOf('[')
            val lastBracket = cleanedJson.lastIndexOf(']')
            if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
                cleanedJson = cleanedJson.substring(firstBracket, lastBracket + 1)
            } else {
                // fallback to previous cleaning logic
                if (cleanedJson.contains("```json")) {
                    cleanedJson = cleanedJson.substringAfter("```json").substringBeforeLast("```")
                } else if (cleanedJson.contains("```")) {
                    cleanedJson = cleanedJson.substringAfter("```").substringBeforeLast("```")
                }
            }
            cleanedJson = cleanedJson.trim()

            onProgress("[$batchIndex/$totalBatches] Parsing delle card generate...")
            val parsedGenerated = generatedListAdapter.fromJson(cleanedJson) ?: emptyList()
            
            val finalCards = parsedGenerated.map { gen ->
                // Clean and sanitize options and correct_answer to prevent key mismatches
                val cleanType = if (gen.type == "true_false") "true_false" else "multiple_choice"
                var cleanOptions = gen.options.map { it.trim() }.filter { it.isNotEmpty() }
                var cleanCorrectAnswer = gen.correct_answer.trim()

                if (cleanType == "true_false") {
                    cleanOptions = if (lang == "it") listOf("Vero", "Falso") else listOf("True", "False")
                    val isVero = cleanCorrectAnswer.equals("vero", ignoreCase = true) || 
                                 cleanCorrectAnswer.equals("true", ignoreCase = true) || 
                                 cleanCorrectAnswer.equals("v", ignoreCase = true) ||
                                 cleanCorrectAnswer.equals("t", ignoreCase = true)
                    cleanCorrectAnswer = if (isVero) cleanOptions[0] else cleanOptions[1]
                } else {
                    if (cleanOptions.size < 2) {
                        cleanOptions = listOf(cleanCorrectAnswer, "Opzione 2", "Opzione 3", "Opzione 4")
                    }
                    val matchIndex = cleanOptions.indexOfFirst { it.equals(cleanCorrectAnswer, ignoreCase = true) }
                    if (matchIndex != -1) {
                        cleanCorrectAnswer = cleanOptions[matchIndex]
                    } else {
                        // Correct answer was missing from options list! Insert it at index 0
                        val mutableOptions = cleanOptions.toMutableList()
                        if (mutableOptions.isNotEmpty()) {
                            mutableOptions[0] = cleanCorrectAnswer
                        } else {
                            mutableOptions.add(cleanCorrectAnswer)
                        }
                        cleanOptions = mutableOptions.shuffled()
                    }
                }

                // Map topic and subtopic, falling back to sourceFile name if empty
                val finalTopic = gen.topic?.trim()?.takeIf { it.isNotEmpty() } ?: run {
                    var fallback = sourceFile
                    if (fallback.contains("/")) {
                        fallback = fallback.substringAfterLast("/")
                    }
                    if (fallback.endsWith(".md", ignoreCase = true)) {
                        fallback = fallback.substring(0, fallback.length - 3)
                    }
                    fallback.trim()
                }

                val finalSubtopic = gen.subtopic?.trim()?.takeIf { it.isNotEmpty() } ?: "Generale"

                val finalTopics = listOf(finalTopic, finalSubtopic)

                Flashcard(
                    type = cleanType,
                    question = gen.question.trim(),
                    correct_answer = cleanCorrectAnswer,
                    options = cleanOptions,
                    explanation = gen.explanation.trim(),
                    source_file = sourceFile,
                    source_excerpt = gen.source_excerpt.trim(),
                    difficulty = gen.difficulty,
                    topics = finalTopics,
                    source_flag = gen.source_flag,
                    topic = finalTopic,
                    subtopic = finalSubtopic
                )
            }

            onProgress("[$batchIndex/$totalBatches] Salvataggio nel database locale...")
            cardDao.insertAll(finalCards.map { FlashcardMapper.toEntity(it) })
            
            // Background save to github
            finalCards.forEachIndexed { index, card ->
                onProgress("[$batchIndex/$totalBatches] Caricamento su GitHub di ${card.id}.json (${index + 1}/${finalCards.size})...")
                saveCardToGithub(card)
            }

            onProgress("[$batchIndex/$totalBatches] Aggiornamento statistiche su GitHub...")
            saveStatsToGithub()

            // Traccia il file completato
            try {
                val sha = fileContentResponse.sha ?: ""
                val trackedFile = com.example.data.local.entities.TrackedFileEntity(
                    path = sourceFile,
                    lastSha = sha,
                    lastIndexedAt = System.currentTimeMillis()
                )
                deepDiveDao.insertTrackedFile(trackedFile)
                saveTrackedFilesToGithub()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            onProgress("[$batchIndex/$totalBatches] Sincronizzazione completata! ${finalCards.size} nuove card generate e caricate su GitHub.")
            return finalCards.size
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress("[$batchIndex/$totalBatches] Errore durante la generazione: ${e.localizedMessage}")
            return 0
        }
    }

    suspend fun verifyGithubConnection(): String? {
        val owner = appPreferences.githubOwnerFlow.first()
        val repo = appPreferences.githubRepoFlow.first()
        val branch = appPreferences.githubBranchFlow.first()
        val rawToken = appPreferences.getGithubPat()
        val token = "Bearer $rawToken"
        
        if (rawToken.isBlank() || owner.isBlank() || repo.isBlank()) {
            return "Credenziali GitHub incomplete. Inserisci Token PAT, Owner e Nome Repository."
        }
        
        return try {
            githubApi.getRootContents(token, owner, repo, branch)
            null // Success, no error
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                401 -> "Token PAT non valido o scaduto (Errore 401 Unauthorized)."
                403 -> "Accesso negato. Controlla i permessi o i limiti del tuo token PAT (Errore 403 Forbidden)."
                404 -> "Repository o Branch non trovati. Verifica l'account Owner, il nome del Repository e il Branch (Errore 404 Not Found)."
                else -> "Errore GitHub (Codice ${e.code()}): ${e.message()}"
            }
        } catch (e: Exception) {
            "Errore di rete o connessione: ${e.localizedMessage ?: "impossibile connettersi a GitHub"}"
        }
    }

    suspend fun syncFlashcardsFromGithub(onProgress: (String) -> Unit): Int {
        val owner = appPreferences.githubOwnerFlow.first()
        val repo = appPreferences.githubRepoFlow.first()
        val branch = appPreferences.githubBranchFlow.first()
        val token = "Bearer ${appPreferences.getGithubPat()}"
        
        if (owner.isBlank() || repo.isBlank() || token.isBlank()) {
            throw IllegalArgumentException("Credenziali incomplete! Configura GitHub prima di sincronizzare.")
        }
        
        onProgress("Verifica connessione al repository GitHub...")
        val connectionError = verifyGithubConnection()
        if (connectionError != null) {
            throw IllegalArgumentException(connectionError)
        }
        
        val cardsFolder = getCardsFolder()
        onProgress("Lettura cartella '$cardsFolder/' su GitHub...")
        val contents = try {
            githubApi.getDirectoryContents(token, owner, repo, cardsFolder, branch)
        } catch (e: Exception) {
            onProgress("La cartella '$cardsFolder' non esiste ancora su GitHub. Sarà creata quando generi delle nuove card.")
            return 0
        }
        
        val jsonFiles = contents.filter { it.type == "file" && it.name.endsWith(".json", ignoreCase = true) }
        if (jsonFiles.isEmpty()) {
            onProgress("Nessuna flashcard trovata su GitHub nella cartella '$cardsFolder/'.")
            return 0
        }
        
        onProgress("Rilevate ${jsonFiles.size} card su GitHub. Sincronizzazione in corso...")
        var downloadedCount = 0
        
        for ((index, item) in jsonFiles.withIndex()) {
            try {
                if (item.name.equals("statistics.json", ignoreCase = true)) {
                    continue
                }
                
                if (item.name.equals("tracked_files.json", ignoreCase = true)) {
                    onProgress("Ripristino elenco file tracciati da GitHub...")
                    val fileContentResponse = githubApi.getContent(token, owner, repo, item.path, branch)
                    val base64Content = fileContentResponse.content?.replace("\n", "")
                    if (base64Content != null) {
                        val contentJson = String(Base64.decode(base64Content, Base64.DEFAULT))
                        val trackedJsonType = Types.newParameterizedType(List::class.java, com.example.data.local.entities.TrackedFileEntity::class.java)
                        val trackedJsonAdapter = moshi.adapter<List<com.example.data.local.entities.TrackedFileEntity>>(trackedJsonType)
                        val trackedList = trackedJsonAdapter.fromJson(contentJson) ?: emptyList()
                        trackedList.forEach {
                            deepDiveDao.insertTrackedFile(it)
                        }
                    }
                    continue
                }

                onProgress("Scaricamento card (${index + 1}/${jsonFiles.size}): ${item.name}...")
                val fileContentResponse = githubApi.getContent(token, owner, repo, item.path, branch)
                val base64Content = fileContentResponse.content?.replace("\n", "")
                if (base64Content != null) {
                    val contentJson = String(Base64.decode(base64Content, Base64.DEFAULT))
                    
                    if (contentJson.contains("\"hook\"") && contentJson.contains("\"body\"")) {
                        val ddCard = deepDiveCardAdapter.fromJson(contentJson)
                        if (ddCard != null) {
                            deepDiveDao.insertCard(DeepDiveMapper.toEntity(ddCard))
                            downloadedCount++
                        }
                    } else if (contentJson.contains("\"question\"") && contentJson.contains("\"correct_answer\"")) {
                        val card = cardAdapter.fromJson(contentJson)
                        if (card != null) {
                            cardDao.insert(FlashcardMapper.toEntity(card))
                            downloadedCount++
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        onProgress("Database locale sincronizzato! $downloadedCount elementi caricati.")
        return downloadedCount
    }

    suspend fun initializeDemoDeck(): Int {
        val demoCards = mutableListOf<Flashcard>()
        
        // Define base 5 True/False templates and generate 50 with slight context variations
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
                    type = "true_false",
                    question = "${base.first}$indexStr",
                    correct_answer = base.second,
                    options = listOf("Vero", "Falso"),
                    explanation = base.third,
                    source_file = "Demo_TrueFalse.md",
                    source_excerpt = base.first,
                    difficulty = if (i % 3 == 0) "easy" else if (i % 3 == 1) "medium" else "hard"
                )
            )
        }
        
        // Define base 5 Multiple Choice templates and generate 50
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
                    type = "multiple_choice",
                    question = "${base.first}$indexStr",
                    correct_answer = base.second,
                    options = base.third,
                    explanation = "La risposta corretta è ${base.second}. Questa è una card demo del sistema FlashTonnos per facilitare lo studio attivo e la memorizzazione.",
                    source_file = "Demo_MultipleChoice.md",
                    source_excerpt = base.first,
                    difficulty = if (i % 3 == 0) "easy" else if (i % 3 == 1) "medium" else "hard"
                )
            )
        }
        
        // Define base 5 Curiosities templates and generate 50
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
                    type = "multiple_choice",
                    question = "${base.first}$indexStr",
                    correct_answer = base.second,
                    options = listOf(base.second, "Altra opzione A", "Altra opzione B", "Altra opzione C").shuffled(),
                    explanation = base.third,
                    source_file = "Demo_Curiosities.md",
                    source_excerpt = base.first,
                    difficulty = if (i % 3 == 0) "easy" else if (i % 3 == 1) "medium" else "hard"
                )
            )
        }
        
        cardDao.insertAll(demoCards.map { FlashcardMapper.toEntity(it) })
        appPreferences.updateDemoInitialized(true)
        return demoCards.size
    }

    fun getGithubPat(): String = appPreferences.getGithubPat()
    fun getOpenRouterKey(): String = appPreferences.getOpenRouterKey()

    suspend fun getGithubOwner(): String = appPreferences.githubOwnerFlow.first()
    suspend fun getGithubRepo(): String = appPreferences.githubRepoFlow.first()
    suspend fun getGithubBranch(): String = appPreferences.githubBranchFlow.first()

    suspend fun updateCredentials(
        pat: String,
        owner: String,
        repo: String,
        branch: String,
        openRouterKey: String
    ) {
        appPreferences.setGithubPat(pat)
        appPreferences.setOpenRouterKey(openRouterKey)
        appPreferences.updateGithubOwner(owner)
        appPreferences.updateGithubRepo(repo)
        appPreferences.updateGithubBranch(branch)
        appPreferences.updateDemoInitialized(true)
    }

    suspend fun clearAllCards() {
        cardDao.clearAll()
        appPreferences.updateDemoInitialized(false)
    }
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class StatsJson(
    val total: Int,
    val correct: Int,
    val incorrect: Int,
    val accuracy: Int,
    val easy: Int,
    val medium: Int,
    val hard: Int,
    val last_updated: String
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class GeneratedFlashcard(
    val type: String,
    val question: String,
    val correct_answer: String,
    val options: List<String>,
    val explanation: String,
    val source_excerpt: String = "",
    val difficulty: String = "medium",
    val topics: List<String> = emptyList(),
    val topic: String? = null,
    val subtopic: String? = null,
    val source_flag: String? = null
)
