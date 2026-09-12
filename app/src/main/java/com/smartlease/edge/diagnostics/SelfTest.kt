package com.smartlease.edge.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.ConsumerIrManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import com.smartlease.edge.acoustic.AcousticModelBundle
import com.smartlease.edge.narration.ReportNarratorFactory
import com.smartlease.edge.acoustic.AcousticFeatureExtractor
import com.smartlease.edge.vision.DefectSegmenterFactory
import com.smartlease.edge.vision.YoloSegConfig
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

/**
 * Everything about the handset that we would otherwise have to ask adb for.
 *
 * The judging handset is a venue loaner: possibly no cable, possibly no laptop, and a short
 * window. So every number needed to trust — or distrust — a demo on it has to be readable on
 * the phone itself and screenshotable. That is the whole justification for this file.
 *
 * Two rules it exists to enforce:
 *
 *  - **No device-specific constant is committed anywhere.** Focal lengths, sensor size and
 *    the derived fx/fy are read from [CameraCharacteristics] at runtime. If the app ever
 *    silently used the development handset's intrinsics on the judging handset, every area
 *    it printed would be wrong and nobody would know.
 *  - **A field that cannot be read says so.** Never a zero, never a blank — those read as
 *    measurements. [UNAVAILABLE] is the only honest value for something this hardware or
 *    this API level cannot tell us.
 */
object SelfTest {

    const val UNAVAILABLE = "unavailable on this device"
    private const val WARMUP_RUNS = 3
    private const val TIMED_RUNS = 10

    data class Section(val title: String, val rows: List<Pair<String, String>>)

    /** Runs everything. Slow — do this off the main thread. */
    fun run(context: Context): List<Section> {
        val thermalBefore = thermalStatus(context)
        val modelsSection = models(context)
        val thermalAfter = thermalStatus(context)
        return listOf(
            device(context),
            infrared(context),
            cameras(context),
            modelsSection,
            performance(context, thermalBefore, thermalAfter),
            privacy(context)
        )
    }

    // ------------------------------------------------------------ performance

    /**
     * Battery and thermal readings from Android's own public APIs — no synthetic load, no
     * fabricated numbers. This is deliberately NOT a sustained stress test: it reads state
     * before and after the ten timed inference runs [models] already does, which is a real
     * but small and short workload. It answers "did ten inferences move the needle at all",
     * not "what happens after twenty minutes" — that longer protocol is manual, on-device,
     * and described in `docs/HACKTRACKER_STRATEGY.md` §5.3 (tests S1-S6). Treat every value
     * here as a single data point, not a benchmark result to quote on its own.
     */
    private fun performance(context: Context, thermalBefore: String, thermalAfter: String) = Section(
        "PERFORMANCE",
        listOf(
            "battery" to batteryPercent(context),
            "thermal status (idle, before self-test)" to thermalBefore,
            "thermal status (after vision warm-up loop)" to thermalAfter,
            "what this does and doesn't show" to
                "This reflects the load from this self-test's own $TIMED_RUNS-run inference " +
                "loop, not sustained real-world use. A stable reading here is not evidence " +
                "the device won't throttle during a real 10-20 minute walkthrough — only a " +
                "longer, manual run can show that. See docs/HACKTRACKER_STRATEGY.md §5.3."
        )
    )

