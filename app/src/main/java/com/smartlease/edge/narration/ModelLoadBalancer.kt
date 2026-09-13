package com.smartlease.edge.narration

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.io.File

/**
 * Memory- and thermal-aware load balancer for on-device LLM inference.
 *
 * On-device models (Gemma 3, Gemma 4) range from ~1.5 GB to 3.7+ GB.
 * Weights are memory-mapped, and on top of that the KV cache, working tensors, and
 * the Android OS overhead require substantial RAM headroom. If available RAM is
 * insufficient, Android's Low Memory Killer (LMK) will abruptly kill the app process.
 *
 * This load balancer inspects available RAM and thermal throttle state before attempting
 * to load a model, and returns a [LoadDecision]:
 *  - [LoadDecision.Go]: Safe to load with normal token budget.
 *  - [LoadDecision.Tight]: Memory or thermal pressure detected; safe to load but with
 *    reduced token budget (e.g., 256–384 tokens instead of 640) to constrain the KV cache.
 *  - [LoadDecision.Skip]: High risk of process death (OOM / thermal emergency); falls back
 *    to deterministic rule-based template narration with an honest explanation.
 */
object ModelLoadBalancer {

    private const val TAG = "ModelLoadBalancer"

    /** Default token budget for normal conditions and smaller models. */
    const val DEFAULT_MAX_TOKENS = 640

    /** Balanced token budget for larger models (e.g. >3GB) under normal RAM. */
    const val LARGE_MODEL_MAX_TOKENS = 512

    /** Constrained token budget for tight memory or elevated thermals. */
    const val TIGHT_MAX_TOKENS = 256

    /** Minimum safety headroom (in bytes) required beyond the model file size.
     *  Accounts for KV cache, model runtime allocations, and OS system services. (~1.5 GB) */
    const val HEADROOM_BYTES = 1536L * 1024L * 1024L // 1.5 GB

    /** Critical safety margin (in bytes) below which loading is guaranteed to risk OOM. (~600 MB) */
    const val MINIMUM_WORKING_HEADROOM_BYTES = 600L * 1024L * 1024L // 600 MB

    /** Threshold file size for considering a model "large" (e.g. Gemma 4 E4B is ~3.66 GB). */
    const val LARGE_MODEL_THRESHOLD_BYTES = 3000L * 1024L * 1024L // 3.0 GB

    sealed interface LoadDecision {
        val recommendedMaxTokens: Int

        data class Go(
            override val recommendedMaxTokens: Int,
            val availableMb: Long,
            val requiredMb: Long,
            val note: String? = null
        ) : LoadDecision

        data class Tight(
            override val recommendedMaxTokens: Int,
            val availableMb: Long,
            val requiredMb: Long,
            val warning: String
        ) : LoadDecision

        data class Skip(
            val reason: String,
            val availableMb: Long,
            val requiredMb: Long
        ) : LoadDecision {
            override val recommendedMaxTokens: Int get() = 0
        }
    }

