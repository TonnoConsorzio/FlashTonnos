package com.example.ui.screens.deepdive

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.di.AppContainer
import com.example.domain.models.DeepDiveCard

/**
 * Generates a modern gradient background depending on the topic name hash.
 */
@Composable
fun getTopicGradient(topic: String, isDark: Boolean): Brush {
    val cleanTopic = topic.trim().lowercase()
    val hash = Math.abs(cleanTopic.hashCode())
    
    val colorPairs = if (isDark) {
        listOf(
            Pair(Color(0xFF0F172A), Color(0xFF1E293B)), // Slate 900 to Slate 800
            Pair(Color(0xFF09201A), Color(0xFF114D3E)), // Emerald/Teal Dark
            Pair(Color(0xFF1E0E25), Color(0xFF4C1D5C)), // Royal Violet Dark
            Pair(Color(0xFF1F1206), Color(0xFF432004)), // Chocolate/Amber Dark
            Pair(Color(0xFF081C2E), Color(0xFF133F65)), // Sapphire Blue Dark
            Pair(Color(0xFF171717), Color(0xFF2D2D2D))  // Dark Gray
        )
    } else {
        listOf(
            Pair(Color(0xFFEEF2F6), Color(0xFFD0D7DE)), // Soft Pearl/Silver
            Pair(Color(0xFFE6F4EA), Color(0xFFA8DAB5)), // Soft Mint Green
            Pair(Color(0xFFFCE8E6), Color(0xFFF1B0B7)), // Soft Rose Pink
            Pair(Color(0xFFFEF3C7), Color(0xFFFDE68A)), // Soft Warm Apricot
            Pair(Color(0xFFE0F2FE), Color(0xFF7DD3FC)), // Soft Sky Blue
            Pair(Color(0xFFF5F5F5), Color(0xFFE5E5E5))  // Warm Light Gray
        )
    }
    
    val pair = colorPairs[hash % colorPairs.size]
    return Brush.verticalGradient(
        colors = listOf(pair.first, pair.second)
    )
}

/**
 * Immersive full-screen TikTok-style study deck for Deep Dives.
 */
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
    val darkTheme = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (feed.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
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

            // Fixed back button for empty state
            IconButton(
                onClick = { navController?.navigateUp() },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, bottom = 24.dp)
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .testTag("back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (lang == "it") "Indietro" else "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { feed.size })
            
            // Dwell time tracking variables
            var activePage by remember { mutableIntStateOf(0) }
            var pageStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
            val explicitFeedbacks = remember { mutableStateMapOf<Int, Int>() }

            LaunchedEffect(pagerState.currentPage) {
                val previousPage = activePage
                val duration = System.currentTimeMillis() - pageStartTime
                
                if (duration > 500 && previousPage < feed.size) {
                    val prevCard = feed[previousPage]
                    val feedback = explicitFeedbacks[previousPage] ?: 0
                    viewModel.trackInteraction(prevCard, duration, feedback)
                }
                
                activePage = pagerState.currentPage
                pageStartTime = System.currentTimeMillis()
            }

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
                    .testTag("deep_dive_pager"),
                contentPadding = PaddingValues(0.dp),
                pageSpacing = 0.dp
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
                    onBackClicked = { navController?.navigateUp() },
                    lang = lang,
                    isDark = darkTheme
                )
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
    onBackClicked: () -> Unit,
    lang: String,
    isDark: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(getTopicGradient(card.topic, isDark))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("deep_dive_card")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Upper Area: Only a clean topic identifier badge, no tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(100),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    tonalElevation = 1.dp
                ) {
                    val displayTopic = card.topic.trim().uppercase()
                    Text(
                        text = if (displayTopic.isBlank()) "GENERALE" else displayTopic,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (card.subtopic.isNotBlank()) {
                    Text(
                        text = card.subtopic.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            // Central Area: Big impact hook/title and short readable body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hook / Large Impact Title
                Text(
                    text = card.hook,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 30.sp,
                        lineHeight = 38.sp,
                        textAlign = TextAlign.Center,
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )

                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(2.dp)
                        )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Body text styled with modern clarity, formatted markdown, and generous line height
                com.example.ui.screens.study.FormattedMarkdownText(
                    text = card.body,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Start
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Bottom Area: Immersive bottom navigation (Back, Source info, Like/Dislike)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Back button and source file info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBackClicked,
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(26.dp)
                            )
                            .testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (lang == "it") "Indietro" else "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                          text = if (lang == "it") "Fonte" else "Source",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                          text = card.source_file.substringAfterLast("/"),
                          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                          color = MaterialTheme.colorScheme.onSurface,
                          maxLines = 1
                        )
                    }
                }

                // Right: Like/Dislike feedback buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dislike Button
                    IconButton(
                        onClick = onDislikeClicked,
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = if (feedback == -1) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(26.dp)
                            )
                            .testTag("dislike_button")
                    ) {
                        Icon(
                            imageVector = if (feedback == -1) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Dislike",
                            tint = if (feedback == -1) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Like Button
                    IconButton(
                        onClick = onLikeClicked,
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = if (feedback == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(26.dp)
                            )
                            .testTag("like_button")
                    ) {
                        Icon(
                            imageVector = if (feedback == 1) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = if (feedback == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
