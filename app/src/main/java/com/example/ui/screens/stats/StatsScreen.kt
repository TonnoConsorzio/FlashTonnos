package com.example.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.di.AppContainer
import com.example.ui.utils.Loc

@Composable
fun StatsScreen(
    appContainer: AppContainer,
    navController: androidx.navigation.NavHostController? = null,
    viewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.Factory(
            appContainer.flashcardRepository
        )
    )
) {
    val stats by viewModel.stats.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val lang = selectedLanguage

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
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
                        text = Loc.get("stats_header", lang),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }
            }
        }

        if (stats.isNotEmpty() && (stats["total"] as? Int ?: 0) > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(
                        title = Loc.get("stat_accuracy", lang),
                        value = "${stats["accuracy"]}%",
                        icon = Icons.Default.TrendingUp,
                        iconColor = MaterialTheme.colorScheme.primary,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = Loc.get("stat_total_cards", lang),
                        value = "${stats["total"]}",
                        icon = Icons.Default.Style,
                        iconColor = MaterialTheme.colorScheme.secondary,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(
                        title = Loc.get("correct_label", lang),
                        value = "${stats["correct"]}",
                        icon = Icons.Default.CheckCircle,
                        iconColor = Color(0xFF4CAF50),
                        backgroundColor = Color(0xFFE8F5E9).copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = Loc.get("incorrect_label", lang),
                        value = "${stats["incorrect"]}",
                        icon = Icons.Default.Cancel,
                        iconColor = MaterialTheme.colorScheme.error,
                        backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Text(
                    text = Loc.get("streak_records_title", lang),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val dailyStreak = stats["daily_streak"] as? Int ?: 0
                    val maxDaily = stats["max_daily_streak"] as? Int ?: 0
                    val correctStreak = stats["current_correct_streak"] as? Int ?: 0
                    val maxCorrect = stats["max_correct_streak"] as? Int ?: 0

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Loc.get("daily_study_label", lang),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                                Icon(
                                    imageVector = Icons.Default.Whatshot,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$dailyStreak ${if (dailyStreak == 1) Loc.get("day_label", lang) else Loc.get("days_label", lang)}",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Record: $maxDaily ${if (maxDaily == 1) Loc.get("day_label", lang) else Loc.get("days_label", lang)}",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Loc.get("consecutive_correct_label", lang),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$correctStreak ${if (correctStreak == 1) Loc.get("correct_singular", lang) else Loc.get("correct_plural", lang)}",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Record: $maxCorrect",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }
            }
            item {
                val total = (stats["total"] as? Int ?: 0).toFloat()
                val easy = (stats["easy"] as? Int ?: 0).toFloat()
                val medium = (stats["medium"] as? Int ?: 0).toFloat()
                val hard = (stats["hard"] as? Int ?: 0).toFloat()
                
                DifficultyDistributionChart(
                    easy = easy,
                    medium = medium,
                    hard = hard,
                    total = total,
                    lang = lang
                )
            }
        } else {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Style,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = Loc.get("no_stats_available", lang),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = Loc.get("no_stats_desc", lang),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // --- DEEP DIVE STATS SECTION ---
        if (stats.isNotEmpty()) {
            val totalDwellSec = stats["dd_total_dwell_sec"] as? Long ?: 0L
            val likes = stats["dd_positive_count"] as? Int ?: 0
            val dislikes = stats["dd_negative_count"] as? Int ?: 0
            val topicDwell = stats["dd_topic_dwell"] as? Map<String, Long> ?: emptyMap()

            item {
                Text(
                    text = if (lang == "it") "Statistiche Approfondimenti" else "Deep Dive Analytics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dwell Time Card
                    Card(
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (lang == "it") "Tempo di Lettura" else "Reading Time",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = formatDwellTime(totalDwellSec, lang),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Likes/Dislikes Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (lang == "it") "Feedback" else "Feedback",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.ThumbUp, contentDescription = "Likes", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text(text = "$likes", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.ThumbDown, contentDescription = "Dislikes", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Text(text = "$dislikes", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }

            // Dwell Time by Topic/Tag (Interests)
            if (topicDwell.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = if (lang == "it") "Interessi per Argomento" else "Reading Time by Topic",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            val sortedTopics = topicDwell.toList().sortedByDescending { it.second }.take(4)
                            sortedTopics.forEach { (topic, sec) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                        Text(text = topic, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                    }
                                    Text(text = formatDwellTime(sec, lang), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDwellTime(seconds: Long, lang: String): String {
    if (seconds == 0L) return if (lang == "it") "0 sec" else "0s"
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) {
        if (lang == "it") "${mins}m ${secs}s" else "${mins}m ${secs}s"
    } else {
        if (lang == "it") "${secs}s" else "${secs}s"
    }
}

@Composable
fun StatCard(
    title: String, 
    value: String, 
    icon: ImageVector,
    iconColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title, 
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = value, 
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-1).sp
                )
            )
        }
    }
}

@Composable
fun DifficultyDistributionChart(
    easy: Float,
    medium: Float,
    hard: Float,
    total: Float,
    lang: String
) {
    val pEasy = if (total > 0) easy / total else 0f
    val pMedium = if (total > 0) medium / total else 0f
    val pHard = if (total > 0) hard / total else 0f
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = Loc.get("difficulty_distribution", lang),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stacked horizontal bar chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (easy > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(easy)
                            .background(Color(0xFF81C784)), // Soft green
                        contentAlignment = Alignment.Center
                    ) {
                        if (pEasy > 0.12f) {
                            Text(
                                text = "${(pEasy * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold, 
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
                if (medium > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(medium)
                            .background(Color(0xFFFFB74D)), // Soft orange
                        contentAlignment = Alignment.Center
                    ) {
                        if (pMedium > 0.12f) {
                            Text(
                                text = "${(pMedium * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold, 
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
                if (hard > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(hard)
                            .background(Color(0xFFE57373)), // Soft red
                        contentAlignment = Alignment.Center
                    ) {
                        if (pHard > 0.12f) {
                            Text(
                                text = "${(pHard * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold, 
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Custom Legend items with small dots and stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendItem(color = Color(0xFF81C784), label = Loc.get("easy_label", lang), count = easy.toInt(), lang = lang)
                LegendItem(color = Color(0xFFFFB74D), label = Loc.get("medium_label", lang), count = medium.toInt(), lang = lang)
                LegendItem(color = Color(0xFFE57373), label = Loc.get("hard_label", lang), count = hard.toInt(), lang = lang)
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String, count: Int, lang: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = "$count ${if (lang == "it") "card" else if (count == 1) "card" else "cards"}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
