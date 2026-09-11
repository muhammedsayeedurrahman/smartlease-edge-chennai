package com.smartlease.edge.acoustic

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp

/**
 * The trained tap classifier, as exported by ml/acoustic/export_for_android.py.
 *
 * Everything the model needs travels as data: the mel filterbank and DCT basis (so Kotlin
 * never reconstructs them and cannot drift from the trainer), the StandardScaler statistics,
 * and the logistic-regression weights. Inference is therefore a matrix multiply plus a dot
 * product -- 36 multiply-adds -- which is why this ships as a 73 KB JSON rather than a
 * neural-network runtime.
 *
 * Immutable once loaded; [load] returns null rather than a partially-built bundle so callers
 * fall back to the deterministic heuristic instead of scoring against garbage.
 */
class AcousticModelBundle private constructor(
    val sampleRate: Int,
    val nFft: Int,
    val hop: Int,
    val nMels: Int,
    val nMfcc: Int,
    val topDb: Double,
    val amin: Double,
    val featureCount: Int,
    val classes: List<String>,
    val melFilterbank: FloatArray,
    val dctMatrix: FloatArray,
    private val scalerMean: FloatArray,
    private val scalerScale: FloatArray,
    private val coefficients: FloatArray,
    private val intercept: Float,
    /** Grouped cross-validated accuracy, for honest reporting in the UI. */
    val groupedAccuracy: Float,
    val groupedAccuracyCi: Pair<Float, Float>,
    val trainedTaps: Int
) {

    /** @return probability that the tap is hollow, in [0, 1]. */
    fun hollowProbability(features: FloatArray): Float {
        require(features.size == featureCount) {
            "Expected $featureCount features, got ${features.size}"
        }
        var z = intercept.toDouble()
        for (i in features.indices) {
            val scaled = (features[i] - scalerMean[i]) / scalerScale[i]
            z += scaled * coefficients[i]
        }
        return (1.0 / (1.0 + exp(-z))).toFloat()
    }

    companion object {
        private const val TAG = "AcousticModelBundle"
        const val ASSET_NAME = "acoustic_tap_model.json"

        fun load(context: Context, assetName: String = ASSET_NAME): AcousticModelBundle? = try {
            val text = context.assets.open(assetName).bufferedReader().use { it.readText() }
            parse(JSONObject(text))
        } catch (e: Exception) {
            Log.w(TAG, "Trained acoustic model unavailable ($assetName): ${e.message}")
            null
        }

        /** Exposed for unit tests, which parse the same asset from the filesystem. */
        fun parse(json: JSONObject): AcousticModelBundle {
            val ci = json.getJSONArray("groupedAccuracyCi95")
            return AcousticModelBundle(
                sampleRate = json.getInt("sampleRate"),
                nFft = json.getInt("nFft"),
                hop = json.getInt("hop"),
                nMels = json.getInt("nMels"),
                nMfcc = json.getInt("nMfcc"),
                topDb = json.getDouble("topDb"),
                amin = json.getDouble("amin"),
                featureCount = json.getInt("featureCount"),
                classes = json.getJSONArray("classes").toStringList(),
                melFilterbank = json.getJSONArray("melFilterbank").toFloatArray(),
                dctMatrix = json.getJSONArray("dctMatrix").toFloatArray(),
                scalerMean = json.getJSONArray("scalerMean").toFloatArray(),
                scalerScale = json.getJSONArray("scalerScale").toFloatArray(),
                coefficients = json.getJSONArray("coefficients").toFloatArray(),
                intercept = json.getDouble("intercept").toFloat(),
                groupedAccuracy = json.getDouble("groupedAccuracy").toFloat(),
                groupedAccuracyCi = ci.getDouble(0).toFloat() to ci.getDouble(1).toFloat(),
                trainedTaps = json.getInt("trainedTaps")
            )
        }

        private fun JSONArray.toFloatArray() = FloatArray(length()) { getDouble(it).toFloat() }
        private fun JSONArray.toStringList() = List(length()) { getString(it) }
    }
}
