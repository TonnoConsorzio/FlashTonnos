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
            android.util.Log.e("FlashTonnos", "DetectChangedFilesUseCase: credenziali mancanti — owner='$owner' repo='$repo' token_blank=${rawToken.isBlank()}")
            throw Exception("Credenziali GitHub non configurate. Vai in Impostazioni e inserisci Token PAT, Owner e Repository.")
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

            // Se non ci sono file tracciati in Room, forza la generazione di tutti i file
            val trackedCount = deepDiveDao.getAllTrackedFiles().size
            if (trackedCount == 0) {
                android.util.Log.d("FlashTonnos", "Nessun file tracciato in locale — primo avvio, processo tutti i ${mdEntries.size} file")
                return interleaveFilesToProcess(
                    mdEntries.map { FileToProcess(it.path, it.sha) }
                )
            }

            // 4. Confronta lo SHA con il database locale
            val changedFiles = mutableListOf<FileToProcess>()
            for (entry in mdEntries) {
                val tracked = deepDiveDao.getTrackedFile(entry.path)
                if (tracked == null || tracked.lastSha != entry.sha) {
                    changedFiles.add(FileToProcess(entry.path, entry.sha))
                }
            }
            interleaveFilesToProcess(changedFiles)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun interleaveFilesToProcess(files: List<FileToProcess>): List<FileToProcess> {
        if (files.isEmpty()) return files
        val groups = files.groupBy { f ->
            f.path.substringBeforeLast("/", "")
        }
        val sortedFolders = groups.keys.sorted()
        val maxListSize = groups.values.maxOfOrNull { it.size } ?: 0
        val result = mutableListOf<FileToProcess>()
        for (i in 0 until maxListSize) {
            for (folder in sortedFolders) {
                val list = groups[folder] ?: continue
                if (i < list.size) {
                    result.add(list[i])
                }
            }
        }
        return result
    }
}
