package com.smartlease.edge.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Real device-orientation tracking for the "ghost overlay" baseline-alignment feature.
 * Uses the rotation-vector sensor (fused from accelerometer + gyro + magnetometer) to get
 * pitch/roll, and reports how far off the current pose is from the baseline pose recorded
 * at move-in. This is the lowest-risk subsystem per the design doc's own risk table —
 * genuinely simple, genuinely real, no ML involved.
 */
class ArAlignmentTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var currentPitchDeg = 0f
    private var currentRollDeg = 0f
    private var baselinePitchDeg: Float? = null
    private var baselineRollDeg: Float? = null

    private var onPoseUpdate: ((AlignmentState) -> Unit)? = null

    data class AlignmentState(
        val pitchDeg: Float,
        val rollDeg: Float,
        val deltaFromBaselineDeg: Float?,
        val isAligned: Boolean
    )

    fun start(listener: (AlignmentState) -> Unit) {
        onPoseUpdate = listener
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        onPoseUpdate = null
    }

    /** Call once the user confirms "this matches the move-in photo angle". */
    fun captureBaseline() {
        baselinePitchDeg = currentPitchDeg
        baselineRollDeg = currentRollDeg
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        currentPitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
        currentRollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()

        val delta = if (baselinePitchDeg != null && baselineRollDeg != null) {
            val dp = abs(currentPitchDeg - baselinePitchDeg!!)
            val dr = abs(currentRollDeg - baselineRollDeg!!)
            kotlin.math.sqrt(dp * dp + dr * dr)
        } else null

        onPoseUpdate?.invoke(
            AlignmentState(
                pitchDeg = currentPitchDeg,
                rollDeg = currentRollDeg,
                deltaFromBaselineDeg = delta,
                isAligned = delta != null && delta < ALIGNMENT_TOLERANCE_DEG
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        // Within ~4 degrees combined pitch/roll drift reads as "aligned enough" for a
        // pixel-comparable before/after photo pair — loose enough to be usable handheld,
        // tight enough that the comparison is meaningful.
        const val ALIGNMENT_TOLERANCE_DEG = 4f
    }
}
