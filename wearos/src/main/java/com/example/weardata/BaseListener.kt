package com.example.weardata

import android.os.Handler
import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker

open class BaseListener {

    private val APP_TAG = "BaseListener"

    private var handler: Handler? = null
    internal var healthTracker: HealthTracker? = null

    private var isHandlerRunning = false

    protected var trackerEventListener: HealthTracker.TrackerEventListener? = null

    fun setHandler(handler: Handler) {
        this.handler = handler
    }

    fun setHandlerRunning(handlerRunning: Boolean) {
        isHandlerRunning = handlerRunning
    }



    open fun startTracker() {
        Log.i(APP_TAG, "startTracker called ")
        healthTracker?.let { tracker ->
            trackerEventListener?.let { listener ->
                Log.d(APP_TAG, "healthTracker: $tracker")
                Log.d(APP_TAG, "trackerEventListener: $listener")
                if (!isHandlerRunning) {
                    handler?.post {
                        tracker.setEventListener(listener)
                        setHandlerRunning(true)
                    }
                } else {
                    handler?.post {
                        tracker.setEventListener(listener)
                    }
                }

                Log.d(APP_TAG, "HealthTracker.setEventListener() called.")
            } ?: Log.e(APP_TAG, "TrackerEventListener is null")
        } ?: Log.e(APP_TAG, "HealthTracker is null")
    }



    open fun stopTracker() {
        Log.i(APP_TAG, "stopTracker called ")
        healthTracker?.let { tracker ->
            trackerEventListener?.let { listener ->
                Log.d(APP_TAG, "healthTracker: $tracker")
                Log.d(APP_TAG, "trackerEventListener: $listener")
                if (isHandlerRunning) {

                    tracker.unsetEventListener()
                    setHandlerRunning(false)
                    handler?.removeCallbacksAndMessages(null)
                    Log.d(APP_TAG, "HealthTracker.unsetEventListener() called.")
                }
            } ?: Log.e(APP_TAG, "TrackerEventListener is null during stop")
        } ?: Log.e(APP_TAG, "HealthTracker is null during stop")
    }
}