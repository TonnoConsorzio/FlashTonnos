package com.example.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.di.AppContainer
import com.example.ui.theme.AppThemes
import com.example.ui.utils.Loc

@Composable
fun SettingsScreen(
    appContainer: AppContainer,
    navController: androidx.navigation.NavHostController? = null,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(appContainer.appPreferences, appContainer.flashcardRepository)
    )
) {
    val context = LocalContext.current
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val lang = selectedLanguage
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.updateDailyReminder(true, context)
            Toast.makeText(context, Loc.get("reminder_set_toast", lang), Toast.LENGTH_SHORT).show()
        } else {
            viewModel.updateDailyReminder(false, context)
            Toast.makeText(context, if (lang == "it") "Permesso di notifica negato" else "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    val uriHandler = LocalUriHandler.current
    val githubOwner by viewModel.githubOwner.collectAsState()
    val githubRepo by viewModel.githubRepo.collectAsState()
    val githubBranch by viewModel.githubBranch.collectAsState()
    val githubCardsFolder by viewModel.githubCardsFolder.collectAsState()
    val openRouterModel by viewModel.openRouterModel.collectAsState()
    val selectedThemeIndex by viewModel.selectedTheme.collectAsState()
    val studyMode by viewModel.studyMode.collectAsState()
    val isVerifying by viewModel.isVerifying.collectAsState()
    val verificationResult by viewModel.verificationResult.collectAsState()
    val isSyncingToGithub by viewModel.isSyncingToGithub.collectAsState()
    val syncToGithubResult by viewModel.syncToGithubResult.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val sourceFolders by viewModel.sourceFolders.collectAsState()

    var githubPat by remember { mutableStateOf(viewModel.getGithubPat()) }
    var openRouterKey by remember { mutableStateOf(viewModel.getOpenRouterKey()) }
    var foldersInput by remember(sourceFolders) {
        mutableStateOf(sourceFolders.joinToString(", "))
    }

    var patVisible by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    if (navController != null) {
                        IconButton(
                            onClick = { navController.navigateUp() },
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = if (lang == "it") "Indietro" else "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Text(
                        text = Loc.get("settings_title", lang),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }
                Text(
                    text = Loc.get("settings_desc", lang),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                )
            }
        }

        // Section: Themes
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = "Theme",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = Loc.get("visual_theme", lang),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    AppThemes.forEachIndexed { index, palette ->
                        val isSelected = selectedThemeIndex == index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) palette.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) palette.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { viewModel.updateSelectedTheme(index) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Colors indicators
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(palette.primary, CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(palette.primaryContainer, CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = Loc.get("theme_${index}_name", lang),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = Loc.get("theme_${index}_desc", lang),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selezionato",
                                    tint = palette.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Language Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = "Language",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = Loc.get("language_label", lang),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    val languages = listOf(
                        "en" to Loc.get("lang_en", lang),
                        "it" to Loc.get("lang_it", lang)
                    )

                    languages.forEach { (langKey, langTitle) ->
                        val isSelected = lang == langKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { viewModel.updateSelectedLanguage(langKey) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (langKey == "en") "🇬🇧 " else "🇮🇹 ",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = langTitle,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Study Mode
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = "Study Mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = Loc.get("default_study_mode", lang),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    val modes = listOf(
                        Triple("classic", Loc.get("mode_classic_label", lang), Loc.get("mode_classic_detail", lang)),
                        Triple("questions", Loc.get("mode_questions_label", lang), Loc.get("mode_questions_detail", lang)),
                        Triple("curiosities", Loc.get("mode_curiosities_label", lang), Loc.get("mode_curiosities_detail", lang))
                    )

                    modes.forEach { (modeKey, modeTitle, modeDesc) ->
                        val isSelected = studyMode == modeKey
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { viewModel.updateStudyMode(modeKey) }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = modeTitle,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.RadioButtonChecked,
                                        contentDescription = if (lang == "it") "Attivo" else "Active",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.RadioButtonUnchecked,
                                        contentDescription = if (lang == "it") "Inattivo" else "Inactive",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = modeDesc,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }

        // Section: OpenRouter AI Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = Loc.get("ai_config_title", lang),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = openRouterKey,
                        onValueChange = {
                            openRouterKey = it
                            viewModel.updateOpenRouterKey(it)
                        },
                        label = { Text(Loc.get("openrouter_key", lang)) },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        imageVector = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (lang == "it") "Mostra Chiave" else "Show Key",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { uriHandler.openUri("https://openrouter.ai/keys") }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = if (lang == "it") "Trova API Key" else "Get API Key",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = openRouterModel,
                        onValueChange = { viewModel.updateOpenRouterModel(it) },
                        label = { Text("AI Model ID") },
                        placeholder = { Text("openrouter/auto") },
                        trailingIcon = {
                            IconButton(onClick = { uriHandler.openUri("https://openrouter.ai/models") }) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = if (lang == "it") "Cerca Modelli AI" else "Search AI Models",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Loc.get("ai_model_desc", lang),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        // Section: Promemoria Giornalieri
        item {
            val dailyReminderEnabled by viewModel.dailyReminder.collectAsState()
            val permissionContext = LocalContext.current
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = "Promemoria",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = Loc.get("daily_reminder", lang),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = Loc.get("daily_reminder_desc", lang),
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                        
                        Switch(
                            checked = dailyReminderEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.updateDailyReminder(true, permissionContext)
                                        Toast.makeText(permissionContext, Loc.get("reminder_set_toast", lang), Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    viewModel.updateDailyReminder(false, permissionContext)
                                    Toast.makeText(permissionContext, Loc.get("reminder_disabled_toast", lang), Toast.LENGTH_SHORT).show()
                                }
                             }
                         )
                     }
                 }
             }
         }

        // Section: GitHub Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = "GitHub",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = Loc.get("github_integration", lang),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = githubPat,
                        onValueChange = {
                            githubPat = it
                            viewModel.updateGithubPat(it)
                        },
                        label = { Text(Loc.get("github_token", lang)) },
                        visualTransformation = if (patVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { patVisible = !patVisible }) {
                                    Icon(
                                        imageVector = if (patVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (lang == "it") "Mostra Token" else "Show Token",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { uriHandler.openUri("https://github.com/settings/tokens") }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = if (lang == "it") "Cerca/Genera Token PAT" else "Search/Generate PAT",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = githubOwner,
                        onValueChange = { viewModel.updateGithubOwner(it) },
                        label = { Text(Loc.get("account_owner", lang)) },
                        trailingIcon = {
                            IconButton(onClick = { uriHandler.openUri("https://github.com") }) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = if (lang == "it") "Cerca Utente" else "Search User",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = githubRepo,
                        onValueChange = { viewModel.updateGithubRepo(it) },
                        label = { Text(Loc.get("repo_name", lang)) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val url = if (githubOwner.isNotBlank()) "https://github.com/$githubOwner?tab=repositories" else "https://github.com"
                                uriHandler.openUri(url)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = if (lang == "it") "Cerca Repository" else "Search Repository",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = githubBranch,
                        onValueChange = { viewModel.updateGithubBranch(it) },
                        label = { Text(Loc.get("branch", lang)) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val url = if (githubOwner.isNotBlank() && githubRepo.isNotBlank()) "https://github.com/$githubOwner/$githubRepo/branches" else "https://github.com"
                                uriHandler.openUri(url)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = if (lang == "it") "Cerca Branch" else "Search Branch",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = githubCardsFolder,
                        onValueChange = { viewModel.updateGithubCardsFolder(it) },
                        label = { Text(Loc.get("github_cards_folder", lang)) },
                        placeholder = { Text(if (lang == "it") "es. flashcards" else "e.g. flashcards") },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = foldersInput,
                        onValueChange = { newValue ->
                            foldersInput = newValue
                            val set = newValue.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toSet()
                            viewModel.updateSourceFolders(set)
                        },
                        label = { Text(Loc.get("note_folders", lang)) },
                        placeholder = { Text(if (lang == "it") "Appunti, note, Scrittura" else "Notes, Outlines, Writing") },
                        supportingText = {
                            Text(Loc.get("note_folders_desc", lang))
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.verifyGithubConnection() },
                        enabled = !isVerifying,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(Loc.get("verifying_conn", lang))
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(Loc.get("verify_conn_btn", lang))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (lang == "it") "Sincronizzazione Manuale" else "Manual Synchronization",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (lang == "it") {
                            "Carica tutte le flashcard e i dati di avanzamento locali nel repository GitHub configurato per sincronizzarli su altri dispositivi."
                        } else {
                            "Upload all local flashcards and learning progress data to the configured GitHub repository to sync them across other devices."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.uploadLocalDataToGithub() },
                        enabled = !isSyncingToGithub && !isVerifying,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        if (isSyncingToGithub) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (lang == "it") "Caricamento in corso..." else "Uploading...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (lang == "it") "Carica tutto su GitHub" else "Upload everything to GitHub")
                        }
                    }

                    if (isSyncingToGithub && syncProgress.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = syncProgress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Section: Gestione Dati Locale (Local Data Management)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Database",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = Loc.get("local_data_mgmt", lang),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = Loc.get("local_data_desc", lang),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    var showDeleteConfirm by remember { mutableStateOf(false) }
                    var showResetStatsConfirm by remember { mutableStateOf(false) }
                    var isResettingStats by remember { mutableStateOf(false) }

                    Button(
                        onClick = { showResetStatsConfirm = true },
                        enabled = !isResettingStats,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isResettingStats) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (lang == "it") "Azzera Statistiche di Studio" else "Reset Study Statistics"
                        )
                    }

                    if (showResetStatsConfirm) {
                        AlertDialog(
                            onDismissRequest = { showResetStatsConfirm = false },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showResetStatsConfirm = false
                                        isResettingStats = true
                                        viewModel.resetStudyStatistics {
                                            isResettingStats = false
                                            Toast.makeText(
                                                context,
                                                if (lang == "it") "Statistiche ripristinate con successo!" else "Statistics successfully reset!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text(if (lang == "it") "Azzera" else "Reset")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showResetStatsConfirm = false }) {
                                    Text(Loc.get("cancel", lang))
                                }
                            },
                            title = {
                                Text(
                                    text = if (lang == "it") "Conferma Ripristino" else "Confirm Reset",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Text(
                                    text = if (lang == "it") {
                                        "Sei sicuro di voler azzerare tutti i progressi di studio, punteggi e cronologia? Le flashcard e gli approfondimenti NON verranno cancellati."
                                    } else {
                                        "Are you sure you want to reset all study progress, scores, and history? Flashcards and deep dives will NOT be deleted."
                                    }
                                )
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Loc.get("clear_cache_btn", lang))
                    }
                    
                    if (showDeleteConfirm) {
                        var confirmInput by remember { mutableStateOf("") }
                        val isConfirmed = confirmInput.trim().equals(githubOwner.trim(), ignoreCase = true)
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showDeleteConfirm = false
                                        viewModel.clearAllCards {
                                            Toast.makeText(context, Loc.get("cache_cleared_toast", lang), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = isConfirmed,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                        disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                                        disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Text(Loc.get("yes_delete", lang))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text(Loc.get("cancel", lang))
                                }
                            },
                            title = { Text(Loc.get("confirm_delete_title", lang), fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(Loc.get("confirm_delete_desc", lang))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (lang == "it") {
                                            "Digita il nome dell'account GitHub ($githubOwner) per confermare:"
                                        } else {
                                            "Type the GitHub account name ($githubOwner) to confirm:"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    OutlinedTextField(
                                        value = confirmInput,
                                        onValueChange = { confirmInput = it },
                                        placeholder = { Text(githubOwner) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }
        }

    }

    if (verificationResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearVerificationResult() },
            confirmButton = {
                val isSuccess = verificationResult?.contains("successo", ignoreCase = true) == true || verificationResult?.contains("success", ignoreCase = true) == true
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isSuccess) {
                        TextButton(
                            onClick = {
                                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clipData = android.content.ClipData.newPlainText("GitHub Connection Error Log", verificationResult ?: "")
                                clipboardManager.setPrimaryClip(clipData)
                                Toast.makeText(context, if (lang == "it") "Copiato negli appunti! 📋" else "Copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "it") "Copia errore" else "Copy error")
                        }
                    }
                    Button(onClick = { viewModel.clearVerificationResult() }) {
                        Text("OK")
                    }
                }
            },
            title = {
                val isSuccess = verificationResult?.contains("successo", ignoreCase = true) == true || verificationResult?.contains("success", ignoreCase = true) == true
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = if (isSuccess) Loc.get("conn_result_title", lang) else Loc.get("conn_error_title", lang),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Text(
                    text = verificationResult ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (syncToGithubResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearSyncResult() },
            confirmButton = {
                Button(onClick = { viewModel.clearSyncResult() }) {
                    Text("OK")
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isSuccess = syncToGithubResult?.contains("successo", ignoreCase = true) == true || syncToGithubResult?.contains("success", ignoreCase = true) == true
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = if (isSuccess) {
                            if (lang == "it") "Caricamento Completato" else "Upload Completed"
                        } else {
                            if (lang == "it") "Errore Caricamento" else "Upload Error"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Text(
                    text = syncToGithubResult ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}
