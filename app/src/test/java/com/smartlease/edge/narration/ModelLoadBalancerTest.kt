package com.smartlease.edge.narration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelLoadBalancerTest {

    @Test
    fun `abundant memory and standard model selects Go decision with full budget`() {
        val modelFile = createTempFileWithSize(1500L * 1024L * 1024L) // 1.5 GB
        val availableBytes = 6000L * 1024L * 1024L // 6.0 GB RAM

        val decision = ModelLoadBalancer.assess(
            modelFile = modelFile,
            mockAvailableBytes = availableBytes,
            mockThermalStatus = 0,
            mockIsLowMemory = false
        )

        assertTrue(decision is ModelLoadBalancer.LoadDecision.Go)
        assertEquals(ModelLoadBalancer.DEFAULT_MAX_TOKENS, decision.recommendedMaxTokens)
    }

    @Test
    fun `large model such as Gemma 4 E4B balances token budget to 512 tokens`() {
        val modelFile = createTempFileWithSize(3660L * 1024L * 1024L) // 3.66 GB (Gemma 4 E4B)
        val availableBytes = 8400L * 1024L * 1024L // 8.4 GB RAM (iQOO 15 typical free RAM)

        val decision = ModelLoadBalancer.assess(
            modelFile = modelFile,
            mockAvailableBytes = availableBytes,
            mockThermalStatus = 0,
            mockIsLowMemory = false
        )

        assertTrue(decision is ModelLoadBalancer.LoadDecision.Go)
        assertEquals(ModelLoadBalancer.LARGE_MODEL_MAX_TOKENS, decision.recommendedMaxTokens)
    }

    @Test
    fun `constrained memory below recommended headroom scales down to Tight decision`() {
        val modelFile = createTempFileWithSize(2000L * 1024L * 1024L) // 2.0 GB
        // Available is 2.8 GB (more than model + 600MB, but less than model + 1536MB)
        val availableBytes = 2800L * 1024L * 1024L

        val decision = ModelLoadBalancer.assess(
            modelFile = modelFile,
            mockAvailableBytes = availableBytes,
            mockThermalStatus = 0,
            mockIsLowMemory = false
        )

        assertTrue(decision is ModelLoadBalancer.LoadDecision.Tight)
        assertEquals(ModelLoadBalancer.TIGHT_MAX_TOKENS, decision.recommendedMaxTokens)
    }

    @Test
    fun `insufficient memory below minimum working margin results in Skip decision`() {
        val modelFile = createTempFileWithSize(3660L * 1024L * 1024L) // 3.66 GB
        // Available is only 3.8 GB (less than 3.66 GB + 600 MB = 4.26 GB)
        val availableBytes = 3800L * 1024L * 1024L

        val decision = ModelLoadBalancer.assess(
            modelFile = modelFile,
            mockAvailableBytes = availableBytes,
            mockThermalStatus = 0,
            mockIsLowMemory = false
        )

        assertTrue(decision is ModelLoadBalancer.LoadDecision.Skip)
        val skip = decision as ModelLoadBalancer.LoadDecision.Skip
        assertTrue(skip.reason.contains("Insufficient RAM"))
        assertEquals(0, decision.recommendedMaxTokens)
    }

    @Test
    fun `system in low memory state skips loading to protect process`() {
        val modelFile = createTempFileWithSize(1500L * 1024L * 1024L)
        val availableBytes = 5000L * 1024L * 1024L

        val decision = ModelLoadBalancer.assess(
            modelFile = modelFile,
            mockAvailableBytes = availableBytes,
            mockThermalStatus = 0,
            mockIsLowMemory = true
        )

        assertTrue(decision is ModelLoadBalancer.LoadDecision.Skip)
        val skip = decision as ModelLoadBalancer.LoadDecision.Skip
        assertTrue(skip.reason.contains("low-memory state"))
    }

    @Test
    fun `severe thermal status throttles token budget`() {
        val modelFile = createTempFileWithSize(1500L * 1024L * 1024L)
        val availableBytes = 6000L * 1024L * 1024L

        val decision = ModelLoadBalancer.assess(
            modelFile = modelFile,
            mockAvailableBytes = availableBytes,
            mockThermalStatus = 3, // THERMAL_STATUS_SEVERE
            mockIsLowMemory = false
        )

        assertTrue(decision is ModelLoadBalancer.LoadDecision.Tight)
        assertEquals(ModelLoadBalancer.TIGHT_MAX_TOKENS, decision.recommendedMaxTokens)
    }

    @Test
    fun `critical thermal status skips inference completely`() {
        val modelFile = createTempFileWithSize(1500L * 1024L * 1024L)
        val availableBytes = 6000L * 1024L * 1024L

        val decision = ModelLoadBalancer.assess(
            modelFile = modelFile,
            mockAvailableBytes = availableBytes,
            mockThermalStatus = 4, // THERMAL_STATUS_CRITICAL
            mockIsLowMemory = false
        )

        assertTrue(decision is ModelLoadBalancer.LoadDecision.Skip)
        val skip = decision as ModelLoadBalancer.LoadDecision.Skip
        assertTrue(skip.reason.contains("thermal throttling critical"))
    }

    private fun createTempFileWithSize(bytes: Long): File {
        val file = File.createTempFile("mock_model", ".litertlm")
        file.deleteOnExit()
        return object : File(file.absolutePath) {
            override fun length(): Long = bytes
        }
    }
}
