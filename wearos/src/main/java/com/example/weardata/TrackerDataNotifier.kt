package com.example.weardata

class TrackerDataNotifier private constructor() {
    private val observers: MutableList<TrackerDataObserver> = mutableListOf()

    fun addObserver(observer: TrackerDataObserver) {
        if (!observers.contains(observer)) {
            observers.add(observer)
        }
    }

    fun removeObserver(observer: TrackerDataObserver) {
        observers.remove(observer)
    }

    fun notifyHeartRateTrackerObservers(hrData: HeartRateData) {
        for (observer in observers) {
            observer.onHeartRateTrackerDataChanged(hrData)
        }
    }

    fun notifySpO2TrackerObservers(status: Int, spO2: Int) {
        for (observer in observers) {
            observer.onSpO2TrackerDataChanged(status, spO2)
        }
    }


    fun notifyError(errorResourceId: Int) {
        for (observer in observers) {
            observer.onError(errorResourceId)
        }
    }

    companion object {
        private var instance: TrackerDataNotifier? = null
        fun getInstance(): TrackerDataNotifier {
            if (instance == null) {
                instance = TrackerDataNotifier()
            }
            return instance!!
        }
    }
}