package com.example.domain.usecases

import com.example.data.github.GithubApiService
import com.example.data.local.DeepDiveDao
import com.example.data.local.entities.TrackedFileEntity
import com.example.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first

data class FileToProcess(
    val path: String,
    val sha: String
)

/**
 * Rileva file nuovi o modificati confrontando lo SHA del blob con quello tracciato localmente.
 */
class DetectChangedFilesUseCase(
    private val githubApi: GithubApiService,
    private val deepDiveDao: DeepDiveDao,
    private val appPreferences: AppPreferences
) {

    suspend fun execute(): List<FileToProcess> {
        val owner = appPreferences.githubOwnerFlow.first()
        val repo = appPreferences.githubRepoFlow.first()
        val branch = appPreferences.githubBranchFlow.first()
        val rawToken = appPreferences.getGithubPat()
        val token = "Bearer $rawToken"

        if (owner.isBlank() || repo.isBlank() || rawToken.isBlank()) {
            return emptyList()
        }

        return try {
            // 1. Recupera il tree ricorsivo completo
            val response = githubApi.getTreeRecursive(token, owner, repo, branch, recursive = 1)
            
            // 2. Ottieni le cartelle sorgente configurate
            val sourceFolders = appPreferences.sourceFoldersFlow.first()
            val cardsFolder = appPreferences.githubCardsFolderFlow.first().trim('/')

            // 3. Filtra solo i file Markdown che rientrano nelle cartelle configurate (e non sono nella cartella delle flashcard)
            val mdEntries = response.tree.filter { entry ->
                entry.type == "blob" && 
                entry.path.endsWith(".md", ignoreCase = true) &&
                !entry.path.startsWith("$cardsFolder/", ignoreCase = true) &&
                (sourceFolders.isEmpty() || sourceFolders.any { folder ->
                    val cleanFolder = folder.trim('/')
                    entry.path.startsWith("$cleanFolder/", ignoreCase = true) || entry.path.equals(cleanFolder, ignoreCase = true)
                })
            }

            // 4. Confronta lo SHA con il database locale
            val changedFiles = mutableListOf<FileToProcess>()
            for (entry in mdEntries) {
                val tracked = deepDiveDao.getTrackedFile(entry.path)
                if (tracked == null || tracked.lastSha != entry.sha) {
                    changedFiles.add(FileToProcess(entry.path, entry.sha))
                }
            }
            changedFiles
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
