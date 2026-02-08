package com.accelerator.app.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RunPhase {
    IDLE,
    WAITING,
    RUNNING,
    FINISHED
}

data class RunResult(
    val zeroTo60Mph: Double? = null,
    val zeroTo100Kmh: Double? = null,
    val quarterMileTime: Double? = null,
    val quarterMileSpeed: Double? = null,
    val topSpeedMph: Double = 0.0,
    val totalDistanceMeters: Double = 0.0,
    val totalTimeSeconds: Double = 0.0
)

data class AccelState(
    val phase: RunPhase = RunPhase.IDLE,
    val currentSpeedMph: Double = 0.0,
    val currentSpeedKmh: Double = 0.0,
    val elapsedSeconds: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val result: RunResult = RunResult()
)

class AccelerationTracker(context: Context) : SensorEventListener {

    companion object {
        private const val LAUNCH_THRESHOLD_MS2 = 0.5       // m/s² to detect launch
        private const val DECEL_THRESHOLD_MS2 = -0.3        // m/s² to detect slowdown
        private const val DECEL_DURATION_MS = 1500L         // sustained decel to end run
        private const val MPS_TO_MPH = 2.23694
        private const val MPS_TO_KMH = 3.6
        private const val QUARTER_MILE_METERS = 402.336
        private const val SIXTY_MPH_MPS = 26.8224           // 60 mph in m/s
        private const val HUNDRED_KMH_MPS = 27.7778         // 100 km/h in m/s
        private const val GPS_INTERVAL_MS = 50L
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _state = MutableStateFlow(AccelState())
    val state: StateFlow<AccelState> = _state.asStateFlow()

    private var runStartTimeMs = 0L
    private var lastLocationTime = 0L
    private var lastLocation: Location? = null
    private var totalDistance = 0.0
    private var topSpeedMps = 0.0
    private var decelStartTime = 0L
    private var currentAccelMagnitude = 0.0

    private var zeroTo60Recorded = false
    private var zeroTo100Recorded = false
    private var quarterMileRecorded = false

    private var pendingResult = RunResult()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            onGpsUpdate(location)
        }
    }

    fun startRun() {
        resetState()
        _state.value = AccelState(phase = RunPhase.WAITING)
        registerSensors()
    }

    fun stopRun() {
        unregisterSensors()
        _state.value = _state.value.copy(phase = RunPhase.IDLE)
        resetState()
    }

    fun reset() {
        unregisterSensors()
        resetState()
        _state.value = AccelState()
    }

    private fun resetState() {
        runStartTimeMs = 0L
        lastLocationTime = 0L
        lastLocation = null
        totalDistance = 0.0
        topSpeedMps = 0.0
        decelStartTime = 0L
        currentAccelMagnitude = 0.0
        zeroTo60Recorded = false
        zeroTo100Recorded = false
        quarterMileRecorded = false
        pendingResult = RunResult()
    }

    @SuppressLint("MissingPermission")
    private fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GPS_INTERVAL_MS)
            .setMinUpdateIntervalMillis(GPS_INTERVAL_MS)
            .build()

        fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
        fusedClient.removeLocationUpdates(locationCallback)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return

        // Use magnitude of X and Y (horizontal plane) as forward acceleration proxy
        val ax = event.values[0].toDouble()
        val ay = event.values[1].toDouble()
        val magnitude = Math.sqrt(ax * ax + ay * ay)

        // Sign: positive = accelerating, we use the largest axis as direction hint
        val dominantAccel = if (Math.abs(ay) > Math.abs(ax)) ay.toDouble() else ax.toDouble()
        currentAccelMagnitude = if (dominantAccel >= 0) magnitude else -magnitude

        val currentPhase = _state.value.phase

        if (currentPhase == RunPhase.WAITING && currentAccelMagnitude > LAUNCH_THRESHOLD_MS2) {
            // Launch detected!
            runStartTimeMs = System.currentTimeMillis()
            _state.value = _state.value.copy(phase = RunPhase.RUNNING)
            decelStartTime = 0L
        }

        if (currentPhase == RunPhase.RUNNING) {
            checkDeceleration()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onGpsUpdate(location: Location) {
        val phase = _state.value.phase
        if (phase != RunPhase.RUNNING && phase != RunPhase.WAITING) return

        val speedMps = if (location.hasSpeed()) location.speed.toDouble() else 0.0

        if (phase == RunPhase.WAITING) {
            // Also allow GPS-based launch detection if speed starts increasing
            if (speedMps > 1.0) {
                runStartTimeMs = System.currentTimeMillis()
                _state.value = _state.value.copy(phase = RunPhase.RUNNING)
                decelStartTime = 0L
            }
            return
        }

        // Phase is RUNNING
        val now = System.currentTimeMillis()
        val elapsed = (now - runStartTimeMs) / 1000.0

        // Calculate distance from GPS positions
        lastLocation?.let { prev ->
            totalDistance += prev.distanceTo(location).toDouble()
        }
        lastLocation = location

        if (speedMps > topSpeedMps) {
            topSpeedMps = speedMps
        }

        // Check milestones
        if (!zeroTo60Recorded && speedMps >= SIXTY_MPH_MPS) {
            pendingResult = pendingResult.copy(zeroTo60Mph = elapsed)
            zeroTo60Recorded = true
        }

        if (!zeroTo100Recorded && speedMps >= HUNDRED_KMH_MPS) {
            pendingResult = pendingResult.copy(zeroTo100Kmh = elapsed)
            zeroTo100Recorded = true
        }

        if (!quarterMileRecorded && totalDistance >= QUARTER_MILE_METERS) {
            pendingResult = pendingResult.copy(
                quarterMileTime = elapsed,
                quarterMileSpeed = speedMps * MPS_TO_MPH
            )
            quarterMileRecorded = true
        }

        pendingResult = pendingResult.copy(
            topSpeedMph = topSpeedMps * MPS_TO_MPH,
            totalDistanceMeters = totalDistance,
            totalTimeSeconds = elapsed
        )

        _state.value = AccelState(
            phase = RunPhase.RUNNING,
            currentSpeedMph = speedMps * MPS_TO_MPH,
            currentSpeedKmh = speedMps * MPS_TO_KMH,
            elapsedSeconds = elapsed,
            distanceMeters = totalDistance,
            result = pendingResult
        )
    }

    private fun checkDeceleration() {
        val now = System.currentTimeMillis()
        val elapsed = (now - runStartTimeMs) / 1000.0

        // Don't end the run in the first 2 seconds (avoid false positives during gear shifts etc.)
        if (elapsed < 2.0) return

        if (currentAccelMagnitude < DECEL_THRESHOLD_MS2) {
            if (decelStartTime == 0L) {
                decelStartTime = now
            } else if (now - decelStartTime > DECEL_DURATION_MS) {
                finishRun()
            }
        } else {
            decelStartTime = 0L
        }
    }

    private fun finishRun() {
        unregisterSensors()
        _state.value = _state.value.copy(
            phase = RunPhase.FINISHED,
            result = pendingResult
        )
    }
}
