package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EngineConfig
import com.example.ui.theme.StrataCyanPrimary
import com.example.ui.theme.StrataEmeraldTertiary

@Composable
fun ConfigScreen(
    currentConfig: EngineConfig,
    onSaveConfig: (EngineConfig) -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    var vramBudget by remember(currentConfig) { mutableFloatStateOf(currentConfig.vramBudgetGb) }
    var ramBudget by remember(currentConfig) { mutableFloatStateOf(currentConfig.hostRamBudgetGb) }
    var topK by remember(currentConfig) { mutableIntStateOf(currentConfig.topKActive) }
    var selectedQuant by remember(currentConfig) { mutableStateOf(currentConfig.quantFormat) }
    var selectedKvQuant by remember(currentConfig) { mutableStateOf(currentConfig.kvQuant) }
    var prefetchSteps by remember(currentConfig) { mutableIntStateOf(currentConfig.prefetchSteps) }

    var showSavedMessage by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                text = "Engine & Memory Allocator Config",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Controls dynamic layer allocation across VRAM, Host RAM, and NVMe mmap backing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Model & Target Quantization
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_config_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Target Model Specification", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Target: ${currentConfig.modelName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = StrataCyanPrimary
                    )
                    Text(
                        text = "Topology: 24,576 fine-grained MoE experts (48 layers)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(text = "Weight Quantization Target", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Q4_K_M", "FP8_E4M3", "INT4", "EXL2").forEach { quant ->
                            FilterChip(
                                selected = selectedQuant == quant,
                                onClick = { selectedQuant = quant },
                                label = { Text(quant, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // 2. Hardware Resource Sliders
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hardware_budgets_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Hardware Resource Ceilings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(12.dp))

                    // VRAM Budget Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "GPU VRAM Budget (L1)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${String.format("%.1f", vramBudget)} GB",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = StrataCyanPrimary
                        )
                    }
                    Slider(
                        value = vramBudget,
                        onValueChange = { vramBudget = it },
                        valueRange = 8f..24f,
                        steps = 15,
                        modifier = Modifier.testTag("vram_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Host RAM Budget Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Pinned Host RAM Budget (L2)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${String.format("%.0f", ramBudget)} GB",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = StrataCyanPrimary
                        )
                    }
                    Slider(
                        value = ramBudget,
                        onValueChange = { ramBudget = it },
                        valueRange = 16f..128f,
                        steps = 13,
                        modifier = Modifier.testTag("ram_slider")
                    )
                }
            }
        }

        // 3. Routing & KV Cache Settings
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("routing_config_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "MoE Router & Cache Parameters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Active Experts per Token (Top-K)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "$topK experts",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = StrataEmeraldTertiary
                        )
                    }
                    Slider(
                        value = topK.toFloat(),
                        onValueChange = { topK = it.toInt() },
                        valueRange = 4f..16f,
                        steps = 11,
                        modifier = Modifier.testTag("topk_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "KV-Cache Quantization", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("FP8", "INT4", "FP16").forEach { kvQ ->
                            FilterChip(
                                selected = selectedKvQuant == kvQ,
                                onClick = { selectedKvQuant = kvQ },
                                label = { Text(kvQ, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // 4. Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onResetDefaults,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reset_config_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Defaults")
                }

                Button(
                    onClick = {
                        val newConfig = currentConfig.copy(
                            vramBudgetGb = vramBudget,
                            hostRamBudgetGb = ramBudget,
                            topKActive = topK,
                            quantFormat = selectedQuant,
                            kvQuant = selectedKvQuant,
                            prefetchSteps = prefetchSteps
                        )
                        onSaveConfig(newConfig)
                        showSavedMessage = true
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("save_config_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StrataCyanPrimary,
                        contentColor = MaterialTheme.colorScheme.background
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply & Sync YAML")
                }
            }

            if (showSavedMessage) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✓ Configuration synchronized with config.yaml and offloader",
                    style = MaterialTheme.typography.bodySmall,
                    color = StrataEmeraldTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
