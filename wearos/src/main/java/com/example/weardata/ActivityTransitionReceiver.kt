package com.example.weardata

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityTransitionReceiver : BroadcastReceiver() {

    companion object {
        const val TRANSITION_UPDATE_ACTION = "com.example.weardata.TRANSITION_UPDATE_ACTION"

        const val ACTIVITY_UPDATE_INTENT_ACTION = "com.example.weardata.ACTIVITY_UPDATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TRANSITION_UPDATE_ACTION) {
            if (ActivityTransitionResult.hasResult(intent)) {
                val result = ActivityTransitionResult.extractResult(intent)
                result?.let {
                    for (event in it.transitionEvents) {
                        val activityType = toActivityString(event.activityType)
                        val transitionType = toTransitionTypeString(event.transitionType)
                        Log.d("ActivityReceiver", "Atividade Detectada: $activityType - Transição: $transitionType")

                        val localIntent = Intent(ACTIVITY_UPDATE_INTENT_ACTION).apply {
                            putExtra("activity_type", event.activityType)
                            putExtra("transition_type", event.transitionType)
                        }
                        context.sendBroadcast(localIntent)
                    }
                }
            }
        }
    }

    private fun toActivityString(activity: Int): String {
        return when (activity) {
            DetectedActivity.STILL -> "PARADO"
            DetectedActivity.WALKING -> "CAMINHANDO"
            DetectedActivity.RUNNING -> "CORRENDO"
            DetectedActivity.ON_BICYCLE -> "DE BICICLETA"
            DetectedActivity.IN_VEHICLE -> "EM VEÍCULO"
            else -> "DESCONHECIDA"
        }
    }

    private fun toTransitionTypeString(transitionType: Int): String {
        return when (transitionType) {
            0 -> "ENTROU"
            1 -> "SAIU"
            else -> "DESCONHECIDA"
        }
    }
}