    /**
     * Assesses whether [modelFile] can be loaded safely given the current system resources.
     *
     * @param context Android context to access [ActivityManager] and [PowerManager].
     * @param modelFile the model file to evaluate.
     * @param mockAvailableBytes optional override for testing memory calculation.
     * @param mockThermalStatus optional override for testing thermal throttling.
     * @param mockIsLowMemory optional override for testing system low memory flag.
     */
    fun assess(
        context: Context? = null,
        modelFile: File,
        mockAvailableBytes: Long? = null,
        mockThermalStatus: Int? = null,
        mockIsLowMemory: Boolean? = null
    ): LoadDecision {
        val modelSize = modelFile.length()
        val modelSizeMb = modelSize / (1024 * 1024)

        // 1. Available RAM check
        val availableBytes = mockAvailableBytes ?: (context?.let { getAvailableMemoryBytes(it) } ?: (4L * 1024L * 1024L * 1024L))
        val availableMb = availableBytes / (1024 * 1024)
        val requiredBytes = modelSize + HEADROOM_BYTES
        val requiredMb = requiredBytes / (1024 * 1024)
        val minRequiredBytes = modelSize + MINIMUM_WORKING_HEADROOM_BYTES
        val minRequiredMb = minRequiredBytes / (1024 * 1024)

        // Check if Android ActivityManager reports system-wide low memory
        val isSystemLowMemory = mockIsLowMemory ?: (context?.let { isLowMemoryState(it) } ?: false)

        Log.i(
            TAG,
            "Assessing load for ${modelFile.name} ($modelSizeMb MB): " +
                "available RAM = $availableMb MB, target safe headroom = $requiredMb MB"
        )

        // If system is already in low-memory condition, loading gigabytes of weights is fatal.
        if (isSystemLowMemory) {
            val reason = "Device is in low-memory state ($availableMb MB free); skipping LLM to prevent crash"
            Log.w(TAG, reason)
            return LoadDecision.Skip(reason, availableMb, requiredMb)
        }

        // Severe RAM shortage: available RAM less than model size + minimum working headroom
        if (availableBytes < minRequiredBytes) {
            val reason = "Insufficient RAM ($availableMb MB available, minimum required is $minRequiredMb MB for $modelSizeMb MB model)"
            Log.w(TAG, reason)
            return LoadDecision.Skip(reason, availableMb, requiredMb)
        }

        // 2. Thermal check
        val thermalStatus = mockThermalStatus ?: (context?.let { getThermalStatus(it) } ?: 0)
        val isThermalThrottled = thermalStatus >= 3 // SEVERE (3), CRITICAL (4), EMERGENCY (5), SHUTDOWN (6)
        if (isThermalThrottled) {
            // If device is in critical or emergency thermal state, skip to prevent thermal shutdown
            if (thermalStatus >= 4) {
                val reason = "Device thermal throttling critical (status $thermalStatus); skipping LLM"
                Log.w(TAG, reason)
                return LoadDecision.Skip(reason, availableMb, requiredMb)
            }
        }

        // 3. Load balancing & token budget calculation
        val isLargeModel = modelSize >= LARGE_MODEL_THRESHOLD_BYTES
        val isMemoryTight = availableBytes < requiredBytes

        return when {
            isThermalThrottled -> {
                val warning = "Elevated device temperature (thermal status $thermalStatus); throttled to $TIGHT_MAX_TOKENS tokens"
                Log.w(TAG, warning)
                LoadDecision.Tight(TIGHT_MAX_TOKENS, availableMb, requiredMb, warning)
            }
            isMemoryTight -> {
                val warning = "RAM is constrained ($availableMb MB available vs $requiredMb MB recommended); reduced token budget to $TIGHT_MAX_TOKENS"
                Log.w(TAG, warning)
                LoadDecision.Tight(TIGHT_MAX_TOKENS, availableMb, requiredMb, warning)
            }
            isLargeModel -> {
                // Large model (e.g. Gemma 4 E4B 3.66 GB) has comfortable RAM, but token budget is balanced to 512
                Log.i(TAG, "Large model detected ($modelSizeMb MB); balancing token budget to $LARGE_MODEL_MAX_TOKENS")
                LoadDecision.Go(LARGE_MODEL_MAX_TOKENS, availableMb, requiredMb, "Balanced for large model ($modelSizeMb MB)")
            }
            else -> {
                Log.i(TAG, "Comfortable RAM ($availableMb MB); full token budget $DEFAULT_MAX_TOKENS")
                LoadDecision.Go(DEFAULT_MAX_TOKENS, availableMb, requiredMb)
            }
        }
    }

    /**
     * Reads available memory via ActivityManager, falling back to /proc/meminfo MemAvailable.
     */
    fun getAvailableMemoryBytes(context: Context): Long {
        val amBytes = runCatching {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            memInfo.availMem
        }.getOrNull()

        val procBytes = readProcMemAvailableBytes()

        // Use the more conservative (smaller) available memory reading if both exist
        return when {
            amBytes != null && procBytes != null -> minOf(amBytes, procBytes)
            amBytes != null -> amBytes
            procBytes != null -> procBytes
            else -> 4L * 1024L * 1024L * 1024L // fallback 4 GB assumption if neither works
        }
    }

    fun getTotalMemoryBytes(context: Context): Long {
        return runCatching {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            memInfo.totalMem
        }.getOrNull() ?: readProcMemTotalBytes() ?: (8L * 1024L * 1024L * 1024L)
    }

    private fun isLowMemoryState(context: Context): Boolean {
        return runCatching {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            memInfo.lowMemory
        }.getOrDefault(false)
    }

    private fun readProcMemAvailableBytes(): Long? {
        return runCatching {
            File("/proc/meminfo").bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith("MemAvailable:") }?.let { line ->
                    val parts = line.split("\\s+".toRegex())
                    parts.getOrNull(1)?.toLongOrNull()?.times(1024L)
                }
            }
        }.getOrNull()
    }

    private fun readProcMemTotalBytes(): Long? {
        return runCatching {
            File("/proc/meminfo").bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith("MemTotal:") }?.let { line ->
                    val parts = line.split("\\s+".toRegex())
                    parts.getOrNull(1)?.toLongOrNull()?.times(1024L)
                }
            }
        }.getOrNull()
    }

    private fun getThermalStatus(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return runCatching {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
            }.getOrDefault(0)
        }
        return 0
    }
}
