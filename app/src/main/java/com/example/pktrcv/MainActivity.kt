package com.example.pktrcv

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.media.RingtoneManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pktrcv.databinding.ActivityMainBinding
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val packetsList = mutableListOf<PacketData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        listenForData()
    }

    private fun showAlert(title: String, message: String) {
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(title)
            builder.setMessage(message)
            builder.setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            builder.show()
        }
    }

    private fun isConnectedToWifi(wifiManager: WifiManager): Boolean {
//        Toast.makeText(this, "isConnectedWifi", Toast.LENGTH_SHORT).show()
        return wifiManager.isWifiEnabled && wifiManager.connectionInfo.networkId != -1
    }

    private var socket: Socket? = null
    private var reader: BufferedReader? = null

    private fun listenForData() {
        coroutineScope.launch {
            try {
                val ipAddress = "192.168.4.1"
                val port = 8000
                socket = Socket(ipAddress, port)
                reader = BufferedReader(InputStreamReader(socket?.getInputStream(), "UTF-8"))
            } catch (e: Exception) {
                showError("Error connecting to socket: ${e.message}")
                socket = null
                reader = null
                delay(3000)
                listenForData()
                return@launch
            }

            while (isActive) {
                if (socket == null || reader == null) {
                    showError("Socket or reader is null. Reconnecting...")
                    delay(3000)
                    listenForData()
                    return@launch
                }

                tryReceivingData()
                delay(3000)
            }
        }
    }

    private suspend fun tryReceivingData() {
        try {
            val receivedData: String? = withContext(Dispatchers.IO) {
                val stringBuilder = StringBuilder()
                var char: Int = reader?.read() ?: -1
                while (char != -1 && char.toChar() != '\n') {
                    stringBuilder.append(char.toChar())
                    char = reader?.read() ?: -1
                }
                if (stringBuilder.isNotEmpty()) stringBuilder.toString() else null
            }

            if (receivedData == null) {
                showError("Received null data. Reconnecting...")
                socket = null
                reader = null
                delay(3000)

                listenForData()
                return
            }

            handleReceivedData(receivedData)
        } catch (e: Exception) {
            showError(e.message ?: "Unknown Error")
        }
    }




    private suspend fun handleReceivedData(data: String) {
        showAlert("ReceivedData",data)
        val fixedData = data.replace("}{", "}\n{")
        val jsonObjects = fixedData.split("\n")
        for (jsonObjectStr in jsonObjects) {
            showAlert("jsonObjectStr",jsonObjectStr)
            try {

                val gson = Gson()
                val packet = gson.fromJson(jsonObjectStr, PacketData::class.java)
                packet.timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                packetsList.add(packet)
                while (packetsList.size > 3) {
                    packetsList.removeAt(0)
                }
                updateUI()
                withContext(Dispatchers.Main) {
                    playNotificationSound()
                    vibrateDevice()
                }
            } catch (e: Exception) {
                showError("Error parsing data: ${e.message}")
            }
        }

    }

    private suspend fun showWifiError() {
        withContext(Dispatchers.Main) {
            showAlert("No WiFi", "Please connect to a WiFi network.")
        }
    }

    private suspend fun showError(message: String) {
        withContext(Dispatchers.Main) {
            showAlert("Error", "Could not receive data: $message")
        }
    }

    private suspend fun updateUI() {
        withContext(Dispatchers.Main) {
            if (packetsList.isNotEmpty()) {
                populateLinearLayout(packetsList.last())
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populateLinearLayout(packet: PacketData) {
        binding.textViewTrainId.text = "Train ID: ${packet.trainId}"
        binding.textViewTimestamp.text = "Received packet time: ${packet.timestamp}"
        binding.textViewDistance.text = "Distance: ${packet.distance} km"
        binding.textViewETA.text = "ETA: ${packet.time} minutes"
    }

    private fun playNotificationSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, notification)
            r.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrateDevice() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(3000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}