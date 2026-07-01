package com.example.ui.screens.deepdive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.di.AppContainer
import com.example.domain.models.DeepDiveCard
import com.example.ui.utils.Loc
import kotlinx.coroutines.delay

/**
 * Schermata di visualizzazione degli Approfondimenti (Deep Dive) in stile TikTok.
 * Dispone di un VerticalPager che permette lo scorrimento verticale,
 * con tracciamento automatico del tempo di permanenza (dwell time) per ciascuna card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepDiveScreen(
    appContainer: AppContainer,
    navController: NavController? = null,
    viewModel: DeepDiveViewModel = viewModel(
        factory = DeepDiveViewModel.Factory(appContainer)
    )
) {
    val lang by appContainer.flashcardRepository.selectedLanguageFlow.collectAsState(initial = "en")
    val feed by viewModel.feedState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val topics by viewModel.availableTopics.collectAsState()
    val selectedTopic by viewModel.selectedTopic.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (lang == "it") "Approfondimenti" else "Deep Dives",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController?.navigateUp() },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (lang == "it") "Indietro" else "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. Barra orizzontale dei filtri per argomento (Topic Chips)
            if (topics.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = selectedTopic == null,
                            onClick = { viewModel.selectTopic(null) },
                            label = { Text(if (lang == "it") "Tutti" else "All") }
                        )
                    }
                    items(topics) { topic ->
                        FilterChip(
                            selected = selectedTopic == topic,
                            onClick = { viewModel.selectTopic(topic) },
                            label = { Text(topic) }
                        )
                    }
                }
            }

            // 2. Feed verticale stile TikTok delle Deep Dive
            if (feed.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = if (lang == "it") {
                                "Nessun approfondimento disponibile.\n\nAssicurati di aver configurato il repository e che l'auto-generazione abbia scansionato le tue note."
                            } else {
                                "No deep dives available.\n\nMake sure your repository is configured and auto-generation has scanned your notes."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { feed.size })
                
                // Variabili per tracciare il tempo di permanenza (Dwell Time)
                var activePage by remember { mutableIntStateOf(0) }
                var pageStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
                
                // Mappa per salvare il feedback esplicito temporaneo per ciascun indice di pagina
                val explicitFeedbacks = remember { mutableStateMapOf<Int, Int>() }

                // LOGICA DI TRACCIAMENTO DEL TEMPO DI PERMANENZA (DWELL TIME)
                // Questo effetto scatta ogni volta che la pagina attiva del Pager cambia.
                // Quando l'utente passa ad una nuova card, calcoliamo i millisecondi trascorsi
                // sulla card precedente e inviamo l'interazione al ViewModel.
                LaunchedEffect(pagerState.currentPage) {
                    val previousPage = activePage
                    val duration = System.currentTimeMillis() - pageStartTime
                    
                    if (duration > 500 && previousPage < feed.size) {
                        // Registra l'interazione per la card precedente
                        val prevCard = feed[previousPage]
                        val feedback = explicitFeedbacks[previousPage] ?: 0
                        viewModel.trackInteraction(prevCard, duration, feedback)
                    }
                    
                    // Aggiorna la pagina corrente e resetta il timer di inizio
                    activePage = pagerState.currentPage
                    pageStartTime = System.currentTimeMillis()
                }

                // Tracciamento nel caso in cui lo schermo venga chiuso o distrutto (onDispose)
                DisposableEffect(Unit) {
                    onDispose {
                        val duration = System.currentTimeMillis() - pageStartTime
                        if (duration > 500 && activePage < feed.size) {
                            val card = feed[activePage]
                            val feedback = explicitFeedbacks[activePage] ?: 0
                            viewModel.trackInteraction(card, duration, feedback)
                        }
                    }
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .testTag("deep_dive_pager"),
                    contentPadding = PaddingValues(16.dp),
                    pageSpacing = 16.dp
                ) { page ->
                    val card = feed[page]
                    val feedback = explicitFeedbacks[page] ?: 0

                    DeepDivePageItem(
                        card = card,
                        feedback = feedback,
                        onLikeClicked = {
                            val newFeedback = if (feedback == 1) 0 else 1
                            explicitFeedbacks[page] = newFeedback
                        },
                        onDislikeClicked = {
                            val newFeedback = if (feedback == -1) 0 else -1
                            explicitFeedbacks[page] = newFeedback
                        },
                        lang = lang
                    )
                }
            }
        }
    }
}

@Composable
fun DeepDivePageItem(
    card: DeepDiveCard,
    feedback: Int,
    onLikeClicked: () -> Unit,
    onDislikeClicked: () -> Unit,
    lang: String
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .testTag("deep_dive_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Sfondo con gradiente sottile nell'angolo
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Parte superiore: Intestazione con Topic, Subtopic e tag
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 2.dp
                        ) {
                            val displayTopic = card.topic.trim().uppercase()
                            Text(
                                text = if (displayTopic.isBlank()) "GENERALE" else displayTopic,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        if (card.subtopic.isNotBlank()) {
                            Text(
                                text = card.subtopic,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Sezione dei tag corti
                    if (card.tags.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            card.tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("#$tag", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // Parte centrale: Il Hook d'impatto (bold) e il Body dell'approfondimento
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Hook / Frase di aggancio ad alto impatto
                    Text(
                        text = card.hook,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            lineHeight = 32.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )

                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.width(60.dp).padding(bottom = 16.dp)
                    )

                    // Testo principale (Body)
                    Text(
                        text = card.body,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 26.sp,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Parte inferiore: Feedback utente Like/Dislike e info file sorgente
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Informazioni sul file sorgente
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lang == "it") "Fonte" else "Source",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = card.source_file.substringAfterLast("/"),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    // Pulsanti di Feedback esplicito (+1 Like, -1 Dislike)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bottone Dislike
                        IconButton(
                            onClick = onDislikeClicked,
                            modifier = Modifier.testTag("dislike_button")
                        ) {
                            Icon(
                                imageVector = if (feedback == -1) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                                contentDescription = "Dislike",
                                tint = if (feedback == -1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Bottone Like
                        IconButton(
                            onClick = onLikeClicked,
                            modifier = Modifier.testTag("like_button")
                        ) {
                            Icon(
                                imageVector = if (feedback == 1) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = "Like",
                                tint = if (feedback == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
