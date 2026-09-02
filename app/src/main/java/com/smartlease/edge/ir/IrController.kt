package com.smartlease.edge.ir

import android.content.Context
import android.hardware.ConsumerIrManager

/**
 * IR blaster wrapper for capturing and re-transmitting an AC unit's real raw timing pattern.
 *
 * Per the design doc's critical tech fix: AC remotes transmit the ENTIRE device state
 * (power + mode + temp + fan speed + vane + checksum) in one burst, often 100+ bits — not
 * simple toggle codes like a TV. There is no public Android API to receive/decode an
 * arbitrary IR signal (ConsumerIrManager is transmit-only) — real capture requires an
 * external IR receiver/USB dongle on-site, per the 12-day plan's Day 12 checklist. This
 * class handles the transmit half, which IS fully doable with the public API, and exposes
 * a clear injection point for the captured pattern.
 *
 * iQOO 15's IR blaster hardware is independently verified real (GSMArena/Croma/iQOO's own
 * product page) — this class checks for it at runtime rather than assuming it's present,
 * since the manifest declares the consumerir feature as optional so the app still installs
 * on devices without it.
 */
class IrController(context: Context) {

    private val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val hasIrBlaster: Boolean
        get() = irManager?.hasIrEmitter() == true

    /**
     * Transmit a previously captured raw pattern.
     * @param carrierFrequencyHz typically 38000 for most AC remotes, but varies by brand —
     *        the Day 12 on-site capture must record this alongside the pattern itself.
     * @param pattern alternating on/off durations in microseconds, as captured from the
     *        real demo unit — see the fallback library below for common-brand placeholders.
     */
    fun transmit(carrierFrequencyHz: Int, pattern: IntArray): TransmitResult {
        val manager = irManager
            ?: return TransmitResult.Failure("No ConsumerIrManager available on this device")
        if (!manager.hasIrEmitter()) {
            return TransmitResult.Failure("Device reports no IR emitter hardware")
        }
        return try {
            manager.transmit(carrierFrequencyHz, pattern)
            TransmitResult.Success
        } catch (e: Exception) {
            TransmitResult.Failure(e.message ?: "Unknown IR transmit failure")
        }
    }

    sealed class TransmitResult {
        object Success : TransmitResult()
        data class Failure(val reason: String) : TransmitResult()
    }
}

/**
 * Fallback library of PLACEHOLDER carrier frequencies for common Chennai AC brands
 * (per the 12-day plan, Days 9-10). These are typical values for the brand's remote
 * family, NOT verified codes for a specific unit — the real, demo-critical step is still
 * capturing the actual on-stage unit's pattern live on Day 12. Do not treat these ints
 * as real transmittable patterns; only carrierFrequencyHz is populated here on purpose.
 */
object CommonAcIrProfiles {
    data class Profile(val brand: String, val typicalCarrierHz: Int, val notes: String)

    val profiles = listOf(
        Profile("Voltas", 38000, "NEC-family remotes typical; verify on-site"),
        Profile("Blue Star", 38000, "NEC-family remotes typical; verify on-site"),
        Profile("LG", 38000, "Often full-state burst protocol; verify on-site"),
        Profile("Daikin", 38000, "Often full-state burst protocol; verify on-site"),
        Profile("Hitachi", 38000, "Verify on-site — protocol varies by model year")
    )
}
