package com.smartlease.edge.vision

/**
 * Contract for the fine-tuned YOLOv8n-Seg model in ml/YOLOV8.
 *
 * These values are not tunable preferences — they are fixed by how the model was
 * exported (see ml/YOLOV8/convert_yolov8_seg_qnn_pte.py, which pins the same shapes)
 * and by ml/YOLOV8/unified_defects/data.yaml, which defines the class order. Changing
 * one here without re-exporting the model produces silently wrong detections.
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
     * Class order is positional and comes straight from unified_defects/data.yaml:
     *   0: crack, 1: peeling, 2: spalling, 3: stain_mould
     */
    val CLASS_NAMES = listOf("crack", "peeling", "spalling", "stain/mould")

    /**
     * Reported to the tenant/landlord, so these read as plain English rather than
     * dataset labels. Index-aligned with CLASS_NAMES.
     */
    val CLASS_DESCRIPTIONS = listOf(
        "crack in wall surface",
        "peeling paint",
        "spalling (surface breaking away)",
        "staining or mould growth"
    )

    /** Below this class score a detection is discarded before NMS. */
    const val SCORE_THRESHOLD = 0.25f

    /** IoU above which the lower-scoring of two same-class boxes is suppressed. */
    const val NMS_IOU_THRESHOLD = 0.45f

    /** A prototype-mask cell counts as defect surface above this sigmoid value. */
    const val MASK_THRESHOLD = 0.5f

    /** Hard cap on detections kept, so a pathological frame can't stall the walkthrough. */
    const val MAX_DETECTIONS = 32
}
