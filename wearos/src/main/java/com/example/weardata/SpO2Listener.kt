package com.example.weardata

import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey
import androidx.annotation.NonNull // Para a anotação @NonNull

// Importar a classe R do pacote do seu módulo Wear OS
import com.example.weardata.R // Adicione esta importação para resolver R.string

class SpO2Listener : BaseListener() {
    private val APP_TAG = "SpO2Listener"

    init {
        val trackerEventListener = object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(@NonNull list: List<DataPoint>) {
                for (data in list) {
                    updateSpo2(data)
                }
            }

            override fun onFlushCompleted() {
                Log.i(APP_TAG, " onFlushCompleted called")
            }

            override fun onError(trackerError: HealthTracker.TrackerError) {
                Log.e(APP_TAG, " onError called: $trackerError")
                setHandlerRunning(false)
                if (trackerError == HealthTracker.TrackerError.PERMISSION_ERROR) {
                    TrackerDataNotifier.getInstance().notifyError(R.string.NoPermission)
                }
                if (trackerError == HealthTracker.TrackerError.SDK_POLICY_ERROR) {
                    TrackerDataNotifier.getInstance().notifyError(R.string.SdkPolicyError)
                }
            }
        }

        this.trackerEventListener = trackerEventListener
    }

    fun updateSpo2(dataPoint: DataPoint) {
        var status = SpO2Status.CALCULATING

        val statusValue = dataPoint.getValue(ValueKey.SpO2Set.STATUS)
        if (statusValue != null) {
            status = (statusValue as? Number)?.toInt() ?: SpO2Status.CALCULATING
        }

        var spo2Value = 0


        if (status == SpO2Status.MEASUREMENT_COMPLETED) {

            val spo2DataValue = dataPoint.getValue(ValueKey.SpO2Set.SPO2)
            if (spo2DataValue != null) {
                spo2Value = (spo2DataValue as? Number)?.toInt() ?: 0
            }
        }

        TrackerDataNotifier.getInstance().notifySpO2TrackerObservers(status, spo2Value)
        Log.d(APP_TAG, dataPoint.toString())
    }
}