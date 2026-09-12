package com.smartlease.edge.ir

/**
 * Candidate Fujitsu_AC "short frame" (power toggle) encoder — ported from the open-source
 * IRremoteESP8266 library (github.com/crankyoldgit/IRremoteESP8266, src/ir_Fujitsu.h/.cpp,
 * fetched 2026-09-12). This is real published protocol logic, not invented, but it is
 * NOT independently verified against the venue unit.
 *
 * Context: the venue AC remote photographed on-site reads model "YY-107A"; a third-party
 * reseller listing (not the manufacturer) advertises it as O General-compatible. O General
 * ACs commonly use this protocol family — the library itself lists O General's own AR-RCL1E
 * remote as an ARRAH2E-variant match, which is the variant this targets. Two things remain
 * unconfirmed: (1) whether this remote/unit genuinely speaks Fujitsu_AC at all, and (2) that
 * the ARRAH2E short-frame checksum rule (last byte = bitwise NOT of the command byte) is the
 * right one for this specific unit rather than one of the library's other model variants.
 *
 * The short frame only toggles power using whatever mode/temperature/fan the AC last had —
 * it does not set a full state. Frame structure (7 bytes, from checkSum()/off() in the
 * source): [0x14, 0x63, 0x00, 0x10, 0x10, cmd, ~cmd].
 *
 * Do not present a transmit from this as a confirmed working command until it has actually
 * been tested against the real unit — see docs/EXECUTION_PLAN.md's IR item.
 */
object FujitsuAcCandidate {
    private const val CMD_TURN_ON = 0x01
    private const val CMD_TURN_OFF = 0x02
    private val HEADER = intArrayOf(0x14, 0x63, 0x00, 0x10, 0x10)

    /** The 7-byte short (toggle) frame for the given power command. */
    fun shortFrame(turnOn: Boolean): IntArray {
        val cmd = if (turnOn) CMD_TURN_ON else CMD_TURN_OFF
        val inverted = cmd.inv() and 0xFF
        return HEADER + intArrayOf(cmd, inverted)
    }

    /**
     * Converts a byte frame into the mark/space microsecond pattern that
     * [ConsumerIrManager.transmit] expects, using [FujitsuAcCandidateTiming]. Bit order is
     * LSB-first per byte, matching the library's own sendGeneric() call for this protocol
     * (its MSBfirst argument is `false`).
     */
    fun toRawPattern(bytes: IntArray): IntArray {
        val pattern = mutableListOf<Int>()
        pattern += FujitsuAcCandidateTiming.HDR_MARK_US
        pattern += FujitsuAcCandidateTiming.HDR_SPACE_US
        for (byte in bytes) {
            for (bit in 0 until 8) {
                val isOne = (byte shr bit) and 1 == 1
                pattern += FujitsuAcCandidateTiming.BIT_MARK_US
                pattern += if (isOne) {
                    FujitsuAcCandidateTiming.ONE_SPACE_US
                } else {
                    FujitsuAcCandidateTiming.ZERO_SPACE_US
                }
            }
        }
        pattern += FujitsuAcCandidateTiming.BIT_MARK_US
        pattern += FujitsuAcCandidateTiming.MIN_GAP_US
        return pattern.toIntArray()
    }
}
