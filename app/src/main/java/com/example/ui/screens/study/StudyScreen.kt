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
    val studyQueue by viewModel.studyQueue.collectAsState()
    val currentIndex by viewModel.currentCardIndex.collectAsState()
    val isFlipped by viewModel.isFlipped.collectAsState()
    val demoInitialized by viewModel.demoInitializedFlow.collectAsState(initial = false)
    val studyMode by appContainer.appPreferences.studyModeFlow.collectAsState(initial = "classic")
    
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val syncResult by viewModel.syncResult.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var isStudying by remember { mutableStateOf(false) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }

    // Intercept back press when studying to return to the Dashboard
    BackHandler(enabled = isStudying) {
        isStudying = false
        selectedAnswer = null
    }

    // Reset answer when flip resets
    LaunchedEffect(isFlipped) {
        if (!isFlipped) selectedAnswer = null
    }

    var showInitDialog by remember { mutableStateOf(false) }
    var showZeroCardsDialog by remember { mutableStateOf(false) }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (showZeroCardsDialog) {
            AlertDialog(
                onDismissRequest = { showZeroCardsDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Archivio Vuoto",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                text = {
                    Text(
                        text = "Il tuo archivio locale non contiene flashcard.\n\n" +
                               "Vuoi inizializzare un mazzo di 150 flashcard di esempio (Demo) per testare subito l'applicazione, oppure preferisci andare alla schermata di Generazione per crearne di nuove a partire dai tuoi appunti GitHub?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showZeroCardsDialog = false
                                viewModel.initializeDemoDeck()
                                Toast.makeText(context, "Mazzo di esempio inizializzato con successo!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Inizializza Deck Demo")
                        }
                        
                        FilledTonalButton(
                            onClick = {
                                showZeroCardsDialog = false
                                navController?.navigate("generate")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Vai a Genera con AI")
                        }
                        
                        OutlinedButton(
                            onClick = {
                                showZeroCardsDialog = false
                                showInitDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Modifica Credenziali GitHub")
                        }

                        TextButton(
                            onClick = { showZeroCardsDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Annulla")
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }

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
                            text = "Configura Deck Studio",
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
                            text = "Inserisci le tue credenziali GitHub e OpenRouter per connettere il tuo archivio note e iniziare a studiare.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )

                        OutlinedTextField(
                            value = patInput,
                            onValueChange = { patInput = it },
                            label = { Text("GitHub Personal Access Token (PAT)") },
                            placeholder = { Text("ghp_...") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { uriHandler.openUri("https://github.com/settings/tokens") }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Cerca o Genera Token PAT",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = ownerInput,
                            onValueChange = { ownerInput = it },
                            label = { Text("GitHub Owner (Nome Utente)") },
                            placeholder = { Text("es. octocat") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { uriHandler.openUri("https://github.com") }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Cerca Utente",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            label = { Text("Nome Repository GitHub") },
                            placeholder = { Text("es. mio-archivio-note") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val url = if (ownerInput.isNotBlank()) "https://github.com/$ownerInput?tab=repositories" else "https://github.com"
                                    uriHandler.openUri(url)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Cerca Repository",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = branchInput,
                            onValueChange = { branchInput = it },
                            label = { Text("Branch Repository") },
                            placeholder = { Text("es. main") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val url = if (ownerInput.isNotBlank() && repoInput.isNotBlank()) "https://github.com/$ownerInput/$repoInput/branches" else "https://github.com"
                                    uriHandler.openUri(url)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Cerca Branch",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = openRouterInput,
                            onValueChange = { openRouterInput = it },
                            label = { Text("OpenRouter API Key") },
                            placeholder = { Text("sk-or-...") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { uriHandler.openUri("https://openrouter.ai/keys") }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Ottieni API Key",
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
                                Toast.makeText(context, "Per favore, compila tutti i campi richiesti!", Toast.LENGTH_SHORT).show()
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
                        Text("Inizializza Deck")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showInitDialog = false }) {
                        Text("Annulla")
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
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dashboard Header
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

                    // Navigation Actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.syncDeck() }) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = "Sincronizza",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { navController?.navigate("stats") }) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = "Statistiche",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { navController?.navigate("generate") }) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Genera",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { navController?.navigate("settings") }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Impostazioni",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Dashboard Content Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // (Welcome & Stats overview card removed)

                    // Topic Filtering Section
                    val availableTopics by viewModel.availableTopics.collectAsState()
                    val selectedTopic by viewModel.selectedTopic.collectAsState()

                    if (availableTopics.isNotEmpty()) {
                        Column {
                            Text(
                                text = "Filtra per Argomento",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedTopic == null,
                                        onClick = { viewModel.selectTopic(null) },
                                        label = { Text("Tutti") },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                items(availableTopics.size) { index ->
                                    val topic = availableTopics[index]
                                    FilterChip(
                                        selected = selectedTopic == topic,
                                        onClick = { viewModel.selectTopic(topic) },
                                        label = { Text(topic) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Interactive Modes Cards (Large, beautiful with big background emojis)
                    val studyModesList = listOf(
                        Triple("classic", "Classica", "Domande multiple e Vero/Falso tradizionali"),
                        Triple("questions", "Solo Domande", "Domande dirette per richiamo attivo"),
                        Triple("curiosities", "Curiosità e Pillole", "Fatti divertenti e curiosità rapide")
                    )
                    
                    val emojisMap = mapOf(
                        "classic" to "🃏",
                        "questions" to "❓",
                        "curiosities" to "💡"
                    )

                    studyModesList.forEach { (modeKey, title, desc) ->
                        val isSelected = studyMode == modeKey
                        val emoji = emojisMap[modeKey] ?: "🃏"
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        appContainer.appPreferences.updateStudyMode(modeKey)
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                                 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Large semi-transparent background emoji
                                Text(
                                    text = emoji,
                                    fontSize = 90.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 10.dp, y = 20.dp)
                                        .alpha(0.12f)
                                )
                                
                                // Card Content
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selezionato",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier.fillMaxWidth(0.75f) // Don't overlap text with the large background emoji
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Dashboard Actions (Inizia Studio / Demo Buttons)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (studyQueue.isNotEmpty()) {
                        Button(
                            onClick = { isStudying = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "INIZIA STUDIO",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    } else {
                        // Empty states initialization helpers directly inside Dashboard
                        Text(
                            text = "Nessuna flashcard disponibile. Configura il tuo archivio note e le tue credenziali per iniziare lo studio.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        )
                        
                        Button(
                            onClick = {
                                val hasCreds = viewModel.getGithubPat().isNotBlank()
                                if (hasCreds) {
                                    showZeroCardsDialog = true
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
                                "INIZIALIZZA DECK",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        OutlinedButton(
                            onClick = { navController?.navigate("settings") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Impostazioni Avanzate")
                        }
                    }
                }
            }
        } else {
            // --- ACTIVE STUDY SESSION ---
            val card = studyQueue.getOrNull(currentIndex)
            if (card == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Animatable offset and size for gesture dragging
                val animOffsetX = remember { Animatable(0f) }
                val animOffsetY = remember { Animatable(0f) }
                val scope = rememberCoroutineScope()

                val dragX = animOffsetX.value
                val dragY = animOffsetY.value

                // Calculate scales for true/false button animations dynamically
                val maxDragPx = 300f
                val scaleDiff = 0.35f

                val scaleTrue = if (card.type == "true_false") {
                    if (dragX < 0) {
                        1.0f + (abs(dragX) / maxDragPx).coerceAtMost(1f) * scaleDiff
                    } else if (dragX > 0) {
                        1.0f - (dragX / maxDragPx).coerceAtMost(1f) * scaleDiff
                    } else 1.0f
                } else 1.0f

                val scaleFalse = if (card.type == "true_false") {
                    if (dragX > 0) {
                        1.0f + (dragX / maxDragPx).coerceAtMost(1f) * scaleDiff
                    } else if (dragX < 0) {
                        1.0f - (abs(dragX) / maxDragPx).coerceAtMost(1f) * scaleDiff
                    } else 1.0f
                } else 1.0f

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp),
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
                                isStudying = false
                                selectedAnswer = null
                            }
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Ritorna alla Dashboard",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Short aesthetic header
                        Text(
                            text = when (studyMode) {
                                "classic" -> "🃏 Studio Classico"
                                "questions" -> "❓ Solo Domande"
                                else -> "💡 Pillola Curiosità"
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
                            text = "↑ Salta  •  ↓ Posticipa  " + (if (card.type == "true_false") "•  ← Vero  •  → Falso" else ""),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                letterSpacing = 0.5.sp
                            )
                        )
                    }

                    // GORGEOUS ELEVATED CARD WITH DIFFICULTY GRADIENT AND GESTURES
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                            .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                            .pointerInput(card.id) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        scope.launch {
                                            animOffsetX.snapTo(animOffsetX.value + dragAmount.x)
                                            animOffsetY.snapTo(animOffsetY.value + dragAmount.y)
                                        }
                                    },
                                    onDragEnd = {
                                        val threshold = 140.dp.toPx()
                                        scope.launch {
                                            if (animOffsetX.value < -threshold && card.type == "true_false" && !isFlipped) {
                                                // Swipe Left -> Answer "Vero"
                                                selectedAnswer = "Vero"
                                                viewModel.submitAnswer(card, "Vero")
                                                animOffsetX.animateTo(-1000f, tween(250))
                                                animOffsetX.snapTo(0f)
                                                animOffsetY.snapTo(0f)
                                            } else if (animOffsetX.value > threshold && card.type == "true_false" && !isFlipped) {
                                                // Swipe Right -> Answer "Falso"
                                                selectedAnswer = "Falso"
                                                viewModel.submitAnswer(card, "Falso")
                                                animOffsetX.animateTo(1000f, tween(250))
                                                animOffsetX.snapTo(0f)
                                                animOffsetY.snapTo(0f)
                                            } else if (animOffsetY.value < -threshold) {
                                                // Swipe Up -> Skip card ("Salta")
                                                animOffsetY.animateTo(-1000f, tween(250))
                                                selectedAnswer = null
                                                viewModel.skipCard()
                                                Toast.makeText(context, "Card saltata ⏭️", Toast.LENGTH_SHORT).show()
                                                animOffsetX.snapTo(0f)
                                                animOffsetY.snapTo(0f)
                                            } else if (animOffsetY.value > threshold) {
                                                // Swipe Down -> Postpone card
                                                animOffsetY.animateTo(1000f, tween(250))
                                                selectedAnswer = null
                                                viewModel.postponeCard()
                                                Toast.makeText(context, "Card posticipata 🔄", Toast.LENGTH_SHORT).show()
                                                animOffsetX.snapTo(0f)
                                                animOffsetY.snapTo(0f)
                                            } else {
                                                // Release: Spring back to center
                                                launch { animOffsetX.animateTo(0f, tween(200)) }
                                                launch { animOffsetY.animateTo(0f, tween(200)) }
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
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Card Header: Type indicator + Difficulty
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(100)
                                ) {
                                    Text(
                                        text = if (card.type == "true_false") "VERO O FALSO" else "SCELTA MULTIPLA",
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
                                        text = card.difficulty.uppercase(),
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

                            // Center-Top: Card Topic / Category (Argomento)
                            val topicName = card.source_file
                                .substringBeforeLast(".md")
                                .replace("_", " ")
                                .replace("-", " ")
                                .replace("/", " / ")
                                .trim()

                            if (topicName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "📚 ARGOMENTO: ${topicName.uppercase()}",
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
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                FormattedMarkdownText(
                                    text = card.question,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 32.sp
                                    )
                                )
                            }

                            // Dynamic options based on game mode & type
                            if (studyMode == "curiosities") {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (!isFlipped) {
                                        Button(
                                            onClick = { viewModel.submitAnswer(card, "Capito") },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth().height(56.dp)
                                        ) {
                                            Icon(Icons.Default.Lightbulb, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Scopri la pillola", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }
                                }
                            } else if (studyMode == "questions" && !isFlipped) {
                                Button(
                                    onClick = { viewModel.submitAnswer(card, "Mostra Risposta") },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mostra Risposta", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                }
                            } else if (card.type == "true_false") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    AnswerButton(
                                        text = "Vero",
                                        card = card,
                                        isFlipped = isFlipped,
                                        selectedAnswer = selectedAnswer,
                                        onClick = { 
                                            selectedAnswer = "Vero"
                                            viewModel.submitAnswer(card, "Vero") 
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .scale(scaleTrue)
                                    )
                                    AnswerButton(
                                        text = "Falso",
                                        card = card,
                                        isFlipped = isFlipped,
                                        selectedAnswer = selectedAnswer,
                                        onClick = { 
                                            selectedAnswer = "Falso"
                                            viewModel.submitAnswer(card, "Falso") 
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .scale(scaleFalse)
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    card.options.forEach { option ->
                                        AnswerButton(
                                            text = option,
                                            card = card,
                                            isFlipped = isFlipped,
                                            selectedAnswer = selectedAnswer,
                                            onClick = { 
                                                selectedAnswer = option
                                                viewModel.submitAnswer(card, option) 
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            // REVEALED EXPLANATION / FEEDBACK SECTION (What I got wrong)
                            AnimatedVisibility(visible = isFlipped) {
                                val isCorrect = selectedAnswer == card.correct_answer
                                val outlineColor = MaterialTheme.colorScheme.outlineVariant
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp)
                                ) {
                                    // Feedback box stating EXACTLY what was right/wrong with explanation
                                    if (studyMode != "curiosities" && studyMode != "questions") {
                                        Surface(
                                            color = if (isCorrect) {
                                                if (isSystemInDarkTheme()) Color(0xFF0F3E22) else Color(0xFFE8F5E9)
                                            } else {
                                                if (isSystemInDarkTheme()) Color(0xFF4C1D1D) else Color(0xFFFFEBEE)
                                            },
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = 1.5.dp,
                                                color = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336)
                                            ),
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                                        contentDescription = null,
                                                        tint = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Text(
                                                        text = if (isCorrect) "RISPOSTA CORRETTA!" else "RISPOSTA ERRATA!",
                                                        style = MaterialTheme.typography.titleSmall.copy(
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = if (isCorrect) {
                                                                if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
                                                            } else {
                                                                if (isSystemInDarkTheme()) Color(0xFFE57373) else Color(0xFFC62828)
                                                            }
                                                        )
                                                    )
                                                }
                                                
                                                Spacer(modifier = Modifier.height(10.dp))
                                                
                                                if (selectedAnswer != null) {
                                                    Text(
                                                        text = "La tua risposta: $selectedAnswer",
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    )
                                                }
                                                
                                                Text(
                                                    text = "Risposta corretta: ${card.correct_answer}",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }

                                    // Deep explanation surface
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = if (studyMode == "curiosities") "💡 Lo sapevi che?" else "🎯 Spiegazione e Dettagli",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            FormattedMarkdownText(
                                                text = card.explanation,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Footer with source file
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(0.dp, Color.Transparent)
                                            .drawBehind {
                                                drawLine(
                                                    color = outlineColor,
                                                    start = Offset(0f, 0f),
                                                    end = Offset(size.width, 0f),
                                                    strokeWidth = 1.dp.toPx()
                                                )
                                            }
                                            .padding(top = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("📄", fontSize = 14.sp)
                                        Text(
                                            text = card.source_file,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Medium,
                                                fontStyle = FontStyle.Italic,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Button(
                                        onClick = { 
                                            selectedAnswer = null
                                            viewModel.nextCard() 
                                        },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(
                                            "Prossima",
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
                            text = "Sincronizzazione...",
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
                        val isError = result.contains("Errore", ignoreCase = true)
                        Icon(
                            imageVector = if (isError) Icons.Default.Cancel else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isError) "Esito Sincronizzazione" else "Sincronizzazione Completata",
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
    val isCorrectOption = text == card.correct_answer
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
                // Plain text with potential inline code
                val annotatedString = remember(segment) {
                    buildAnnotatedString {
                        val inlineSegments = segment.split("`")
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
                                append(inlineSegment)
                            }
                        }
                    }
                }
                Text(
                    text = annotatedString,
                    style = style,
                    color = color
                )
            }
        }
    }
}
