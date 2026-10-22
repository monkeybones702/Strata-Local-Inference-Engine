package com.example.model

data class EngineTelemetry(
    val vramUsedGb: Float = 14.2f,
    val vramTotalGb: Float = 16.0f,
    val hostRamUsedGb: Float = 44.8f,
    val hostRamTotalGb: Float = 64.0f,
    val nvmeCacheGb: Float = 68.4f,
    val tokensPerSec: Float = 94.6f,
    val timeToFirstTokenMs: Int = 142,
    val pcieBandwidthGbps: Float = 22.8f,
    val kvCacheUsedPercent: Int = 48,
    val kvCompressionRatio: String = "4.2x (FP8)",
    val activeExperts: List<Int> = listOf(142, 591, 1024, 4821, 8912, 12045, 15820, 19412, 22100, 24001),
    val totalExperts: Int = 24576,
    val vramHotCount: Int = 128,
    val hostWarmCount: Int = 2048,
    val nvmeColdCount: Int = 22400
)

data class ChatMessage(
    val id: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val tokensCount: Int = 0,
    val activatedExperts: List<Int> = emptyList(),
    val protocol: String = "OpenAI", // "OpenAI" or "Anthropic"
    val timestamp: Long = System.currentTimeMillis()
)

data class EngineConfig(
    val modelName: String = "Qwen3.8-Flash-Next-125B-MoE",
    val vramBudgetGb: Float = 14.5f,
    val hostRamBudgetGb: Float = 48.0f,
    val totalExperts: Int = 24576,
    val topKActive: Int = 10,
    val hotExpertsVram: Int = 128,
    val warmExpertsRam: Int = 2048,
    val quantFormat: String = "Q4_K_M",
    val kvQuant: String = "FP8",
    val pageSize: Int = 16,
    val prefetchSteps: Int = 2,
    val serverPort: Int = 8000
)

data class DeploymentArtifact(
    val filename: String,
    val category: String,
    val description: String,
    val content: String
)
