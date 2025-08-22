package com.example.weardata

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import androidx.annotation.NonNull

class ConnectionManager(private val connectionObserver: ConnectionObserver) {
    private val TAG = "Connection Manager"
    private var healthTrackingService: HealthTrackingService? = null

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(TAG, "Connected")
            connectionObserver.onConnectionResult(R.string.ConnectedToHs)
            healthTrackingService?.let { service ->
                if (!isSpO2Available(service)) {
                    Log.i(TAG, "Device does not support SpO2 tracking")
                    connectionObserver.onConnectionResult(R.string.NoSpo2Support)
                }
                if (!isHeartRateAvailable(service)) {
                    Log.i(TAG, "Device does not support Heart Rate tracking")
                    connectionObserver.onConnectionResult(R.string.NoHrSupport)
                }
            }
        }

        override fun onConnectionEnded() {
            Log.i(TAG, "Disconnected")
        }

        override fun onConnectionFailed(e: HealthTrackerException?) {
            connectionObserver.onError(e ?: HealthTrackerException("Unknown error"))
        }
    }

    fun connect(context: Context) {
        healthTrackingService = HealthTrackingService(connectionListener, context)
        healthTrackingService?.connectService()
    }

    fun disconnect() {
        if (healthTrackingService != null)
            healthTrackingService?.disconnectService()
    }

    fun initSpO2(spO2Listener: SpO2Listener) {
        healthTrackingService?.let { service ->
            val spo2Tracker = service.getHealthTracker(HealthTrackerType.SPO2_ON_DEMAND)
            spO2Listener.healthTracker = spo2Tracker
        }
        setHandlerForBaseListener(spO2Listener)
    }

    fun initHeartRate(heartRateListener: HeartRateListener) {
        healthTrackingService?.let { service ->
            val heartRateTracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
            heartRateListener.healthTracker = heartRateTracker
        }
        setHandlerForBaseListener(heartRateListener)
    }

    private fun setHandlerForBaseListener(baseListener: BaseListener) {
        baseListener.setHandler(Handler(Looper.getMainLooper()))
    }

    private fun isSpO2Available(@NonNull healthTrackingService: HealthTrackingService): Boolean {
        val availableTrackers = healthTrackingService.trackingCapability.supportHealthTrackerTypes
        return availableTrackers.contains(HealthTrackerType.SPO2_ON_DEMAND)
    }

    private fun isHeartRateAvailable(@NonNull healthTrackingService: HealthTrackingService): Boolean {
        val availableTrackers = healthTrackingService.trackingCapability.supportHealthTrackerTypes
        return availableTrackers.contains(HealthTrackerType.HEART_RATE_CONTINUOUS)
    }
}
