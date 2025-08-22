package com.example.weardata

interface TrackerDataObserver {
    fun onHeartRateTrackerDataChanged(hrData: HeartRateData)
    fun onSpO2TrackerDataChanged(status: Int, spO2: Int)

    fun onError(errorResourceId: Int)
}
