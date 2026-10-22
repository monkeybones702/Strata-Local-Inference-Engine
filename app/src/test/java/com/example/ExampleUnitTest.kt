package com.example

import com.example.model.EngineConfig
import com.example.model.EngineTelemetry
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testDefaultEngineConfig() {
        val config = EngineConfig()
        assertEquals("Qwen3.8-Flash-Next-125B-MoE", config.modelName)
        assertEquals(24576, config.totalExperts)
        assertEquals(10, config.topKActive)
        assertEquals("FP8", config.kvQuant)
    }

    @Test
    fun testDefaultTelemetryBounds() {
        val telemetry = EngineTelemetry()
        assertTrue(telemetry.vramUsedGb <= telemetry.vramTotalGb)
        assertTrue(telemetry.hostRamUsedGb <= telemetry.hostRamTotalGb)
        assertEquals(10, telemetry.activeExperts.size)
    }
}
