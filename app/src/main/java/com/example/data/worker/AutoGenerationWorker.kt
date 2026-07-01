package com.example.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.FlashTonnosApplication
import com.example.domain.usecases.AutoGenerationUseCase
import com.example.domain.usecases.DetectChangedFilesUseCase

/**
 * Worker di WorkManager che esegue la generazione automatica delle flashcard in background.
 */
class AutoGenerationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FlashTonnosApplication ?: return Result.failure()
        val container = app.container

        val detectUseCase = DetectChangedFilesUseCase(
            githubApi = container.githubApiService,
            deepDiveDao = container.deepDiveDao,
            appPreferences = container.appPreferences
        )

        val generateUseCase = AutoGenerationUseCase(
            githubApi = container.githubApiService,
            openRouterApi = container.openRouterService,
            deepDiveDao = container.deepDiveDao,
            repository = container.flashcardRepository,
            appPreferences = container.appPreferences,
            context = applicationContext
        )

        return try {
            // Rileva file modificati/nuovi
            val filesToProcess = detectUseCase.execute()
            if (filesToProcess.isNotEmpty()) {
                // Esegui la generazione in background
                generateUseCase.execute(filesToProcess) { progress ->
                    // Possiamo loggare il progresso o aggiornare lo stato se necessario
                    println("Background AutoGen: $progress")
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
