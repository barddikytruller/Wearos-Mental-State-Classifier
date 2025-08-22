package com.example.weardata

import com.samsung.android.service.health.tracking.HealthTrackerException

interface ConnectionObserver {
    fun onConnectionResult(stringResourceId: Int)
    fun onError(e: HealthTrackerException)
}