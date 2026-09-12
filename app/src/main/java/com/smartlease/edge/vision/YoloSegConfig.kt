package com.smartlease.edge.vision

/**
 * Contract for the YOLOv8n-Seg model shipped in assets/yolov8n_seg.ptl.
 *
 * The shipping weights are **Candidate A**, retrained on the `unified_v2` rebuild after a
 * content-hash audit showed 81% of the previous model's test set had leaked into its
 * training data. Provenance, benchmarks and the export-parity report are committed under
 * ml/vision/handoff/ — docs/CONTRACTS.md for the verified tensor shapes,
 * benchmarks/eval_A.json for every number quoted in this file.
 *
 * The shape and class-order values below are not tunable preferences — they are fixed by
 * the export (handoff/scripts/p3_export.py) and by unified_v2/data.yaml, which defines the
 * class order. Changing one here without re-exporting produces silently wrong detections.
 */
object YoloSegConfig {

    /** Model input is [1, 3, 640, 640], RGB, float32, normalised to [0, 1]. */
    const val INPUT_SIZE = 640

    /** preds tensor is [1, CHANNELS, ANCHORS] = [1, 40, 8400]. */
    const val ANCHORS = 8400
    const val BOX_CHANNELS = 4
    const val NUM_CLASSES = 4
    const val MASK_COEFFS = 32
    const val PRED_CHANNELS = BOX_CHANNELS + NUM_CLASSES + MASK_COEFFS // 40

    /** protos tensor is [1, 32, 160, 160]. */
    const val PROTO_SIZE = 160

    /** Input pixels per prototype-mask cell: 640 / 160. */
    const val PROTO_STRIDE = INPUT_SIZE / PROTO_SIZE // 4

    /**
     * Class order is positional and comes straight from unified_v2/data.yaml:
     *   0: crack, 1: peeling, 2: spalling, 3: damp_stain
     *
     * Class 3 was rebuilt for this model and is narrower than the old `stain_mould`:
     * corrosion/rust (398 instances) and efflorescence (55) were removed from it because
     * they dragged its AP50 to 0.065. **The shipping model therefore does not detect rust.**
     * Say that rather than letting a rusty grille read as a silent pass.
     */
    val CLASS_NAMES = listOf("crack", "peeling", "spalling", "damp_stain")

    /**
     * Reported to the tenant/landlord, so these read as plain English rather than
     * dataset labels. Index-aligned with CLASS_NAMES.
     */
    val CLASS_DESCRIPTIONS = listOf(
        "crack in wall surface",
        "peeling paint",
        "spalling (surface breaking away)",
        "damp stain or mould growth"
    )

    /** Used when a detection carries a class index the shipping model should not produce. */
    const val UNCLASSIFIED_KEY = "unclassified"
    const val UNCLASSIFIED_DESCRIPTION = "unclassified defect"

    /**
     * The costing key for a class index. This is the ONLY string the rate card may be looked
     * up by — see [classDescription] for why.
     */
    fun classKey(index: Int): String = CLASS_NAMES.getOrElse(index) { UNCLASSIFIED_KEY }

    /**
     * The tenant-facing description for a class index. Display only. Looking a rate up by
     * this string prices nothing and fails silently, which is the bug RepairTariffCoverageTest
     * pins in place.
     */
    fun classDescription(index: Int): String =
        CLASS_DESCRIPTIONS.getOrElse(index) { UNCLASSIFIED_DESCRIPTION }

    /**
     * Below this class score a detection is discarded before NMS.
     *
     * Raised from 0.25 to 0.55 on measurement, not taste. The false-positive sweep over the
     * 14 clean-surface images in the `unified_v2` test split (handoff/benchmarks/eval_A.json,
     * `clean_fp`) reads:
     *
     * ```
     * threshold   clean images flagged   false positives / image
     *   0.25              78.6%                   1.64
     *   0.35              64.3%                   1.21
     *   0.45              50.0%                   0.79
     *   0.55              28.6%                   0.43
     * ```
     *
     * A tenant-facing report that invents a defect is worse than one that misses a faint
     * one: the first loses the argument, the second merely fails to win it. 0.55 is the
     * cheapest available cut in the invented-defect rate.
     *
     * Known limit: 14 background images is too small a sample to pin this number precisely.
     * docs/SHOOT_LIST.md is the 360-image shoot that would let it be set honestly.
     */
    const val SCORE_THRESHOLD = 0.55f

    /** IoU above which the lower-scoring of two same-class boxes is suppressed. */
    const val NMS_IOU_THRESHOLD = 0.45f

    /** A prototype-mask cell counts as defect surface above this sigmoid value. */
    const val MASK_THRESHOLD = 0.5f

    /** Hard cap on detections kept, so a pathological frame can't stall the walkthrough. */
    const val MAX_DETECTIONS = 32
}
