package com.smartlease.edge.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream

/**
 * DefectSegmenter backed by the team's fine-tuned YOLOv8n-Seg model (ml/YOLOV8).
 *
 * Unlike [HeuristicDefectSegmenter] this reports `isTrainedModel = true`, so the UI stops
 * showing the "heuristic mode" badge — which means the honesty burden moves here: if the
 * model cannot be loaded we return null from [create] rather than silently degrading into
 * something that looks trained but isn't. The caller decides what to fall back to.
 */
class YoloSegDefectSegmenter private constructor(
    private val module: Module
) : DefectSegmenter {

    override val isTrainedModel = true

    override fun segmentDefects(
        bitmap: Bitmap,
        frameWidthInches: Float,
        frameHeightInches: Float
    ): List<DefectSegmenter.Defect> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()

        val letterbox = Letterbox.forSource(bitmap.width, bitmap.height)
        val input = toInputTensor(bitmap, letterbox)

        val detections = try {
            val output = module.forward(IValue.from(input))
            val (preds, protos) = readOutputs(output)
            YoloSegDecoder.decode(
                preds = preds.dataAsFloatArray,
                protos = protos.dataAsFloatArray,
                letterbox = letterbox
            )
        } catch (e: Exception) {
            // A failed inference must not take the walkthrough down mid-inspection.
            Log.e(TAG, "YOLOv8-Seg inference failed: ${e.message}", e)
            return emptyList()
        }

        val frameSqFt = (frameWidthInches / 12f) * (frameHeightInches / 12f)

        return detections.map { d ->
            val name = YoloSegConfig.CLASS_DESCRIPTIONS.getOrElse(d.classIndex) { "unclassified defect" }
            DefectSegmenter.Defect(
                boundingBox = RectF(d.box.left, d.box.top, d.box.right, d.box.bottom),
                label = name,
                areaSqFtEstimate = d.coverageFraction * frameSqFt,
                confidence = d.score
            )
        }
    }

    /** The export returns (preds, protos); tolerate a single-tensor build rather than crashing. */
    private fun readOutputs(output: IValue): Pair<Tensor, Tensor> {
        if (!output.isTuple) {
            error("Expected a (preds, protos) tuple from the segmentation model")
        }
        val parts = output.toTuple()
        require(parts.size >= 2) { "Model returned ${parts.size} outputs, expected 2" }
        return parts[0].toTensor() to parts[1].toTensor()
    }

    /**
     * Bitmap -> [1, 3, 640, 640] float32 in [0, 1], letterboxed and CHW-ordered.
     * Padding is mid-grey (0.447) to match the Ultralytics training-time letterbox fill.
     */
    private fun toInputTensor(bitmap: Bitmap, letterbox: Letterbox): Tensor {
        val size = YoloSegConfig.INPUT_SIZE
        val plane = size * size
        val data = FloatArray(3 * plane) { PAD_VALUE }

        val scaled = Bitmap.createScaledBitmap(
            bitmap, letterbox.contentWidth, letterbox.contentHeight, true
        )
        val pixels = IntArray(letterbox.contentWidth * letterbox.contentHeight)
        scaled.getPixels(
            pixels, 0, letterbox.contentWidth, 0, 0,
            letterbox.contentWidth, letterbox.contentHeight
        )

        for (y in 0 until letterbox.contentHeight) {
            val dstRow = (y + letterbox.padY) * size
            val srcRow = y * letterbox.contentWidth
            for (x in 0 until letterbox.contentWidth) {
                val px = pixels[srcRow + x]
                val dst = dstRow + x + letterbox.padX
                data[dst] = ((px shr 16) and 0xFF) / 255f              // R
                data[plane + dst] = ((px shr 8) and 0xFF) / 255f       // G
                data[2 * plane + dst] = (px and 0xFF) / 255f           // B
            }
        }
        if (scaled != bitmap) scaled.recycle()

        return Tensor.fromBlob(data, longArrayOf(1, 3, size.toLong(), size.toLong()))
    }

    companion object {
        private const val TAG = "YoloSegSegmenter"
        private const val PAD_VALUE = 114f / 255f

        /**
         * Asset filenames tried in order; the first that loads AND satisfies the export
         * contract wins. `vision_best.ptl` used to sit in this list and is now deleted from
         * assets: it is a 5-class DETECTION export (`DetectionModel.forward -> Tensor`, no
         * proto branch), not a segmentation model, and it caused the exact silent failure
         * [returnsSegmentationTuple] now prevents.
         */
        private val CANDIDATE_ASSETS = listOf("yolov8n_seg.ptl")

        /**
         * @return a model-backed segmenter, or null when no usable model ships in assets —
         *         never a stand-in that pretends to be trained.
         */
        fun create(context: Context): YoloSegDefectSegmenter? {
            for (asset in CANDIDATE_ASSETS) {
                val path = copyAssetIfPresent(context, asset) ?: continue
                val module = try {
                    LiteModuleLoader.load(path)
                } catch (e: Exception) {
                    Log.w(TAG, "Asset '$asset' present but failed to load: ${e.message}")
                    continue
                }
                if (!returnsSegmentationTuple(module)) {
                    Log.w(TAG, "Asset '$asset' loaded but is not a (preds, protos) export; skipping")
                    continue
                }
                Log.i(TAG, "Loaded segmentation model from asset '$asset'")
                return YoloSegDefectSegmenter(module)
            }
            Log.w(TAG, "No usable segmentation model in assets; caller should fall back")
            return null
        }

        /**
         * One probe forward pass against the export contract, at load time.
         *
         * A detection-only export loads perfectly and then returns a single tensor, which
         * [readOutputs] rejects on every frame — silently, because [segmentDefects] catches
         * inference failures so a bad frame cannot end a walkthrough. The result was the
         * worst of both: `isTrainedModel = true`, the UI labelling findings "YOLOv8n-Seg",
         * and "nothing flagged" for every capture forever. Failing here instead means the
         * caller falls back to the honestly-labelled heuristic segmenter.
         */
        private fun returnsSegmentationTuple(module: Module): Boolean = try {
            val size = YoloSegConfig.INPUT_SIZE
            val probe = Tensor.fromBlob(
                FloatArray(3 * size * size),
                longArrayOf(1, 3, size.toLong(), size.toLong())
            )
            val output = module.forward(IValue.from(probe))
            output.isTuple && output.toTuple().size >= 2
        } catch (e: Exception) {
            Log.w(TAG, "Segmentation probe failed: ${e.message}")
            false
        }

        /** PyTorch Lite needs a filesystem path, so assets are materialised once into filesDir. */
        private fun copyAssetIfPresent(context: Context, assetName: String): String? {
            val outFile = File(context.filesDir, assetName)
            if (outFile.exists() && outFile.length() > 0) return outFile.absolutePath
            return try {
                context.assets.open(assetName).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                outFile.absolutePath
            } catch (e: Exception) {
                Log.d(TAG, "Asset '$assetName' not available: ${e.message}")
                null
            }
        }
    }
}
