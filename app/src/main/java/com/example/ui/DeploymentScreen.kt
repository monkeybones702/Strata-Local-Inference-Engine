package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DeploymentArtifact
import com.example.ui.theme.StrataCyanPrimary
import com.example.ui.theme.StrataEmeraldTertiary

@Composable
fun DeploymentScreen(
    artifacts: List<DeploymentArtifact>,
    modifier: Modifier = Modifier
) {
    var selectedFileIndex by remember { mutableIntStateOf(0) }
    val currentArtifact = artifacts.getOrNull(selectedFileIndex) ?: artifacts.first()
    val context = LocalContext.current
    var copyStatusMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                text = "Standalone Deployment Files",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Artifacts required to execute the Strata local inference engine outside this cloud sandbox on a host PC or GPU server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Quick Execution Guide Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("host_quickstart_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = StrataCyanPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Host 1-Click Launch Instructions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Linux: chmod +x run.sh && ./run.sh\n• Windows: double-click run.bat\n• Docker GPU: docker compose up --build",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = StrataEmeraldTertiary
                    )
                }
            }
        }

        // 2. Horizontal File Selector Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(artifacts.indices.toList()) { index ->
                    val file = artifacts[index]
                    FilterChip(
                        selected = selectedFileIndex == index,
                        onClick = { selectedFileIndex = index },
                        label = {
                            Text(
                                text = file.filename,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.testTag("file_tab_${file.filename}")
                    )
                }
            }
        }

        // 3. Active File Detail & Code Viewer
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("code_viewer_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentArtifact.filename,
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = StrataCyanPrimary
                            )
                            Text(
                                text = currentArtifact.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(currentArtifact.filename, currentArtifact.content)
                                clipboard.setPrimaryClip(clip)
                                copyStatusMessage = "Copied ${currentArtifact.filename} to clipboard!"
                            },
                            modifier = Modifier.testTag("copy_file_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StrataCyanPrimary,
                                contentColor = MaterialTheme.colorScheme.background
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    copyStatusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = StrataEmeraldTertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Code Viewer Box with horizontal & vertical scroll
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp, max = 460.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        val hScroll = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(hScroll)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = currentArtifact.content,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
