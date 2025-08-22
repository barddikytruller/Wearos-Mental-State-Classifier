package com.example.weardata

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.weardata.ui.theme.WearDataTheme
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.samsung.android.service.health.tracking.HealthTrackerException
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme as WearMaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Scaffold

class WatchMainActivity : ComponentActivity() {

    private val APP_TAG = "WatchMainActivity"
    private val PATH_SENSOR_DATA = "/sensor_data"
    private val PERMISSION_REQUEST_CODE = 100

    @Volatile
    private var lastSendTimestamp: Long = 0L
    private val SEND_INTERVAL_MS = 2000

    private val activityRecognitionClient by lazy { ActivityRecognition.getClient(this) }
    private lateinit var activityPendingIntent: PendingIntent
    private var isUserStill = true
    private val userActivityStatus = mutableStateOf("Atividade: Verificando...")
    private lateinit var connectionManager: ConnectionManager
    private var heartRateListener: HeartRateListener? = null
    private var spo2Listener: SpO2Listener? = null
    private val connectionStatus = mutableStateOf("Desconectado")
    private val heartRateValue = mutableStateOf("FC: -- bpm")
    private val spo2Value = mutableStateOf("SpO2: -- %")
    private val hrStatus = mutableStateOf("Status FC: --")
    private val ibiValue = mutableStateOf("IBI: -- ms")
    private val ibiQuality = mutableStateOf("Qualidade IBI: --")
    private lateinit var messageClient: MessageClient

