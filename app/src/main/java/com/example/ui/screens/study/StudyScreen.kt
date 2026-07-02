package com.example.ui.screens.study

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.di.AppContainer
import com.example.domain.models.Flashcard
import com.example.domain.repository.FlashcardRepository
import com.example.ui.theme.*
import com.example.ui.utils.Loc
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun StudyScreen(
    appContainer: AppContainer,
    navController: androidx.navigation.NavHostController? = null,
    viewModel: StudyViewModel = viewModel(
        factory = StudyViewModel.Factory(appContainer.flashcardRepository)
    )
) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val lang = selectedLanguage
    val studyQueue by viewModel.studyQueue.collectAsState()
    val currentIndex by viewModel.currentCardIndex.collectAsState()
    val isFlipped by viewModel.isFlipped.collectAsState()
    val demoInitialized by viewModel.demoInitializedFlow.collectAsState(initial = false)
    val studyMode by appContainer.appPreferences.studyModeFlow.collectAsState(initial = "classic")
    
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val syncResult by viewModel.syncResult.collectAsState()
    
    val scrollState = rememberScrollState()
    var pullDistance by remember { mutableStateOf(0f) }

    LaunchedEffect(isSyncing) {
        if (!isSyncing) {
            pullDistance = 0f
        }
    }
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val isStudying by viewModel.isStudying.collectAsState()
    var selectedAnswer by remember { mutableStateOf<String?>(null) }

    // Intercept back press when studying to return to the Dashboard
    BackHandler(enabled = isStudying) {
        viewModel.setStudying(false)
        selectedAnswer = null
    }

    // Reset answer when flip resets
    LaunchedEffect(isFlipped) {
        if (!isFlipped) selectedAnswer = null
    }

    var showInitDialog by remember { mutableStateOf(false) }
    var patInput by remember { mutableStateOf("") }
    var ownerInput by remember { mutableStateOf("") }
    var repoInput by remember { mutableStateOf("") }
    var branchInput by remember { mutableStateOf("main") }
    var openRouterInput by remember { mutableStateOf("") }

    LaunchedEffect(showInitDialog) {
        if (showInitDialog) {
            patInput = viewModel.getGithubPat()
            openRouterInput = viewModel.getOpenRouterKey()
            viewModel.getGithubOwner { ownerInput = it }
            viewModel.getGithubRepo { repoInput = it }
            viewModel.getGithubBranch { branchInput = it }
        }
    }

    var showFilterDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (showInitDialog) {
            AlertDialog(
                onDismissRequest = { showInitDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = Loc.get("github_integration", lang),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (lang == "it") {
                                "Inserisci le tue credenziali GitHub e OpenRouter per connettere il tuo archivio note e iniziare a studiare."
                            } else {
                                "Enter your GitHub and OpenRouter credentials to connect your notes archive and start studying."
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )

                        OutlinedTextField(
                            value = patInput,
                            onValueChange = { patInput = it },
                            label = { Text(Loc.get("github_token", lang)) },
                            placeholder = { Text("ghp_...") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { uriHandler.openUri("https://github.com/settings/tokens") }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = if (lang == "it") "Cerca o Genera Token PAT" else "Search or Generate PAT",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = ownerInput,
                            onValueChange = { ownerInput = it },
                            label = { Text(Loc.get("account_owner", lang)) },
                            placeholder = { Text(if (lang == "it") "es. octocat" else "e.g. octocat") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { uriHandler.openUri("https://github") }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = if (lang == "it") "Cerca Utente" else "Search User",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            label = { Text(Loc.get("repo_name", lang)) },
                            placeholder = { Text(if (lang == "it") "es. mio-archivio-note" else "e.g. my-notes-repo") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val url = if (ownerInput.isNotBlank()) "https://github.com/$ownerInput?tab=repositories" else "https://github.com"
                                    uriHandler.openUri(url)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = if (lang == "it") "Cerca Repository" else "Search Repository",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = branchInput,
                            onValueChange = { branchInput = it },
                            label = { Text(Loc.get("branch", lang)) },
                            placeholder = { Text(if (lang == "it") "es. main" else "e.g. main") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val url = if (ownerInput.isNotBlank() && repoInput.isNotBlank()) "https://github.com/$ownerInput/$repoInput/branches" else "https://github.com"
                                    uriHandler.openUri(url)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = if (lang == "it") "Cerca Branch" else "Search Branch",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = openRouterInput,
                            onValueChange = { openRouterInput = it },
                            label = { Text(Loc.get("openrouter_key", lang)) },
                            placeholder = { Text("sk-or-...") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { uriHandler.openUri("https://openrouter.ai/keys") }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = if (lang == "it") "Ottieni API Key" else "Get API Key",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (patInput.isBlank() || ownerInput.isBlank() || repoInput.isBlank() || openRouterInput.isBlank()) {
                                Toast.makeText(context, Loc.get("toast_please_fill_fields", lang), Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.updateCredentials(
                                    pat = patInput,
                                    owner = ownerInput,
                                    repo = repoInput,
                                    branch = branchInput,
                                    openRouterKey = openRouterInput
                                )
                                showInitDialog = false
                                viewModel.syncDeck()
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(Loc.get("init_deck_btn", lang))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showInitDialog = false }) {
                        Text(Loc.get("cancel", lang))
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }

        if (!isStudying) {
            // --- DASHBOARD MODE ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp)
                    .pointerInput(isSyncing) {
                        if (isSyncing) return@pointerInput
                        detectDragGestures(
                            onDragStart = { },
                            onDrag = { change, dragAmount ->
                                if (scrollState.value == 0 && dragAmount.y > 0f) {
                                    change.consume()
                                    pullDistance = (pullDistance + dragAmount.y).coerceAtMost(300f)
                                } else if (pullDistance > 0f && dragAmount.y < 0f) {
                                    change.consume()
                                    pullDistance = (pullDistance + dragAmount.y).coerceAtLeast(0f)
                                }
                            },
                            onDragEnd = {
                                if (pullDistance > 200f) {
                                    viewModel.syncDeck()
                                    Toast.makeText(context, Loc.get("sync_started", lang), Toast.LENGTH_SHORT).show()
                                }
                                pullDistance = 0f
                            },
                            onDragCancel = {
                                pullDistance = 0f
                            }
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated Pull-to-Refresh Visual Feedback Indicator
                AnimatedVisibility(visible = pullDistance > 0f || isSyncing) {
                    val progress = if (isSyncing) 1f else (pullDistance / 200f).coerceAtMost(1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(100))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            CircularProgressIndicator(
                                progress = progress,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                            Text(
                                text = if (isSyncing) Loc.get("syncing_msg", lang) else if (progress >= 1f) Loc.get("release_sync", lang) else Loc.get("pull_sync", lang),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // Dashboard Header - Polished, clean, with ONLY settings in the top right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "FlashTonnos 🐟",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }

                    IconButton(onClick = { navController?.navigate("settings") }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = Loc.get("settings_title", lang),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Topic Filtering Dialog (Modern, high contrast, dark theme aware)
                if (showFilterDialog) {
                    val availableTopics by viewModel.availableTopics.collectAsState()
                    val selectedTopic by viewModel.selectedTopic.collectAsState()

                    AlertDialog(
                        onDismissRequest = { showFilterDialog = false },
                        title = {
                            Text(
                                text = "Filtra per Argomento",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Option for "All"
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectTopic(null)
                                            showFilterDialog = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedTopic == null) MaterialTheme.colorScheme.primaryContainer
                                                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = if (lang == "it") "Tutti gli argomenti" else "All Topics",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = if (selectedTopic == null) MaterialTheme.colorScheme.onPrimaryContainer
                                                   else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // List of other topics
                                availableTopics.forEach { topic ->
                                    val isSelected = selectedTopic == topic
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.selectTopic(topic)
                                                showFilterDialog = false
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Box(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = topic.uppercase(),
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                       else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showFilterDialog = false }) {
                                Text(if (lang == "it") "Chiudi" else "Close")
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

                // Dashboard Content Area (Lag-free, beautifully styled)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val availableTopics by viewModel.availableTopics.collectAsState()
                    val selectedTopic by viewModel.selectedTopic.collectAsState()

                    // 1. Topic Filtering Bar
                    if (availableTopics.isNotEmpty()) {
                        Card(
                            onClick = { showFilterDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = if (lang == "it") "Filtro Argomento" else "Topic Filter",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = selectedTopic?.uppercase() ?: (if (lang == "it") "TUTTI GLI ARGOMENTI" else "ALL TOPICS"),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (studyQueue.isNotEmpty()) {
                        // 2. LARGE MODE BUTTONS / CARDS (1-Tap to study!)
                        
                        // Mode 1: APPROFONDIMENTO (Deep Dives & Curiosities - 1 Tap to Vertical TikTok feed)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp)
                                .wrapContentHeight()
                                .clickable {
                                    navController?.navigate("deep_dive")
                                }
                                .testTag("deep_dive_hub_card"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "💡",
                                    fontSize = 90.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 10.dp, y = 20.dp)
                                        .alpha(0.12f)
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (lang == "it") "Approfondimento" else "Deep Dives",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (lang == "it") "Feed verticale per un apprendimento immediato tramite pillole e curiosità" 
                                               else "Vertical feed for rapid learning via study cards and curiosity pills",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier.fillMaxWidth(0.75f)
                                    )
                                }
                            }
                        }

                        // Mode 2: CLASSICA (Classic study mode - 1 Tap to start!)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 130.dp)
                                .wrapContentHeight()
                                .clickable {
                                    coroutineScope.launch {
                                        appContainer.appPreferences.updateStudyMode("classic")
                                        viewModel.setStudying(true)
                                    }
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "🃏",
                                    fontSize = 90.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 10.dp, y = 20.dp)
                                        .alpha(0.12f)
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (lang == "it") "Classica" else "Classic Flashcards",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (lang == "it") "Ripassa con le flashcard tradizionali per fissare i concetti" 
                                               else "Review traditional double-sided cards to lock in key concepts",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier.fillMaxWidth(0.75f)
                                    )
                                }
                            }
                        }

                        // Mode 3: SOLO DOMANDE (Questions study mode - 1 Tap to start!)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 130.dp)
                                .wrapContentHeight()
                                .clickable {
                                    coroutineScope.launch {
                                        appContainer.appPreferences.updateStudyMode("questions")
                                        viewModel.setStudying(true)
                                    }
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "❓",
                                    fontSize = 90.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 10.dp, y = 20.dp)
                                        .alpha(0.12f)
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (lang == "it") "Solo Domande" else "Questions Only",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (lang == "it") "Mettiti alla prova con domande a scelta multipla e vero/falso" 
                                               else "Test yourself with multiple choice and true/false questions",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier.fillMaxWidth(0.75f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 3. UTILITY ACTIONS ROW: AI Generator & Statistics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Generatore IA Card
                            Card(
                                onClick = { navController?.navigate("generate") },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 100.dp)
                                    .wrapContentHeight(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                            ) {
                                Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                                    Text(
                                        text = "✨",
                                        fontSize = 32.sp,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 4.dp, y = 6.dp)
                                            .alpha(0.2f)
                                    )
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (lang == "it") "Generatore IA" else "AI Generator",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            // Statistiche Card
                            Card(
                                onClick = { navController?.navigate("stats") },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 100.dp)
                                    .wrapContentHeight(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                            ) {
                                Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                                    Text(
                                        text = "📊",
                                        fontSize = 32.sp,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 4.dp, y = 6.dp)
                                            .alpha(0.2f)
                                    )
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BarChart,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (lang == "it") "Statistiche" else "Statistics",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty deck instructions (when they have no cards yet)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = Loc.get("empty_deck_dashboard_msg", lang),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                            )
                            
                            Button(
                                onClick = {
                                    val hasCreds = viewModel.getGithubPat().isNotBlank()
                                    if (hasCreds) {
                                        viewModel.syncDeck()
                                        Toast.makeText(context, Loc.get("sync_started_toast", lang), Toast.LENGTH_SHORT).show()
                                    } else {
                                        showInitDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    Loc.get("init_deck_btn", lang),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                 )
                            }

                            OutlinedButton(
                                onClick = { showInitDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(Loc.get("configure_repository_btn", lang))
                            }
                        }
                    }
                }
            }
        } else {
            // --- ACTIVE STUDY SESSION ---
            if (studyQueue.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val pagerState = rememberPagerState(initialPage = currentIndex) { studyQueue.size }
                val scope = rememberCoroutineScope()
                
                LaunchedEffect(pagerState.currentPage) {
                    viewModel.setCurrentCardIndex(pagerState.currentPage)
                }
                
                LaunchedEffect(currentIndex) {
                    if (pagerState.currentPage != currentIndex && currentIndex in 0 until studyQueue.size) {
                        pagerState.scrollToPage(currentIndex)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar with "Indietro" button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.setStudying(false)
                                selectedAnswer = null
                            }
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = Loc.get("back_to_dashboard_cd", lang),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Short aesthetic header
                        Text(
                            text = when (studyMode) {
                                "classic" -> Loc.get("title_classic", lang)
                                "questions" -> Loc.get("title_questions", lang)
                                else -> Loc.get("title_curiosities", lang)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )

                        // Placeholder to balance
                        Box(modifier = Modifier.size(24.dp))
                    }

                    // Gestures Instruction visual hint (TikTok style - clean, modern)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isFlipped) {
                                if (lang == "it") "Scorri verticalmente per la prossima card" else "Swipe vertically for next card"
                            } else {
                                if (lang == "it") "Rispondi per sbloccare lo scorrimento • Swipe orizzontale per scartare" else "Answer to unlock swipe • Horizontal swipe to discard"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                letterSpacing = 0.5.sp
                            )
                        )
                    }

                    // VerticalPager allows user scrolling ONLY if the card is already flipped (so they saw the answer/explanation)
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        userScrollEnabled = isFlipped,
                        key = { page -> studyQueue.getOrNull(page)?.id ?: page.toString() }
                    ) { page ->
                        val card = studyQueue.getOrNull(page)
                        if (card != null) {
                            // Animatable offset for horizontal swipe to discard
                            val animOffsetX = remember { Animatable(0f) }
                            val dragX = animOffsetX.value

                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp, vertical = 20.dp)
                                    .offset { IntOffset(dragX.roundToInt(), 0) }
                                    .pointerInput(card.id) {
                                        detectDragGestures(
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                scope.launch {
                                                    animOffsetX.snapTo(animOffsetX.value + dragAmount.x)
                                                }
                                            },
                                            onDragEnd = {
                                                val thresholdPx = 140.dp.toPx()
                                                scope.launch {
                                                    if (animOffsetX.value > thresholdPx || animOffsetX.value < -thresholdPx) {
                                                        animOffsetX.animateTo(if (animOffsetX.value > 0) 1000f else -1000f, tween(250))
                                                        viewModel.discardCard(card)
                                                        Toast.makeText(context, if (lang == "it") "Card scartata" else "Card discarded", Toast.LENGTH_SHORT).show()
                                                        animOffsetX.snapTo(0f)
                                                    } else {
                                                        animOffsetX.animateTo(0f, tween(200))
                                                    }
                                                }
                                            }
                                        )
                                    },
                                shape = RoundedCornerShape(32.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(getCardGradient(card.difficulty))
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Card Header: Type indicator + Difficulty + Scarta Button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                                shape = RoundedCornerShape(100)
                                            ) {
                                                Text(
                                                    text = if (card.type == "true_false") Loc.get("true_false_label", lang) else Loc.get("multiple_choice_label", lang),
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontSize = 9.sp,
                                                        letterSpacing = 1.sp
                                                    )
                                                )
                                            }
                                            
                                            Surface(
                                                color = when(card.difficulty.lowercase()) {
                                                    "easy" -> Color(0xFFE8F5E9).copy(alpha = 0.8f)
                                                    "medium" -> Color(0xFFFFF3E0).copy(alpha = 0.8f)
                                                    else -> Color(0xFFFFEBEE).copy(alpha = 0.8f)
                                                },
                                                shape = RoundedCornerShape(100)
                                            ) {
                                                Text(
                                                    text = Loc.get("${card.difficulty.lowercase()}_label", lang).uppercase(),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = when(card.difficulty.lowercase()) {
                                                            "easy" -> Color(0xFF2E7D32)
                                                            "medium" -> Color(0xFFE65100)
                                                            else -> Color(0xFFC62828)
                                                        },
                                                        fontSize = 9.sp
                                                    )
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.discardCard(card)
                                                Toast.makeText(context, if (lang == "it") "Card scartata" else "Card discarded", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = if (lang == "it") "Scarta card" else "Discard card",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Center-Top: Card Topic / Category
                                    val cleanTopic = if (card.topic.isNotBlank()) {
                                        card.topic
                                    } else {
                                        val parts = card.source_file.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                                        var folder = ""
                                        if (parts.size >= 2) {
                                            val last = parts.last()
                                            folder = if (last.endsWith(".md", ignoreCase = true)) parts[parts.size - 2] else last
                                        } else if (parts.isNotEmpty()) {
                                            val last = parts.last()
                                            folder = if (last.endsWith(".md", ignoreCase = true)) last.substringBeforeLast(".md") else last
                                        }
                                        if (folder.equals("appunti", ignoreCase = true)) "GENERALE" else folder
                                    }.trim().uppercase()

                                    if (cleanTopic.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = cleanTopic,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.secondary,
                                                letterSpacing = 1.2.sp
                                            ),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Centered Question Text (tra virgolette)
                                    Box(
                                        modifier = Modifier
                                            .weight(if (isFlipped) 0.8f else 1f)
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val questionStyle = if (card.question.length > 80 || isFlipped) {
                                            MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                lineHeight = 22.sp
                                            )
                                        } else {
                                            MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                lineHeight = 28.sp
                                            )
                                        }
                                        FormattedMarkdownText(
                                            text = card.question,
                                            style = questionStyle
                                        )
                                    }

                                    // Interactive content based on game mode & type
                                    if (!isFlipped) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            if (studyMode == "curiosities") {
                                                Button(
                                                    onClick = { 
                                                        selectedAnswer = "Capito"
                                                        viewModel.submitAnswer(card, "Capito") 
                                                    },
                                                    shape = RoundedCornerShape(16.dp),
                                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                                ) {
                                                    Icon(Icons.Default.Lightbulb, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = Loc.get("btn_discover_pill", lang),
                                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                }
                                            } else if (studyMode == "questions") {
                                                Button(
                                                    onClick = { 
                                                        selectedAnswer = "Mostra Risposta"
                                                        viewModel.submitAnswer(card, "Mostra Risposta") 
                                                    },
                                                    shape = RoundedCornerShape(16.dp),
                                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                                ) {
                                                    Icon(Icons.Default.Visibility, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = Loc.get("btn_show_answer", lang),
                                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                }
                                            } else {
                                                if (card.type == "true_false") {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        val veroLabel = if (lang == "it") "Vero" else "True"
                                                        val falsoLabel = if (lang == "it") "Falso" else "False"
                                                        AnswerButton(
                                                            text = veroLabel,
                                                            card = card,
                                                            isFlipped = false,
                                                            selectedAnswer = selectedAnswer,
                                                            onClick = { 
                                                                selectedAnswer = veroLabel
                                                                viewModel.submitAnswer(card, veroLabel) 
                                                            },
                                                            modifier = Modifier.weight(1f).height(48.dp)
                                                        )
                                                        AnswerButton(
                                                            text = falsoLabel,
                                                            card = card,
                                                            isFlipped = false,
                                                            selectedAnswer = selectedAnswer,
                                                            onClick = { 
                                                                selectedAnswer = falsoLabel
                                                                viewModel.submitAnswer(card, falsoLabel) 
                                                            },
                                                            modifier = Modifier.weight(1f).height(48.dp)
                                                        )
                                                    }
                                                } else {
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        card.options.forEach { option ->
                                                            AnswerButton(
                                                                text = option,
                                                                card = card,
                                                                isFlipped = false,
                                                                selectedAnswer = selectedAnswer,
                                                                onClick = { 
                                                                    selectedAnswer = option
                                                                    viewModel.submitAnswer(card, option) 
                                                                },
                                                                modifier = Modifier.fillMaxWidth().height(40.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Flipped feedback and explanation
                                        Column(
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val isCorrect = if (card.type == "true_false") {
                                                val selVero = selectedAnswer == "Vero" || selectedAnswer == "True" || selectedAnswer == "V" || selectedAnswer == "T"
                                                val corrVero = card.correct_answer == "Vero" || card.correct_answer == "True" || card.correct_answer == "V" || card.correct_answer == "T"
                                                selVero == corrVero
                                            } else {
                                                selectedAnswer == card.correct_answer
                                            }
                                            
                                            val isNeutralFlip = selectedAnswer == null || 
                                                                selectedAnswer == "Mostra Risposta" || 
                                                                selectedAnswer == "Capito" || 
                                                                selectedAnswer == "Show Answer" ||
                                                                selectedAnswer == "Discover Pill" ||
                                                                selectedAnswer == Loc.get("btn_show_answer", lang) ||
                                                                selectedAnswer == Loc.get("btn_discover_pill", lang) ||
                                                                studyMode == "questions" || 
                                                                studyMode == "curiosities"

                                            val feedbackTitle = if (isNeutralFlip) {
                                                Loc.get("ref_answer_title", lang)
                                            } else if (isCorrect) {
                                                Loc.get("feedback_correct", lang)
                                            } else {
                                                Loc.get("feedback_incorrect", lang)
                                            }

                                            val boxColor = if (isNeutralFlip) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else if (isCorrect) {
                                                if (isSystemInDarkTheme()) Color(0xFF0F3E22) else Color(0xFFE8F5E9)
                                            } else {
                                                if (isSystemInDarkTheme()) Color(0xFF4C1D1D) else Color(0xFFFFEBEE)
                                            }

                                            val borderColor = if (isNeutralFlip) {
                                                MaterialTheme.colorScheme.secondary
                                            } else if (isCorrect) {
                                                Color(0xFF4CAF50)
                                            } else {
                                                Color(0xFFF44336)
                                            }

                                            val titleColor = if (isNeutralFlip) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else if (isCorrect) {
                                                if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
                                            } else {
                                                if (isSystemInDarkTheme()) Color(0xFFE57373) else Color(0xFFC62828)
                                            }

                                            val icon = if (isNeutralFlip) {
                                                Icons.Default.Info
                                            } else if (isCorrect) {
                                                Icons.Default.CheckCircle
                                            } else {
                                                Icons.Default.Cancel
                                            }

                                            // Compact feedback header
                                            Surface(
                                                color = boxColor,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(imageVector = icon, contentDescription = null, tint = borderColor, modifier = Modifier.size(18.dp))
                                                    Text(text = feedbackTitle, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = titleColor))
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    Text(
                                                        text = "${if (lang == "it") "Corr: " else "Correct: "}${card.correct_answer}",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                    )
                                                }
                                            }

                                            // Scrollable Explanation Container
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(12.dp)
                                                        .verticalScroll(rememberScrollState())
                                                ) {
                                                    Text(
                                                        text = if (studyMode == "curiosities") Loc.get("did_you_know_title", lang) else Loc.get("explanation_details_title", lang),
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    FormattedMarkdownText(
                                                        text = card.explanation,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text("📄", fontSize = 10.sp)
                                                        Text(
                                                            text = card.source_file.substringAfterLast("/"),
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Medium,
                                                                fontStyle = FontStyle.Italic,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                                fontSize = 10.sp
                                                            )
                                                        )
                                                    }
                                                }
                                            }

                                            // Next Card Action Button (anchored at bottom)
                                            Button(
                                                onClick = { 
                                                    selectedAnswer = null
                                                    viewModel.nextCard() 
                                                },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text(
                                                    text = Loc.get("btn_next_card", lang),
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isSyncing) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = Loc.get("syncing_dialog_title", lang),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = syncStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

        syncResult?.let { result ->
            AlertDialog(
                onDismissRequest = { viewModel.clearSyncResult() },
                confirmButton = {
                    Button(onClick = { viewModel.clearSyncResult() }) {
                        Text("Ok")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isError = result.contains("Errore", ignoreCase = true) || result.contains("Error", ignoreCase = true)
                        Icon(
                            imageVector = if (isError) Icons.Default.Cancel else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isError) Loc.get("sync_result_title", lang) else Loc.get("sync_completed_title", lang),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                text = {
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
fun getCardGradient(difficulty: String, darkTheme: Boolean = isSystemInDarkTheme()): Brush {
    return when (difficulty.lowercase()) {
        "easy" -> {
            if (darkTheme) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F2D1D),
                        Color(0xFF071F13)
                    )
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE8F5E9),
                        Color(0xFFC8E6C9)
                    )
                )
            }
        }
        "medium" -> {
            if (darkTheme) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF331E00),
                        Color(0xFF1F1100)
                    )
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF3E0),
                        Color(0xFFFFE0B2)
                    )
                )
            }
        }
        "hard" -> {
            if (darkTheme) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF3B0F0F),
                        Color(0xFF260808)
                    )
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFEBEE),
                        Color(0xFFFFCDD2)
                    )
                )
            }
        }
        else -> {
            if (darkTheme) {
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    }
}

@Composable
fun AnswerButton(
    text: String,
    card: Flashcard,
    isFlipped: Boolean,
    selectedAnswer: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCorrectOption = if (card.type == "true_false") {
        val textVero = text == "Vero" || text == "True" || text == "V" || text == "T"
        val corrVero = card.correct_answer == "Vero" || card.correct_answer == "True" || card.correct_answer == "V" || card.correct_answer == "T"
        textVero == corrVero
    } else {
        text == card.correct_answer
    }
    val isSelected = text == selectedAnswer
    
    val buttonColors = if (isFlipped) {
        when {
            isCorrectOption -> ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
            isSelected && !isCorrectOption -> ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
            else -> ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    } else {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }

    FilledTonalButton(
        onClick = onClick,
        enabled = !isFlipped,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(64.dp),
        colors = buttonColors
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun FormattedMarkdownText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val segments = text.split("```")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEachIndexed { index, segment ->
            if (index % 2 == 1) {
                // Multi-line code block
                val lines = segment.trim().lines()
                val hasLang = lines.firstOrNull()?.let { firstLine ->
                    firstLine.isNotBlank() && !firstLine.contains(" ") && firstLine.length < 15
                } ?: false
                val language = if (hasLang) lines.first() else null
                val codeContent = if (hasLang) lines.drop(1).joinToString("\n") else segment.trim()

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = Color(0xFF1E1E1E), // Dark code-block theme
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (language != null) {
                            Text(
                                text = language.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = codeContent,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = Color(0xFFE0E0E0),
                                    lineHeight = 18.sp
                                )
                            )
                        }
                    }
                }
            } else {
                // Parse standard markdown line-by-line to handle headers, lists, bold/italic beautifully
                val lines = segment.split("\n")
                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isNotEmpty()) {
                        // Check if it's a heading
                        val isHeading = line.startsWith("#")
                        var cleanLine = line
                        var headingLevel = 0
                        if (isHeading) {
                            while (cleanLine.startsWith("#")) {
                                headingLevel++
                                cleanLine = cleanLine.substring(1)
                            }
                            cleanLine = cleanLine.trim()
                        }

                        // Check if it's a list item
                        val isBulletList = cleanLine.startsWith("* ") || cleanLine.startsWith("- ") || cleanLine.startsWith("• ")
                        val isNumberedList = !isBulletList && cleanLine.firstOrNull()?.isDigit() == true && cleanLine.contains(". ") && cleanLine.substringBefore(". ").all { it.isDigit() }
                        
                        if (isBulletList) {
                            cleanLine = cleanLine.substring(2).trim()
                        } else if (isNumberedList) {
                            cleanLine = cleanLine.substringAfter(". ").trim()
                        }

                        // Parse inline markdown
                        val annotatedString = buildAnnotatedString {
                            if (isBulletList) {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                                    append("•  ")
                                }
                            } else if (isNumberedList) {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                                    append(rawLine.trim().substringBefore(". ") + ".  ")
                                }
                            }

                            val inlineSegments = cleanLine.split("`")
                            inlineSegments.forEachIndexed { inlineIndex, inlineSegment ->
                                if (inlineIndex % 2 == 1) {
                                    // Inline code segment
                                    withStyle(
                                        style = SpanStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD63384), // Magenta/Coral pink code color
                                            background = Color(0x15D63384)
                                        )
                                    ) {
                                        append(inlineSegment)
                                    }
                                } else {
                                    // Parse bold and italic within non-code segments
                                    val boldSegments = inlineSegment.split("**")
                                    boldSegments.forEachIndexed { boldIdx, boldSeg ->
                                        val isBold = (boldIdx % 2 == 1)
                                        val italicSegments = boldSeg.split("*")
                                        italicSegments.forEachIndexed { italicIdx, italicSeg ->
                                            val isItalic = (italicIdx % 2 == 1)
                                            val underItalicSegments = italicSeg.split("_")
                                            underItalicSegments.forEachIndexed { underIdx, underSeg ->
                                                val isUnderItalic = (underIdx % 2 == 1)
                                                val finalWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                                                val finalStyle = if (isItalic || isUnderItalic) FontStyle.Italic else FontStyle.Normal
                                                
                                                withStyle(
                                                    style = SpanStyle(
                                                        fontWeight = finalWeight,
                                                        fontStyle = finalStyle
                                                    )
                                                ) {
                                                    append(underSeg)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Determine text style for heading
                        val lineStyle = if (isHeading) {
                            when (headingLevel) {
                                1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                        } else {
                            style
                        }

                        Text(
                            text = annotatedString,
                            style = lineStyle,
                            color = if (isHeading) Color.Unspecified else color,
                            modifier = Modifier.padding(
                                start = if (isBulletList || isNumberedList) 12.dp else 0.dp,
                                top = if (isHeading) 6.dp else 0.dp,
                                bottom = if (isHeading) 2.dp else 0.dp
                            )
                        )
                    }
                }
            }
        }
    }
}
