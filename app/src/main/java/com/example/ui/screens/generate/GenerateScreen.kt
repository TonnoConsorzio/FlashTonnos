package com.example.ui.screens.generate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.di.AppContainer
import com.example.domain.repository.FlashcardRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(
    appContainer: AppContainer,
    navController: androidx.navigation.NavHostController? = null,
    viewModel: GenerateViewModel = viewModel(
        factory = GenerateViewModel.Factory(
            appContainer.flashcardRepository
        )
    )
) {
    val isGenerating by viewModel.isGenerating.collectAsState()
    val generationResult by viewModel.generationResult.collectAsState()
    val liveProgress by viewModel.liveProgress.collectAsState()
    
    var amount by remember { mutableFloatStateOf(5f) }
    var selectedType by remember { mutableStateOf("entrambi") }
    var showMassiveConfirmDialog by remember { mutableStateOf(false) }
    val types = listOf("true_false", "multiple_choice", "entrambi")
    val typeLabels = listOf("V/F", "Multipla", "Entrambi")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            if (navController != null) {
                IconButton(
                    onClick = { navController.navigateUp() },
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Indietro",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = "Genera nuove flashcard",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )
            )
        }

        Text("Tipo di card", modifier = Modifier.align(Alignment.Start))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            types.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = type == selectedType,
                    onClick = { selectedType = type },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size)
                ) {
                    Text(typeLabels[index])
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Numero di card: ${amount.toInt()}", modifier = Modifier.align(Alignment.Start))
        Slider(
            value = amount,
            onValueChange = { amount = it },
            valueRange = 1f..20f,
            steps = 19,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "File sorgente Markdown (.md) nel repository",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.align(Alignment.Start).padding(top = 16.dp, bottom = 8.dp)
        )

        val markdownFiles by viewModel.markdownFiles.collectAsState()
        val selectedFile by viewModel.selectedFile.collectAsState()
        val isScanning by viewModel.isScanning.collectAsState()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "File rilevati: ${markdownFiles.size}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    IconButton(
                        onClick = { viewModel.scanRepository() },
                        enabled = !isScanning && !isGenerating
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Riscansiona repository",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (markdownFiles.isEmpty() && !isScanning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nessun file .md rilevato nel repository. Assicurati che le credenziali GitHub siano corrette e che vi siano file Markdown in 'Appunti', 'appunti' o nella root del repository.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (markdownFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Seleziona il file da utilizzare per la generazione:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        markdownFiles.forEach { file ->
                            val isSelected = file == selectedFile
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.selectFile(file) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.selectFile(file) },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = file,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isGenerating) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Generazione in corso via AI...",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = liveProgress,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.generateCards(amount.toInt(), selectedType) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Genera da File")
                }
                
                FilledTonalButton(
                    onClick = { showMassiveConfirmDialog = true },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Inizializza Tutto")
                }
            }
        }

        generationResult?.let { result ->
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    if (showMassiveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showMassiveConfirmDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        showMassiveConfirmDialog = false
                        viewModel.generateAllCardsMassively(selectedType)
                    }
                ) {
                    Text("Sì, Inizializza")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMassiveConfirmDialog = false }) {
                    Text("Annulla")
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Inizializzazione Massiva", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Sei sicuro di voler avviare l'inizializzazione automatica? " +
                    "Questo processo scansionerà le tue cartelle note su GitHub e genererà " +
                    "esattamente 5 flashcard per ciascun file .md rilevato tramite AI.\n\n" +
                    "L'operazione potrebbe richiedere alcuni minuti a seconda del numero di file."
                )
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}
