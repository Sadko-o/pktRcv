package com.example.pktrcv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.media.RingtoneManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.appcompat.app.AppCompatActivity
import com.example.pktrcv.databinding.ActivityMainBinding
import com.example.pktrcv.databinding.PacketItemBinding
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
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
    private var languageChanged = false


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
                val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
                when {
                    !isConnectedToWifi(wifiManager) ->  showWifiError()
                    else -> continue
                }
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
                var braceCount = 0

                while (char != -1) {
                    stringBuilder.append(char.toChar())
                    if (char.toChar() == '{') {
                        braceCount++
                    } else if (char.toChar() == '}') {
                        braceCount--
                        if (braceCount == 0) {
                            break
                        }
                    }
                    char = reader?.read() ?: -1
                }
                stringBuilder.toString().takeIf { it.isNotBlank() }
            }

            if (receivedData == null) {
                showError("Received null data. Reconnecting...")
                socket = null
                reader = null
                delay(3000)
                listenForData()
                return
            }
//            showAlert("receivedData", receivedData)
            handleReceivedData(receivedData)
        } catch (e: Exception) {
            showError(e.message ?: "Unknown Error")
        }
    }

    private suspend fun handleReceivedData(jsonString: String) {
        try {
            val gson = Gson()
            val packet = gson.fromJson(jsonString, PacketData::class.java)
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

    @SuppressLint("SetTextI18n", "StringFormatMatches")
    private suspend fun updateUI() {
        withContext(Dispatchers.Main) {
            binding.packetContainer.removeAllViews()
            packetsList.takeLast(3).asReversed().forEach { packet ->
                val packetItemBinding = PacketItemBinding.inflate(layoutInflater, binding.packetContainer, false)

                packetItemBinding.textViewTrainId.text = "Train ID/Номер поезда: ${packet.train_id}"
                packetItemBinding.textViewTimestamp.text = "Received packet time/Время получения данных: ${packet.timestamp}"
                packetItemBinding.textViewDistance.text = "Distance/Расстояние  : ${packet.distance} km"
                packetItemBinding.textViewETA.text = "ETA/Время в пути: ${packet.time} minutes"
                packetItemBinding.textViewDirection.text = "Direction/Направление: ${packet.direction}"



                binding.packetContainer.addView(packetItemBinding.root)
            }
        }
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
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}