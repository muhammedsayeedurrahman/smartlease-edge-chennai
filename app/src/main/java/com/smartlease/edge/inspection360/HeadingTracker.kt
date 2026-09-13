package com.smartlease.edge.inspection360

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

enum class Quadrant { NORTH, EAST, SOUTH, WEST }

class HeadingTracker(
    context: Context, 
    private val onQuadrantChange: (Quadrant, Float) -> Unit
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    var currentAngularVelocity = 0.0f
        private set

    var currentQuadrant: Quadrant = Quadrant.NORTH
        private set

    var currentAzimuth: Float = 0.0f
        private set

    fun start() {
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            // Calculate rotational speed (rad/sec)
            val wx = event.values[0]
            val wy = event.values[1]
            val wz = event.values[2]
            currentAngularVelocity = sqrt((wx * wx + wy * wy + wz * wz).toDouble()).toFloat()
            return
        }

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            // Azimuth in degrees [0, 360)
            val azimuth = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360) % 360
            val quadrant = when (azimuth) {
                in 315.0..360.0, in 0.0..45.0 -> Quadrant.NORTH
                in 45.0..135.0 -> Quadrant.EAST
                in 135.0..225.0 -> Quadrant.SOUTH
                else -> Quadrant.WEST
            }
            currentQuadrant = quadrant
            currentAzimuth = azimuth
            onQuadrantChange(quadrant, azimuth)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
