package com.example.compasscorrector

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

class SensorHelper(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var lastAccelerometer = FloatArray(3)
    private var lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    var onAzimuthChanged: ((Float) -> Unit)? = null
    var onInclinationChanged: ((pitch: Float, roll: Float) -> Unit)? = null
    var onMagneticAccuracyChanged: ((Int) -> Unit)? = null
    var onMagneticFieldStrengthChanged: ((Float) -> Unit)? = null

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
            lastAccelerometerSet = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
            lastMagnetometerSet = true

            // Calculate field strength in uT
            val strength = kotlin.math.sqrt(event.values[0]*event.values[0] + event.values[1]*event.values[1] + event.values[2]*event.values[2])
            onMagneticFieldStrengthChanged?.invoke(strength)
        }

        if (lastAccelerometerSet && lastMagnetometerSet) {
            SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Azimuth in radians
            val azimuthInRadians = orientationAngles[0]
            // Convert to degrees
            var azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
            if (azimuthInDegrees < 0) {
                azimuthInDegrees += 360f
            }

            onAzimuthChanged?.invoke(azimuthInDegrees)

            // Pitch and Roll in degrees
            val pitchInDegrees = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            val rollInDegrees = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
            onInclinationChanged?.invoke(pitchInDegrees, rollInDegrees)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            onMagneticAccuracyChanged?.invoke(accuracy)
        }
    }

    companion object {
        fun getDeclination(latitude: Double, longitude: Double, altitude: Double, timeMillis: Long): Float {
            val geoField = GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                altitude.toFloat(),
                timeMillis
            )
            return geoField.declination
        }
    }
}
