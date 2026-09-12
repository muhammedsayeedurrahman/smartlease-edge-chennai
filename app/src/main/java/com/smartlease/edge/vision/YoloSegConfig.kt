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
     * **0.45, set from the measured exchange rate rather than from either side of it alone.**
     *
     * A threshold buys specificity by spending recall, so it cannot be chosen from a
     * false-positive table alone. Specificity is measured on 63 held-out backgrounds
     * (`handoff/runs/t0_1_clean_fp_63.json`); recall on the 268-image test split. Both for
     * the shipping weights:
     *
     * ```
     * conf   clean images left alone   FP / clean image   mask recall   mask precision
     * 0.25            19.0%                 3.79             0.247          0.421
     * 0.45            39.7%                 1.87             0.240          0.442   <- here
     * 0.55            47.6%                 1.41             0.223          0.516
     * 0.65            52.4%                 1.03             0.182          0.573
     * 0.80            74.6%                 0.27             0.081          0.649
     * ```
     *
     * Read the 0.25 -> 0.45 row transition: specificity doubles and false positives per clean
     * image halve, for **0.7 of a percentage point** of recall. Almost no true detections
     * score in that band and a great many false ones do. Past 0.55 the trade inverts, and by
     * 0.80 recall is 0.081 — an inspection tool that finds one defect in twelve, which is not
     * a tool. 0.45 is the knee.
     *
     * Two honest limits. **First**, all 63 negatives are drone photographs of building
     * exteriors, and this app is pointed at the inside of a rented flat; the shape of the
     * curve should transfer but the magnitudes may not. `docs/guides/SHOOT_LIST.md` is the
     * indoor shoot that would settle it, and this number should be re-derived once it exists.
     * **Second**, an earlier revision set 0.55 from the 14-image `clean_fp` table in
     * eval_A.json — too small a sample to carry the claim. The 63-image measurement and the
     * recall sweep above supersede it.
     *
     * Nothing here removes the downstream mitigation: DeductionEngine still refuses to price
     * below `PRICING_CONFIDENCE_FLOOR`, so a detection scoring between this cut and that floor
     * reaches the tenant as "listed for human review, not priced" rather than as a charge.
     */
    const val SCORE_THRESHOLD = 0.45f

    /** IoU above which the lower-scoring of two same-class boxes is suppressed. */
    const val NMS_IOU_THRESHOLD = 0.45f

    /** A prototype-mask cell counts as defect surface above this sigmoid value. */
    const val MASK_THRESHOLD = 0.5f

    /** Hard cap on detections kept, so a pathological frame can't stall the walkthrough. */
    const val MAX_DETECTIONS = 32
}
