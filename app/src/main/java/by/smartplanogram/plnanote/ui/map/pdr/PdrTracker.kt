package by.smartplanogram.plnanote.ui.map.pdr

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * PDR без маяков. Шаги:
 * 1) TYPE_STEP_DETECTOR / TYPE_STEP_COUNTER — если есть
 * 2) иначе акселерометр (пики) — работает почти на всех телефонах
 *
 * Направление: ROTATION_VECTOR, иначе ACCEL+MAGNETIC.
 */
class PdrTracker(
    context: Context,
    private val floor: FloorConstraint,
    private val onUpdate: (x: Float, y: Float, headingDeg: Float, steps: Int) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    var enabled: Boolean = false
        private set

    var x: Float = 0f
        private set
    var y: Float = 0f
        private set
    var mapHeadingDeg: Float = 0f
        private set
    var stepCount: Int = 0
        private set

    var strideMeters: Float = 0.72f
    var mapBearingDeg: Float = 0f

    /** Как считаем шаги: hardware / counter / accel */
    var stepMode: String = "none"
        private set

    /** Без ACTIVITY_RECOGNITION — только акселерометр. */
    var forceAccelSteps: Boolean = false

    private var phoneAzimuthDeg: Float = 0f
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val accelReading = FloatArray(3)
    private val magReading = FloatArray(3)
    private var hasAccelSample = false
    private var hasMagSample = false

    // акселерометр-шаги
    private var lastAccelMag = 0f
    private var accelSmooth = 0f
    private var lastStepAtMs = 0L
    private var wasAbove = false
    private var stepCounterBase = -1f

    fun hasSensors(): Boolean {
        val canStep = stepDetector != null || stepCounter != null || accelerometer != null
        val canHead = rotation != null || (accelerometer != null && magnetometer != null)
        return canStep && canHead
    }

    /** ACTIVITY_RECOGNITION нужен только для hardware step_* (не для акселерометра). */
    fun needsActivityRecognition(): Boolean =
        !forceAccelSteps && (stepDetector != null || stepCounter != null)

    fun sensorStatusLabel(): String = when {
        forceAccelSteps || (stepDetector == null && stepCounter == null) ->
            if (accelerometer != null) "accelerometer" else "none"
        stepDetector != null -> "step_detector"
        stepCounter != null -> "step_counter"
        accelerometer != null -> "accelerometer"
        else -> "none"
    }

    fun resetTo(sx: Float, sy: Float) {
        val (cx, cy) = floor.clampAndPush(sx, sy)
        x = cx
        y = cy
        stepCount = 0
        stepCounterBase = -1f
        emit()
    }

    fun setPosition(sx: Float, sy: Float) {
        val (cx, cy) = floor.clampAndPush(sx, sy)
        x = cx
        y = cy
        emit()
    }

    fun start() {
        if (enabled) return
        if (!hasSensors()) return
        enabled = true

        when {
            !forceAccelSteps && stepDetector != null -> {
                stepMode = "step_detector"
                sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_GAME)
            }
            !forceAccelSteps && stepCounter != null -> {
                stepMode = "step_counter"
                sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_GAME)
            }
            else -> {
                stepMode = "accelerometer"
                accelerometer?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                }
            }
        }

        when {
            rotation != null -> {
                sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME)
            }
            else -> {
                accelerometer?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                }
                magnetometer?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                }
            }
        }
    }

    fun stop() {
        if (!enabled) return
        enabled = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                var az = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (az < 0f) az += 360f
                phoneAzimuthDeg = az
                mapHeadingDeg = normalize(phoneAzimuthDeg - mapBearingDeg)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelReading, 0, 3)
                hasAccelSample = true
                updateHeadingFromAccelMag()
                if (stepMode == "accelerometer") {
                    detectStepFromAccel(event.values)
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magReading, 0, 3)
                hasMagSample = true
                updateHeadingFromAccelMag()
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                registerStep()
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val total = event.values[0]
                if (stepCounterBase < 0f) {
                    stepCounterBase = total
                    return
                }
                val delta = (total - stepCounterBase).toInt()
                if (delta > stepCount) {
                    val add = delta - stepCount
                    repeat(add.coerceAtMost(5)) { registerStep() }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateHeadingFromAccelMag() {
        if (rotation != null) return
        if (!hasAccelSample || !hasMagSample) return
        val ok = SensorManager.getRotationMatrix(rotationMatrix, null, accelReading, magReading)
        if (!ok) return
        SensorManager.getOrientation(rotationMatrix, orientation)
        var az = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (az < 0f) az += 360f
        phoneAzimuthDeg = az
        mapHeadingDeg = normalize(phoneAzimuthDeg - mapBearingDeg)
    }

    private fun detectStepFromAccel(values: FloatArray) {
        val mag = sqrt(
            (values[0] * values[0] + values[1] * values[1] + values[2] * values[2]).toDouble()
        ).toFloat()
        // убрать гравитацию ~9.8
        val dynamic = abs(mag - 9.8f)
        accelSmooth = accelSmooth * 0.8f + dynamic * 0.2f
        val now = System.currentTimeMillis()
        val threshold = 1.35f
        val minInterval = 320L
        if (accelSmooth > threshold && !wasAbove && now - lastStepAtMs > minInterval) {
            wasAbove = true
            lastStepAtMs = now
            registerStep()
        } else if (accelSmooth < threshold * 0.55f) {
            wasAbove = false
        }
        lastAccelMag = mag
    }

    private fun registerStep() {
        stepCount++
        stepForward()
    }

    private fun stepForward() {
        val rad = Math.toRadians(mapHeadingDeg.toDouble())
        val nx = x + (sin(rad) * strideMeters).toFloat()
        val ny = y + (cos(rad) * strideMeters).toFloat()
        val (cx, cy) = floor.clampAndPush(nx, ny)
        x = cx
        y = cy
        emit()
    }

    private fun emit() {
        onUpdate(x, y, mapHeadingDeg, stepCount)
    }

    private fun normalize(deg: Float): Float {
        var d = deg % 360f
        if (d < 0f) d += 360f
        return d
    }
}
