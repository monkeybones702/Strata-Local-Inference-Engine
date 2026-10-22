package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EngineTelemetry
import com.example.ui.theme.*

@Composable
fun TelemetryScreen(
    telemetry: EngineTelemetry,
    isInferring: Boolean,
    onTriggerBenchmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 1. Hero Performance Metric Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("telemetry_hero_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(width = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isInferring) StrataEmeraldTertiary else StrataCyanPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isInferring) "GENERATING (MoE ROUTING ACTIVE)" else "ENGINE READY (WARM CACHE)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isInferring) StrataEmeraldTertiary else StrataCyanPrimary,
                                letterSpacing = 1.sp
                            )
                        }
                        Button(
                            onClick = onTriggerBenchmark,
                            modifier = Modifier.testTag("benchmark_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StrataCyanPrimary,
                                contentColor = Color(0xFF003548)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = "Run Benchmark", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stress Test", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricBlock(
                            label = "Throughput",
                            value = "${String.format("%.1f", telemetry.tokensPerSec)}",
                            unit = "T/sec",
                            color = StrataCyanPrimary
                        )
                        MetricBlock(
                            label = "TTFT",
                            value = "${telemetry.timeToFirstTokenMs}",
                            unit = "ms",
                            color = StrataIndigoSecondary
                        )
                        MetricBlock(
                            label = "PCIe Gen4 DMA",
                            value = "${String.format("%.1f", telemetry.pcieBandwidthGbps)}",
                            unit = "GB/s",
                            color = StrataEmeraldTertiary
                        )
                    }
                }
            }
        }

        // 2. 3-Tier Memory Hierarchy Gauges
        item {
            Text(
                text = "3-Tier Memory Hierarchy Allocation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            TierMemoryCard(
                tierLabel = "Tier 1: GPU VRAM (L1 Cache)",
                sublabel = "Fixed Self-Attention + ${telemetry.vramHotCount} Hot Experts",
                usedGb = telemetry.vramUsedGb,
                totalGb = telemetry.vramTotalGb,
                color = StrataVramColor,
                icon = Icons.Default.Memory,
                testTag = "tier1_vram_card"
            )
        }

        item {
            TierMemoryCard(
                tierLabel = "Tier 2: Pinned Host RAM (L2 Cache)",
                sublabel = "${telemetry.hostWarmCount} Warm Experts via Page-Locked DMA",
                usedGb = telemetry.hostRamUsedGb,
                totalGb = telemetry.hostRamTotalGb,
                color = StrataRamColor,
                icon = Icons.Default.Speed,
                testTag = "tier2_ram_card"
            )
        }

        item {
            TierMemoryCard(
                tierLabel = "Tier 3: NVMe SSD (L3 Direct mmap)",
                sublabel = "${telemetry.nvmeColdCount} Cold Experts Mapped (Bypass OS Buffer)",
                usedGb = telemetry.nvmeCacheGb,
                totalGb = 100.0f,
                color = StrataNvmeColor,
                icon = Icons.Default.Storage,
                testTag = "tier3_nvme_card"
            )
        }

        // 3. Live 24,576 Sparse MoE Activation Heatmap
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("moe_heatmap_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sparse MoE Expert Lattice",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "10 of 24,576 Firing",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = StrataCyanPrimary
                        )
                    }

                    Text(
                        text = "Representing active router dispatch across distributed matrix partitions:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 8x8 Grid of representative expert clusters
                    val transition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by transition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "expert_pulse"
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (row in 0 until 6) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (col in 0 until 12) {
                                    val cellIndex = row * 12 + col
                                    val isHot = (cellIndex % 7 == 0)
                                    val isFiring = isInferring && (cellIndex % 5 == 0 || (cellIndex + 2) % 9 == 0)

                                    val cellColor = when {
                                        isFiring -> StrataCyanPrimary.copy(alpha = pulseAlpha)
                                        isHot -> StrataIndigoSecondary.copy(alpha = 0.8f)
                                        cellIndex % 3 == 0 -> StrataEmeraldTertiary.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.surface
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(18.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(cellColor)
                                            .border(
                                                width = 0.5.dp,
                                                color = if (isFiring) StrataCyanPrimary else Color(0x33475569),
                                                shape = RoundedCornerShape(3.dp)
                                            )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        LegendItem(color = StrataCyanPrimary, label = "Active Top-10")
                        LegendItem(color = StrataIndigoSecondary, label = "L1 VRAM Hot")
                        LegendItem(color = StrataEmeraldTertiary, label = "L2 RAM Warm")
                        LegendItem(color = MaterialTheme.colorScheme.surface, label = "L3 NVMe Cold")
                    }
                }
            }
        }

        // 4. KV Cache & Compression
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("kv_cache_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "KV-Cache PagedAttention Allocation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Utilization (${telemetry.kvCacheUsedPercent}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = telemetry.kvCompressionRatio,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = StrataEmeraldTertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { telemetry.kvCacheUsedPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = StrataCyanPrimary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, unit: String, color: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = unit,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}

@Composable
private fun TierMemoryCard(
    tierLabel: String,
    sublabel: String,
    usedGb: Float,
    totalGb: Float,
    color: Color,
    icon: ImageVector,
    testTag: String
) {
    val progress = (usedGb / totalGb).coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = tierLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(text = sublabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    text = "${String.format("%.1f", usedGb)} / ${String.format("%.1f", totalGb)} GB",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
