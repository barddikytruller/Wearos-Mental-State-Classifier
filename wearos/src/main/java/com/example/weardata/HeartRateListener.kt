package com.example.weardata

import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey

class HeartRateListener : BaseListener() {
    private val APP_TAG = "HeartRateListener"

    private val ibiSlidingWindow = mutableListOf<Int>()
    private val IBI_WINDOW_SIZE = 10

    init {
        val trackerEventListener = object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(list: List<DataPoint>) {
                if (list.isEmpty()) {
                    return
                }

                for (dataPoint in list) {
                    val hrData = HeartRateData()
                    hrData.hr = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE).toInt()
                    hrData.status = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)


                    val hrIbiListRaw = dataPoint.getValue(ValueKey.HeartRateSet.IBI_LIST)
                    val hrIbiStatus = dataPoint.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST)

                    if (!hrIbiListRaw.isNullOrEmpty()) {

                        ibiSlidingWindow.addAll(hrIbiListRaw)

                        while (ibiSlidingWindow.size > IBI_WINDOW_SIZE) {
                            ibiSlidingWindow.removeAt(0)
                        }

                        if (ibiSlidingWindow.isNotEmpty()) {
                            hrData.ibi = ibiSlidingWindow.last()
                        }
                        Log.d(APP_TAG, "Janela de IBIs atualizada. Tamanho: ${ibiSlidingWindow.size}")
                    }

                    if (!hrIbiStatus.isNullOrEmpty()) {
                        hrData.qIbi = hrIbiStatus.last()
                    }

                    val dataToSend = HeartRateData(
                        status = hrData.status,
                        hr = hrData.hr,
                        ibi = hrData.ibi,
                        qIbi = hrData.qIbi,
                        ibiList = ibiSlidingWindow.toList()
                    )
                    TrackerDataNotifier.getInstance().notifyHeartRateTrackerObservers(dataToSend)
                    Log.d(APP_TAG, "Notificando com HR: ${dataToSend.hr}, Tamanho da lista de IBIs: ${ibiSlidingWindow.size}")
                }
            }

            override fun onFlushCompleted() {
                Log.i(APP_TAG, "onFlushCompleted chamado")
            }

            override fun onError(trackerError: HealthTracker.TrackerError) {
                Log.e(APP_TAG, "onError chamado: $trackerError")
                TrackerDataNotifier.getInstance().notifyError(R.string.HrError)
            }
        }
        super.trackerEventListener = trackerEventListener
    }
}