    private val activityUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ActivityTransitionReceiver.ACTIVITY_UPDATE_INTENT_ACTION) {
                val activityType = intent.getIntExtra("activity_type", -1)
                val transitionType = intent.getIntExtra("transition_type", -1)

                if (activityType == DetectedActivity.STILL) {
                    isUserStill = (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    userActivityStatus.value = if (isUserStill) "Atividade: Parado" else "Atividade: Em Movimento"
                    Log.i(APP_TAG, "Usuário ${if (isUserStill) "ENTROU" else "SAIU"} do estado PARADO.")
                }
            }
        }
    }

    private val trackerDataObserver = object : TrackerDataObserver {
        override fun onHeartRateTrackerDataChanged(hrData: HeartRateData) {
            runOnUiThread {
                heartRateValue.value = "FC: ${hrData.hr} bpm"
                hrStatus.value = "Status FC: ${getStatusString(hrData.status)}"
                ibiValue.value = "IBI: ${hrData.ibi} ms"
                ibiQuality.value = "Qualidade IBI: ${hrData.qIbi}"
                sendSensorDataToPhone("HEART_RATE", hrData.hr.toFloat(), hrData.status, hrData.ibi, hrData.qIbi, hrData.ibiList)
            }
        }

        override fun onSpO2TrackerDataChanged(status: Int, spO2Val: Int) {
            runOnUiThread {
                when (status) {
                    SpO2Status.CALCULATING -> spo2Value.value = "SpO2: Calculando..."
                    SpO2Status.MEASUREMENT_COMPLETED -> {
                        spo2Value.value = "SpO2: $spO2Val %"
                        sendSensorDataToPhone("SPO2", spO2Val.toFloat(), status)
                    }
                    SpO2Status.DEVICE_MOVING -> spo2Value.value = "SpO2: Dispositivo em movimento"
                    SpO2Status.LOW_SIGNAL -> spo2Value.value = "SpO2: Sinal baixo"
                    else -> spo2Value.value = "SpO2: --"
                }
            }
        }

        override fun onError(errorResourceId: Int) {
            runOnUiThread {
                connectionStatus.value = "Erro: ${getString(errorResourceId)}"
            }
        }
    }

    private val connectionObserver = object : ConnectionObserver {
        override fun onConnectionResult(stringResourceId: Int) {
            runOnUiThread {
                connectionStatus.value = getString(stringResourceId)
                if (stringResourceId == R.string.ConnectedToHs) {
                    heartRateListener = HeartRateListener()
                    spo2Listener = SpO2Listener()
                    heartRateListener?.let { connectionManager.initHeartRate(it) }
                    spo2Listener?.let { connectionManager.initSpO2(it) }
                }
            }
        }

        override fun onError(e: HealthTrackerException) {
            runOnUiThread {
                connectionStatus.value = "Erro de Conexão: ${e.message}"
                if (e.hasResolution()) {
                    e.resolve(this@WatchMainActivity)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        messageClient = Wearable.getMessageClient(this)
        TrackerDataNotifier.getInstance().addObserver(trackerDataObserver)
        connectionManager = ConnectionManager(connectionObserver)

        val intent = Intent(this, ActivityTransitionReceiver::class.java).apply {
            action = ActivityTransitionReceiver.TRANSITION_UPDATE_ACTION
        }
        activityPendingIntent = PendingIntent.getBroadcast(this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        setContent {
            WearDataTheme {
                Scaffold {
                    WatchAppScreen(
                        connectionStatus = connectionStatus.value,
                        heartRate = heartRateValue.value,
                        spo2 = spo2Value.value,
                        hrStatus = hrStatus.value,
                        ibi = ibiValue.value,
                        ibiQuality = ibiQuality.value,
                        activityStatus = userActivityStatus.value,
                        onRequestPermissions = { requestAppPermissions() },
                        onConnectService = { connectionManager.connect(this) },
                        onStartTracking = { startAllTrackers() },
                        onStopTracking = { stopAllTrackers() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(ActivityTransitionReceiver.ACTIVITY_UPDATE_INTENT_ACTION)
        ContextCompat.registerReceiver(this, activityUpdateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        if (!arePermissionsGranted()) {
            requestAppPermissions()
        } else {
            connectionManager.connect(applicationContext)
            startActivityUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(activityUpdateReceiver)
        stopAllTrackers()
        stopActivityUpdates()
        connectionManager.disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        TrackerDataNotifier.getInstance().removeObserver(trackerDataObserver)
    }

    private fun arePermissionsGranted(): Boolean {
        val bodySensors = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        val activityRecognition = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else { true }
        return bodySensors && activityRecognition
    }

    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BODY_SENSORS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                connectionStatus.value = "Permissões concedidas."
            } else {
                connectionStatus.value = "Permissões negadas."
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startActivityUpdates() {
        if (!arePermissionsGranted()) {
            Log.w(APP_TAG, "Tentativa de iniciar ActivityUpdates sem permissão.")
            return
        }
        val transitions = listOf(
            ActivityTransition.Builder().setActivityType(DetectedActivity.STILL).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
            ActivityTransition.Builder().setActivityType(DetectedActivity.STILL).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build()
        )
        val request = ActivityTransitionRequest(transitions)

        activityRecognitionClient.requestActivityTransitionUpdates(request, activityPendingIntent)
            .addOnSuccessListener { Log.i(APP_TAG, "Registro de transição de atividade bem-sucedido.") }
            .addOnFailureListener { e: Exception -> Log.e(APP_TAG, "Falha ao registrar transição de atividade.", e) }
    }

    @SuppressLint("MissingPermission")
    private fun stopActivityUpdates() {
        if (!arePermissionsGranted()) { return }
        activityRecognitionClient.removeActivityTransitionUpdates(activityPendingIntent)
            .addOnSuccessListener { Log.i(APP_TAG, "Remoção de transição de atividade bem-sucedida.") }
            .addOnFailureListener { e: Exception -> Log.e(APP_TAG, "Falha ao remover transição de atividade.", e) }
    }

    private fun startAllTrackers() {
        heartRateListener?.startTracker()
        spo2Listener?.startTracker()
        connectionStatus.value = "Rastreamento iniciado."
    }

    private fun stopAllTrackers() {
        heartRateListener?.stopTracker()
        spo2Listener?.stopTracker()
        connectionStatus.value = "Rastreamento parado."
    }

    private fun getStatusString(status: Int): String {
        return when (status) {
            HeartRateStatus.HR_STATUS_NONE -> getString(R.string.DetailsStatusNone)
            HeartRateStatus.HR_STATUS_FIND_HR -> getString(R.string.DetailsStatusFindHr)
            HeartRateStatus.HR_STATUS_ATTACHED -> getString(R.string.DetailsStatusAttached)
            HeartRateStatus.HR_STATUS_DETECT_MOVE -> getString(R.string.DetailsStatusMoveDetection)
            HeartRateStatus.HR_STATUS_DETACHED -> getString(R.string.DetailsStatusDetached)
            HeartRateStatus.HR_STATUS_LOW_RELIABILITY -> getString(R.string.DetailsStatusLowReliability)
            HeartRateStatus.HR_STATUS_VERY_LOW_RELIABILITY -> getString(R.string.DetailsStatusVeryLowReliability)
            HeartRateStatus.HR_STATUS_NO_DATA_FLUSH -> getString(R.string.DetailsStatusNoDataFlush)
            else -> "Desconhecido ($status)"
        }
    }

    private fun sendSensorDataToPhone(sensorType: String, value: Float, status: Int, ibi: Int = 0, ibiQuality: Int = 0, ibiList: List<Int>? = null) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSendTimestamp < SEND_INTERVAL_MS) {
            return
        }
        lastSendTimestamp = currentTime

        if (!isUserStill) {
            Log.d(APP_TAG, "Usuário em movimento. Dados de $sensorType não serão enviados.")
            return
        }

        val ibiListString = ibiList?.joinToString(",") ?: "N/A"
        val data = "$sensorType|$value|$status|$ibi|$ibiQuality|${System.currentTimeMillis()}|$ibiListString"
        val dataBytes = data.toByteArray(Charsets.UTF_8)

        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) return@addOnSuccessListener
            for (node in nodes) {
                messageClient.sendMessage(node.id, PATH_SENSOR_DATA, dataBytes)
                    .addOnSuccessListener { Log.d(APP_TAG, "Dados enviados (throttled): $data") }
                    .addOnFailureListener { e -> Log.e(APP_TAG, "Falha ao enviar dados.", e) }
            }
        }
    }
}

@Composable
fun WatchAppScreen(
    connectionStatus: String,
    heartRate: String,
    spo2: String,
    hrStatus: String,
    ibi: String,
    ibiQuality: String,
    activityStatus: String,
    onRequestPermissions: () -> Unit,
    onConnectService: (Context) -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "WearData - Coleta de Saúde", textAlign = TextAlign.Center, style = WearMaterialTheme.typography.title3)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Status: $connectionStatus", textAlign = TextAlign.Center, style = WearMaterialTheme.typography.body1)
        Text(text = activityStatus, textAlign = TextAlign.Center, style = WearMaterialTheme.typography.body1, modifier = Modifier.padding(bottom = 16.dp))
        Text(text = heartRate, style = WearMaterialTheme.typography.body1)
        Text(text = hrStatus, style = WearMaterialTheme.typography.caption1)
        Text(text = ibi, style = WearMaterialTheme.typography.caption1)
        Text(text = ibiQuality, style = WearMaterialTheme.typography.caption1, modifier = Modifier.padding(bottom = 8.dp))
        Text(text = spo2, style = WearMaterialTheme.typography.body1, modifier = Modifier.padding(bottom = 16.dp))
        Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text("Pedir Permissões") }
        Button(onClick = { onConnectService(context) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text("Conectar Serviço") }
        Button(onClick = onStartTracking, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text("Iniciar Rastreamento") }
        Button(onClick = onStopTracking, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text("Parar Rastreamento") }
    }
}

@Preview(device = "id:wearos_square", showSystemUi = true)
@Composable
fun DefaultWatchPreview() {
    WearDataTheme {
        WatchAppScreen(
            connectionStatus = "Conectado",
            heartRate = "FC: 75 bpm",
            spo2 = "SpO2: 98 %",
            hrStatus = "Status FC: OK",
            ibi = "IBI: 800 ms",
            ibiQuality = "Qualidade IBI: 1",
            activityStatus = "Atividade: Parado",
            onRequestPermissions = {},
            onConnectService = {},
            onStartTracking = {},
            onStopTracking = {}
        )
    }
}
