package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatMessage
import com.example.ui.theme.*

@Composable
fun PlaygroundScreen(
    messages: List<ChatMessage>,
    selectedProtocol: String, // "OpenAI" or "Anthropic"
    onProtocolChanged: (String) -> Unit,
    onSendMessage: (String, Boolean) -> Unit,
    isInferring: Boolean,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var attachVisionImage by remember { mutableStateOf(false) }
    var showInspectorDialog by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 1. Dual-Protocol Toggle Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "API Gateway Playground",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Localhost Endpoint: ${if (selectedProtocol == "OpenAI") "/v1/chat/completions" else "/v1/messages"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.testTag("protocol_selector")) {
                SegmentedButton(
                    selected = selectedProtocol == "OpenAI",
                    onClick = { onProtocolChanged("OpenAI") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("OpenAI", fontSize = 12.sp)
                }
                SegmentedButton(
                    selected = selectedProtocol == "Anthropic",
                    onClick = { onProtocolChanged("Anthropic") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Anthropic", fontSize = 12.sp)
                }
            }
        }

        // 2. Chat Conversation History
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyPlaygroundPrompt(
                        onSelectSample = { samplePrompt ->
                            inputText = samplePrompt
                        }
                    )
                }
            }

            items(messages) { msg ->
                ChatBubble(
                    message = msg,
                    onInspect = { showInspectorDialog = msg }
                )
            }
        }

        // 3. Vision Attachment Badge
        if (attachVisionImage) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = StrataCyanPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Vision Payload: base64 (diagram_arch.png) attached",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = StrataCyanPrimary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { attachVisionImage = false },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Remove", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // 4. Input Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { attachVisionImage = !attachVisionImage },
                modifier = Modifier
                    .testTag("attach_vision_button")
                    .size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (attachVisionImage) StrataCyanPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = "Attach Vision Image",
                    tint = if (attachVisionImage) StrataCyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                placeholder = { Text("Ask Qwen MoE or send query...", fontSize = 13.sp) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StrataCyanPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotBlank() && !isInferring) {
                        val text = inputText
                        val vision = attachVisionImage
                        inputText = ""
                        attachVisionImage = false
                        onSendMessage(text, vision)
                    }
                },
                enabled = inputText.isNotBlank() && !isInferring,
                modifier = Modifier
                    .testTag("send_button")
                    .size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = StrataCyanPrimary,
                    contentColor = Color(0xFF003548),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                if (isInferring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = StrataCyanPrimary
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    // Modal Sheet: Raw JSON & SSE Protocol Inspector
    showInspectorDialog?.let { inspectedMsg ->
        AlertDialog(
            onDismissRequest = { showInspectorDialog = null },
            title = {
                Text(
                    text = "Raw Protocol Inspector (${inspectedMsg.protocol})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Active Experts Fired (Top-10):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = inspectedMsg.activatedExperts.joinToString(", ") { "#$it" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = StrataEmeraldTertiary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Wire SSE Frame Stream:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        val sampleWire = if (inspectedMsg.protocol == "OpenAI") {
                            """data: {"id":"chatcmpl-strata","object":"chat.completion.chunk","choices":[{"delta":{"content":"${inspectedMsg.content.take(20)}..."}}]}"""
                        } else {
                            """event: content_block_delta\ndata: {"type":"content_block_delta","delta":{"type":"text_delta","text":"${inspectedMsg.content.take(20)}..."}}"""
                        }
                        Text(
                            text = sampleWire,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp),
                            color = StrataCyanPrimary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInspectorDialog = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, onInspect: () -> Unit) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) StrataCyanPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
            border = if (isUser) CardDefaults.outlinedCardBorder().copy(width = 1.dp) else null,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isUser) "Client Request" else "Strata Engine",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) StrataCyanPrimary else StrataEmeraldTertiary
                    )

                    if (!isUser) {
                        IconButton(
                            onClick = onInspect,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = "Inspect Frame", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!isUser && message.activatedExperts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "MoE Routed: 10/24,576 experts [${message.activatedExperts.take(4).joinToString(", ")}...]",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = StrataTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaygroundPrompt(onSelectSample: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Psychology,
                contentDescription = null,
                tint = StrataCyanPrimary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Dual API Gateway Live Test",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Queries test local dynamic expert activation and stream responses via standard OpenAI or Anthropic SDK formats.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            val samplePrompts = listOf(
                "Explain how Strata offloads 24,576 experts across NVMe and VRAM.",
                "Compare OpenAI SSE chunk format with Anthropic message_delta.",
                "How does FP8 PagedAttention prevent high-context OOM?"
            )

            samplePrompts.forEach { sample ->
                OutlinedButton(
                    onClick = { onSelectSample(sample) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(sample, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}