    private fun batteryPercent(context: Context): String = try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (level in 0..100) "$level%" else UNAVAILABLE
    } catch (e: Exception) {
        "$UNAVAILABLE (${e.javaClass.simpleName})"
    }

    /**
     * [PowerManager.getCurrentThermalStatus] needs API 29. Below that, Android exposes no
     * public thermal signal at all — say so rather than guessing from CPU frequency, which
     * would be a proxy, not a measurement.
     */
    private fun thermalStatus(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "$UNAVAILABLE (needs API 29, this is API ${Build.VERSION.SDK_INT})"
        }
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            when (pm?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "NONE - no throttling"
                PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE - throttling likely"
                PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN imminent"
                null -> UNAVAILABLE
                else -> "unrecognised status code ${pm.currentThermalStatus}"
            }
        } catch (e: Exception) {
            "$UNAVAILABLE (${e.javaClass.simpleName})"
        }
    }

    // ---------------------------------------------------------------- device

    private fun device(context: Context) = Section(
        "DEVICE",
        listOf(
            "Build.MODEL" to Build.MODEL,
            "Build.DEVICE" to Build.DEVICE,
            "Build.MANUFACTURER" to Build.MANUFACTURER,
            "Build.SOC_MANUFACTURER" to
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER
                    else "$UNAVAILABLE (needs API 31, this is API ${Build.VERSION.SDK_INT})",
            "Build.SOC_MODEL" to
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL
                    else "$UNAVAILABLE (needs API 31, this is API ${Build.VERSION.SDK_INT})",
            "SUPPORTED_ABIS" to Build.SUPPORTED_ABIS.joinToString(", "),
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Display" to displayString(context)
        )
    )

    private fun displayString(context: Context): String = try {
        val m = context.resources.displayMetrics
        "${m.widthPixels} x ${m.heightPixels} px, ${m.densityDpi} dpi"
    } catch (e: Exception) {
        UNAVAILABLE
    }

    // ---------------------------------------------------------------- IR

    /**
     * The only genuinely iQOO-specific hardware in this submission, and the one path that has
     * never executed on hardware that has the emitter. Reports presence AND the carrier
     * ranges the emitter actually supports, because 38 kHz being in range is an assumption
     * until this screen says otherwise.
     */
    private fun infrared(context: Context): Section {
        val rows = mutableListOf<Pair<String, String>>()
        val hasFeature = context.packageManager
            .hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR)
        rows += "FEATURE_CONSUMER_IR" to if (hasFeature) "PRESENT" else "ABSENT"

        val mgr = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        val hasEmitter = try { mgr?.hasIrEmitter() == true } catch (e: Exception) { false }
        rows += "hasIrEmitter()" to if (hasEmitter) "true" else "false"

        // hasIrEmitter() only says the hardware exists -- it says nothing about whether
        // transmit() can actually be called. TRANSMIT_IR is what transmit() checks, and its
        // absence is exactly what made every transmit() throw SecurityException on a device
        // that does have the emitter (see the manifest comment next to this permission).
        val hasTransmitPermission = context.checkSelfPermission(
            android.Manifest.permission.TRANSMIT_IR
        ) == PackageManager.PERMISSION_GRANTED
        rows += "TRANSMIT_IR permission" to if (hasTransmitPermission) "GRANTED" else "NOT GRANTED"

        rows += "carrier frequencies" to if (!hasEmitter) {
            "$UNAVAILABLE (no emitter)"
        } else {
            try {
                mgr?.carrierFrequencies
                    ?.joinToString(", ") { "${it.minFrequency}-${it.maxFrequency} Hz" }
                    ?.ifEmpty { "reported none" }
                    ?: UNAVAILABLE
            } catch (e: Exception) {
                "$UNAVAILABLE (${e.javaClass.simpleName})"
            }
        }

        rows += "38 kHz supported" to when {
            !hasEmitter -> "$UNAVAILABLE (no emitter)"
            else -> try {
                val ok = mgr?.carrierFrequencies?.any { 38000 in it.minFrequency..it.maxFrequency }
                when (ok) { true -> "yes"; false -> "NO - the app transmits at 38 kHz"; null -> UNAVAILABLE }
            } catch (e: Exception) { UNAVAILABLE }
        }

        rows += "app behaviour here" to when {
            !hasEmitter ->
                "IR button reads 'No IR blaster detected' and logs a Failure. Degrades visibly, not silently."
            !hasTransmitPermission ->
                "IR button will call transmit() without TRANSMIT_IR declared -- ConsumerIrManager " +
                    "throws SecurityException, IrController catches it and logs a Failure. " +
                    "Degrades visibly, not silently, but nothing will actually transmit."
            else ->
                "IR trigger will transmit. Pattern is an unverified NEC header - the report says so."
        }
        return Section("INFRARED", rows)
    }

    // ---------------------------------------------------------------- cameras

    /**
     * fx and fy in pixels, derived at runtime, per camera:
     *
     *     fx = focalLength_mm / sensorWidth_mm  * activeArrayWidth_px
     *     fy = focalLength_mm / sensorHeight_mm * activeArrayHeight_px
     *
     * Quoted at the **active array** size. If CameraX binds a lower capture resolution, both
     * scale by the same ratio — which is exactly the trap a committed constant would hide.
     */
    private fun cameras(context: Context): Section {
        val rows = mutableListOf<Pair<String, String>>()
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return Section("CAMERAS", listOf("CameraManager" to UNAVAILABLE))

        try {
            val backIds = mgr.cameraIdList.filter {
                mgr.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            rows += "rear camera IDs" to (backIds.joinToString(", ").ifEmpty { "none" })
            rows += "CameraX will bind" to if (backIds.isEmpty()) UNAVAILABLE else
                "DEFAULT_BACK_CAMERA -> normally id ${backIds.first()}. CONFIRM against the focal " +
                        "lengths below: on a multi-camera phone the default is not always the main sensor."

            for (id in backIds) {
                val c = mgr.getCameraCharacteristics(id)
                val focals = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val size = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

                rows += "--- camera $id ---" to ""
                rows += "  focal lengths (mm)" to (focals?.joinToString(", ") ?: UNAVAILABLE)
                rows += "  sensor physical (mm)" to (size?.let { "%.2f x %.2f".format(it.width, it.height) } ?: UNAVAILABLE)
                rows += "  active array (px)" to (active?.let { "${it.width()} x ${it.height()}" } ?: UNAVAILABLE)

                rows += "  derived fx, fy (px)" to if (focals != null && focals.isNotEmpty() && size != null && active != null) {
                    val fx = focals[0] / size.width * active.width()
                    val fy = focals[0] / size.height * active.height()
                    "%.1f, %.1f  (at f=%.2fmm, active array)".format(fx, fy, focals[0])
                } else {
                    "$UNAVAILABLE (needs focal length + physical size + active array)"
                }
            }
        } catch (e: Exception) {
            rows += "enumeration failed" to (e.message ?: e.javaClass.simpleName)
        }

        rows += "used for area?" to
                "NO. The app reports defect area as a share of the frame. These intrinsics are " +
                "reported for verification only - nothing consumes them yet."
        return Section("CAMERAS", rows)
    }

    // ---------------------------------------------------------------- models

    private fun models(context: Context): Section {
        val rows = mutableListOf<Pair<String, String>>()

        // --- vision ---
        var segmenter: com.smartlease.edge.vision.DefectSegmenter? = null
        val coldMs = measureTimeMillis { segmenter = DefectSegmenterFactory.create(context) }
        val trained = segmenter?.isTrainedModel == true
        rows += "vision segmenter" to if (trained) {
            "TRAINED - yolov8n_seg.ptl, 4-class YOLOv8n-Seg via PyTorch Lite (CPU)"
        } else {
            "HEURISTIC FALLBACK - colour/contrast, findings labelled as such"
        }
        rows += "  asset present" to (assetNames(context).filter { it.endsWith(".ptl") }
            .joinToString(", ").ifEmpty { "none" })
        rows += "  cold load" to "$coldMs ms"

        rows += "  warm inference" to try {
            val seg = segmenter
            if (seg == null) UNAVAILABLE else {
                val bmp = testBitmap()
                repeat(WARMUP_RUNS) { seg.segmentDefects(bmp, 48f, 36f) }
                val times = (1..TIMED_RUNS).map {
                    measureTimeMillis { seg.segmentDefects(bmp, 48f, 36f) }
                }
                "${times.average().roundToInt()} ms mean, ${times.min()}-${times.max()} ms over " +
                        "$TIMED_RUNS runs (${YoloSegConfig.INPUT_SIZE}px, this CPU)"
            }
        } catch (e: Exception) {
            "$UNAVAILABLE (${e.javaClass.simpleName}: ${e.message})"
        }

        // --- acoustic ---
        var bundle: AcousticModelBundle? = null
        val acousticColdMs = measureTimeMillis { bundle = AcousticModelBundle.load(context) }
        val b = bundle
        if (b == null) {
            rows += "acoustic classifier" to "HEURISTIC FALLBACK - no trained bundle in assets"
        } else {
            rows += "acoustic classifier" to
                    "TRAINED - acoustic_tap_model.json, ${b.featureCount}-feature logistic regression (CPU)"
            rows += "  trained on" to "${b.trainedTaps} taps"
            rows += "  grouped CV accuracy" to
                    "${(b.groupedAccuracy * 100).roundToInt()}% " +
                    "(95% CI ${(b.groupedAccuracyCi.first * 100).roundToInt()}-" +
                    "${(b.groupedAccuracyCi.second * 100).roundToInt()}%)"
            rows += "  cold load" to "$acousticColdMs ms"
            rows += "  warm feature+score" to try {
                val ex = AcousticFeatureExtractor(b)
                val clip = FloatArray((b.sampleRate * 0.25f).toInt()) {
                    kotlin.math.sin(2.0 * Math.PI * 1200.0 * it / b.sampleRate).toFloat() *
                            kotlin.math.exp(-it / 900.0).toFloat()
                }
                repeat(WARMUP_RUNS) { b.hollowProbability(ex.extract(clip)) }
                val times = (1..TIMED_RUNS).map {
                    measureTimeMillis { b.hollowProbability(ex.extract(clip)) }
                }
                "${times.average().roundToInt()} ms mean over $TIMED_RUNS runs"
            } catch (e: Exception) {
                "$UNAVAILABLE (${e.javaClass.simpleName})"
            }
        }

        // Asks what this device is actually set up for rather than printing a constant.
        // The old line said "no LLM in this build" unconditionally, which would have gone on
        // lying the moment the weights were pushed. `describe` rather than `create`: this
        // screen must not load half a gigabyte of weights to render a row.
        rows += "report narrative" to ReportNarratorFactory.describe(context)
        rows += "NPU / HTP" to "NOT USED. All inference above is on the CPU via PyTorch Lite / Kotlin."
        rows += "peak heap" to try {
            "${(Debug.getNativeHeapAllocatedSize() / (1024 * 1024))} MB native heap allocated"
        } catch (e: Exception) { UNAVAILABLE }

        return Section("MODELS", rows)
    }

    /** Deterministic 640x640 input so the timing is comparable across devices and runs. */
    private fun testBitmap(): Bitmap {
        val n = YoloSegConfig.INPUT_SIZE
        val px = IntArray(n * n)
        var seed = 0x5EED
        for (i in px.indices) {
            seed = seed * 1103515245 + 12345
            val v = (seed ushr 16) and 0xFF
            px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            .apply { setPixels(px, 0, n, 0, 0, n, n) }
    }

    private fun assetNames(context: Context): List<String> = try {
        context.assets.list("")?.toList() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    // ---------------------------------------------------------------- privacy

    private fun privacy(context: Context): Section {
        val rows = mutableListOf<Pair<String, String>>()
        try {
            val pi = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_PERMISSIONS
            )
            val declared = pi.requestedPermissions?.toList() ?: emptyList()
            for (p in declared) {
                val granted = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
                rows += "  ${p.substringAfterLast('.')}" to if (granted) "GRANTED" else "not granted"
            }
            rows += "INTERNET declared" to
                    if (declared.any { it == android.Manifest.permission.INTERNET })
                        "YES - THIS IS A REGRESSION, the app must not have it"
                    else "NO - correct"

            val flags = pi.applicationInfo?.flags ?: 0
            val allowsBackup = (flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
            rows += "allowBackup" to if (allowsBackup) "TRUE - REGRESSION" else "false - correct"
        } catch (e: Exception) {
            rows += "package info" to "$UNAVAILABLE (${e.javaClass.simpleName})"
        }
        return Section("PRIVACY", rows)
    }

    /** Flat text for the clipboard and for the shared .txt. */
    fun asText(sections: List<Section>): String = buildString {
        append("SmartLease Edge - Device & Model Check\n")
        append("generated ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())).append('\n')
        for (s in sections) {
            append('\n').append(s.title).append('\n')
            append("-".repeat(s.title.length)).append('\n')
            for ((k, v) in s.rows) {
                if (v.isEmpty()) append(k).append('\n')
                else append(k).append(": ").append(v).append('\n')
            }
        }
    }
}
