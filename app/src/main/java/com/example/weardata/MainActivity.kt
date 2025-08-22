package com.example.weardata

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.weardata.ui.theme.WearDataTheme
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import androidx.compose.ui.Alignment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    private val TAG = "PhoneMainActivity"

    companion object {
        private const val PATH_SENSOR_DATA = "/sensor_data"
    }


    private val SCALER_MEAN = floatArrayOf(69.50181249062895f, 42.49310372551363f, 27.774291277921908f, 21.347222222222225f)
    private val SCALER_SCALE = floatArrayOf(8.446014647194675f, 9.723895468378371f, 9.078934078907677f, 15.600944445532537f)
    private var tflite: Interpreter? = null
    private val TFLITE_MODEL_FILENAME = "mental_state_model_v2.tflite"

    private lateinit var sensorDataDao: SensorDataDao


    private val receivedHeartRate = mutableStateOf("FC Recebida: -- bpm")
    private val receivedSpo2 = mutableStateOf("SpO2 Recebida: -- %")
    private val receivedStatus = mutableStateOf("Status: --")
    private val receivedIbi = mutableStateOf("IBI: -- ms")
    private val receivedIbiQuality = mutableStateOf("Qualidade IBI: --")
    private val receivedTimestamp = mutableStateOf("Última Atualização: --")
    private val receivedVfcRmssd = mutableStateOf("VFC (RMSSD): -- ms")
    private val predictionResult = mutableStateOf("Predição: --")

    private var lastHeartRate: Float? = null
    private var lastSpo2: Float? = null
    private var lastSensorStatus: Int? = null
    private var lastIbi: Int? = null
    private var lastIbiQuality: Int? = null
    private var lastIbiListRaw: String? = null
    private var lastTimestamp: Long? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                exportDataToCsv()
            } else {
                Toast.makeText(this, "Permissão de escrita negada.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = AppDatabase.getDatabase(applicationContext)
        sensorDataDao = database.sensorDataDao()

        try {
            tflite = Interpreter(loadModelFile())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar o modelo TFLite", e)
            Toast.makeText(this, "Não foi possível carregar o modelo.", Toast.LENGTH_LONG).show()
        }

        setContent {
            val showClearDialog = remember { mutableStateOf(false) }

            WearDataTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PhoneAppScreen(
                        heartRate = receivedHeartRate.value,
                        spo2 = receivedSpo2.value,
                        status = receivedStatus.value,
                        ibi = receivedIbi.value,
                        ibiQuality = receivedIbiQuality.value,
                        timestamp = receivedTimestamp.value,
                        vfcRmssd = receivedVfcRmssd.value,
                        prediction = predictionResult.value,
                        onRelaxedClick = { saveLabeledData("Relaxado") },
                        onAnxiousClick = { saveLabeledData("Ansioso") },
                        onExportClick = { checkPermissionAndExport() },
                        onClearClick = { showClearDialog.value = true }
                    )

                    if (showClearDialog.value) {
                        ClearConfirmationDialog(
                            onConfirm = {
                                clearDatabase()
                                showClearDialog.value = false
                            },
                            onDismiss = {
                                showClearDialog.value = false
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        tflite?.close()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PATH_SENSOR_DATA) {
            val sensorData = String(messageEvent.data, Charsets.UTF_8)
            runOnUiThread { updateSensorDataUI(sensorData) }
        }
    }

    private fun updateSensorDataUI(sensorData: String) {
        val dataParts = sensorData.split("|")
        if (dataParts.size >= 7) {
            val sensorType = dataParts[0]
            val value = dataParts[1].toFloatOrNull()
            val status = dataParts[2].toIntOrNull()
            val ibi = dataParts[3].toIntOrNull()
            val ibiQuality = dataParts[4].toIntOrNull()
            val timestamp = dataParts[5].toLongOrNull()
            val ibiListRaw = dataParts[6]

            lastSensorStatus = status
            lastTimestamp = timestamp

            when (sensorType) {
                "HEART_RATE" -> {
                    lastHeartRate = value
                    lastIbi = ibi
                    lastIbiQuality = ibiQuality
                    lastIbiListRaw = ibiListRaw
                    receivedHeartRate.value = "FC Recebida: ${value?.toInt() ?: "--"} bpm"
                    receivedStatus.value = "Status FC: ${status ?: "--"}"
                    receivedIbi.value = "IBI: ${ibi ?: "--"} ms"
                    receivedIbiQuality.value = "Qualidade IBI: ${ibiQuality ?: "--"}"

                    if (ibiListRaw.isNotEmpty() && ibiListRaw != "N/A") {
                        val ibiList = ibiListRaw.split(",").mapNotNull { it.trim().toIntOrNull() }


                        val rmssd = calculateRmssd(ibiList)
                        val sdnn = calculateSdnn(ibiList)
                        val pnn50 = calculatePnn50(ibiList)

                        receivedVfcRmssd.value = "VFC (RMSSD): ${"%.2f".format(rmssd)} ms"


                        if (value != null && rmssd > 0) {
                            runInference(value, rmssd.toFloat(), sdnn.toFloat(), pnn50.toFloat())
                        }

                    } else {
                        receivedVfcRmssd.value = "VFC (RMSSD): N/A"
                        predictionResult.value = "Predição: --"
                    }
                }
                "SPO2" -> {
                    lastSpo2 = value
                    receivedSpo2.value = "SpO2 Recebida: ${value?.toInt() ?: "--"} %"
                    receivedStatus.value = "Status SpO2: ${status ?: "--"}"
                }
            }
            receivedTimestamp.value = "Última Atualização: ${timestamp?.let { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: "--"}"
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor: AssetFileDescriptor = assets.openFd(TFLITE_MODEL_FILENAME)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun runInference(heartRate: Float, vfcRmssd: Float, sdnn: Float, pnn50: Float) {
        if (tflite == null) {
            Log.e(TAG, "Modelo TFLite não está inicializado.")
            return
        }


        val inputData = floatArrayOf(heartRate, vfcRmssd, sdnn, pnn50)


        val scaledInput = FloatArray(4)
        for (i in inputData.indices) {
            scaledInput[i] = (inputData[i] - SCALER_MEAN[i]) / SCALER_SCALE[i]
        }


        val inputBuffer = ByteBuffer.allocateDirect(4 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (value in scaledInput) {
            inputBuffer.putFloat(value)
        }


        val outputBuffer = ByteBuffer.allocateDirect(4 * 1)
        outputBuffer.order(ByteOrder.nativeOrder())


        tflite?.run(inputBuffer, outputBuffer)


        outputBuffer.rewind()
        val probability = outputBuffer.float

        val resultText = if (probability > 0.5) "Ansioso" else "Relaxado"
        val confidence = if (resultText == "Ansioso") probability else 1 - probability

        predictionResult.value = "Predição: $resultText (${"%.1f".format(confidence * 100)}%)"
    }

    private fun calculateRmssd(ibiList: List<Int>): Double {
        if (ibiList.size < 2) return 0.0
        var sumOfSquaredDifferences = 0.0
        for (i in 0 until ibiList.size - 1) {
            val diff = ibiList[i + 1] - ibiList[i]
            sumOfSquaredDifferences += (diff * diff)
        }
        return sqrt(sumOfSquaredDifferences / (ibiList.size - 1))
    }

    private fun calculateSdnn(ibiList: List<Int>): Double {
        if (ibiList.size < 2) return 0.0
        val mean = ibiList.average()
        return sqrt(ibiList.map { (it - mean) * (it - mean) }.sum() / ibiList.size)
    }

    private fun calculatePnn50(ibiList: List<Int>): Double {
        if (ibiList.size < 2) return 0.0
        val diffs = ibiList.zipWithNext { a, b -> kotlin.math.abs(a - b) }
        val countOver50 = diffs.count { it > 50 }
        return (countOver50.toDouble() / diffs.size) * 100
    }

    private fun saveLabeledData(userState: String) {
        if (lastHeartRate == null && lastSpo2 == null) {
            Toast.makeText(this, "Aguardando dados do relógio para rotular.", Toast.LENGTH_SHORT).show()
            return
        }

        val rmssdToSave = lastIbiListRaw?.let {
            if (it.isNotEmpty() && it != "N/A") {
                calculateRmssd(it.split(",").mapNotNull { str -> str.trim().toIntOrNull() })
            } else null
        }

        val entity = SensorDataEntity(
            sensorType = if (lastHeartRate != null) "HEART_RATE" else "SPO2",
            value = lastHeartRate ?: lastSpo2 ?: 0f,
            status = lastSensorStatus ?: -1,
            ibi = lastIbi,
            ibiQuality = lastIbiQuality,
            ibiListRaw = lastIbiListRaw,
            vfcRmssd = rmssdToSave,
            timestamp = lastTimestamp ?: System.currentTimeMillis(),
            userState = userState
        )

        CoroutineScope(Dispatchers.IO).launch {
            sensorDataDao.insert(entity)
            withContext(Dispatchers.Main) {
                Log.d(TAG, "Dados salvos: $entity")
                Toast.makeText(applicationContext, "Estado '$userState' salvo!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionAndExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportDataToCsv()
        } else {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    exportDataToCsv()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun exportDataToCsv() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataList = sensorDataDao.getAllSensorData().first()
                if (dataList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Nenhum dado para exportar.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val csvHeader = "timestamp,sensorType,value,status,ibi,ibiQuality,vfcRmssd,userState,ibiListRaw\n"
                val stringBuilder = StringBuilder()
                stringBuilder.append(csvHeader)

                dataList.forEach { entity ->
                    stringBuilder.append("${entity.timestamp},")
                    stringBuilder.append("${entity.sensorType},")
                    stringBuilder.append("${entity.value},")
                    stringBuilder.append("${entity.status},")
                    stringBuilder.append("${entity.ibi ?: ""},")
                    stringBuilder.append("${entity.ibiQuality ?: ""},")
                    stringBuilder.append("${entity.vfcRmssd ?: ""},")
                    stringBuilder.append("${entity.userState},")
                    stringBuilder.append("\"${entity.ibiListRaw ?: ""}\"\n")
                }

                saveCsvToFile(stringBuilder.toString())

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao exportar dados para CSV", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Falha na exportação: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun saveCsvToFile(csvData: String) {
        val fileName = "sensor_data_${System.currentTimeMillis()}.csv"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveCsvToFileApi29AndAbove(fileName, csvData)
            } else {
                saveCsvToFileLegacy(fileName, csvData)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Dados exportados para a pasta Downloads!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar o arquivo CSV", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Erro ao salvar o arquivo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveCsvToFileApi29AndAbove(fileName: String, csvData: String) {
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(csvData)
                }
            }
        } else {
            throw Exception("Não foi possível criar o arquivo via MediaStore.")
        }
    }

    @Suppress("DEPRECATION")
    private fun saveCsvToFileLegacy(fileName: String, csvData: String) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(csvData)
            }
        }
    }

    private fun clearDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                sensorDataDao.deleteAll()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Banco de dados limpo com sucesso!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao limpar o banco de dados", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Falha ao limpar o banco de dados.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun PhoneAppScreen(
    heartRate: String,
    spo2: String,
    status: String,
    ibi: String,
    ibiQuality: String,
    timestamp: String,
    vfcRmssd: String,
    prediction: String,
    onRelaxedClick: () -> Unit,
    onAnxiousClick: () -> Unit,
    onExportClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Dados do Relógio", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = heartRate, style = MaterialTheme.typography.titleMedium)
        Text(text = status, style = MaterialTheme.typography.bodyMedium)
        Text(text = ibi, style = MaterialTheme.typography.bodySmall)
        Text(text = ibiQuality, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = spo2, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = vfcRmssd, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = timestamp, style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = prediction,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (prediction.contains("Ansioso")) MaterialTheme.colorScheme.error else Color.Unspecified
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRelaxedClick) { Text("Estou Relaxado") }
            Button(onClick = onAnxiousClick) { Text("Estou Ansioso") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExportClick) { Text("Exportar CSV") }
            Button(onClick = onClearClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Limpar Dados") }
        }
    }
}

@Composable
fun ClearConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar Ação") },
        text = { Text("Você tem certeza que deseja apagar todos os dados coletados? Esta ação não pode ser desfeita.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Apagar")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPhonePreview() {
    WearDataTheme {
        PhoneAppScreen(
            heartRate = "FC Recebida: 75 bpm",
            spo2 = "SpO2 Recebida: 98 %",
            status = "Status: OK",
            ibi = "IBI: 800 ms",
            ibiQuality = "Qualidade IBI: 1",
            timestamp = "Última Atualização: 12:30:00",
            vfcRmssd = "VFC (RMSSD): 50.00 ms",
            prediction = "Predição: Relaxado (95.0%)",
            onRelaxedClick = {},
            onAnxiousClick = {},
            onExportClick = {},
            onClearClick = {}
        )
    }
}
