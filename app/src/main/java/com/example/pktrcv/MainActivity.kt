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
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pktrcv.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.lang.System.out
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.reflect.typeOf

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

    private fun listenForData() {
        coroutineScope.launch {
            while (isActive) {
                val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
                when {

                    isConnectedToWifi(wifiManager) -> tryReceivingData()
                    else -> showWifiError()
                }
                delay(3000)
            }
        }
    }

    private fun isConnectedToWifi(wifiManager: WifiManager): Boolean {
        Toast.makeText(this, "isConnectedWifi", Toast.LENGTH_SHORT).show()
        return wifiManager.isWifiEnabled && wifiManager.connectionInfo.networkId != -1
    }

    private suspend fun tryReceivingData() {
        try {
            val ipAddress = "192.168.4.1"
            val port = 8000
            val receivedData: String? = withContext(Dispatchers.IO) {
                val socket = Socket(ipAddress, port)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val data = reader.readLine()
                socket.close()
                data
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, receivedData, Toast.LENGTH_SHORT).show()
                handleReceivedData(receivedData ?: "")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "tryReceivedData", Toast.LENGTH_SHORT).show();
            showError(e.message ?: "Unknown Error")
        }
    }


    private suspend fun handleReceivedData(data: String) {
        try {
            val jsonObject = JSONObject(data)
            val packet = PacketData(
                distance = jsonObject.getDouble("distance").toFloat(),
                time = jsonObject.getInt("time"),
                trainId = jsonObject.getInt("train_id"),
                direction = jsonObject.getString("direction").first(),
                timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            )
            packetsList.add(packet)

            while (packetsList.size > 3) {
                packetsList.removeAt(0)
            }

            showAlert("received", packet.toString())
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

    private suspend fun updateUI() {
        withContext(Dispatchers.Main) {
            val containers = listOf(
                binding.firstCardContainer,
            )
            for (index in packetsList.indices) {
                populateLinearLayout(containers[index], packetsList[index], index+1)
            }
        }
    }

    @SuppressLint("SetTextI18n", "DiscouragedApi")
    private fun populateLinearLayout(linearLayout: LinearLayout, packet: PacketData, packetNumber: Int) {
        val context = linearLayout.context
        val textViewTrainId = linearLayout.findViewById<TextView>(
            context.resources.getIdentifier("textViewTrainId$packetNumber", "id", context.packageName)
        )
        val textViewTimestamp = linearLayout.findViewById<TextView>(
            context.resources.getIdentifier("textViewTimestamp$packetNumber", "id", context.packageName)
        )
        val textViewDistance = linearLayout.findViewById<TextView>(
            context.resources.getIdentifier("textViewDistance$packetNumber", "id", context.packageName)
        )
        val textViewETA = linearLayout.findViewById<TextView>(
            context.resources.getIdentifier("textViewETA$packetNumber", "id", context.packageName)
        )

        textViewTrainId.text = "Train ID: ${packet.trainId}"
        textViewTimestamp.text = "Received packet time: ${packet.timestamp}"
        textViewDistance.text = "Distance: ${packet.distance} km"
        textViewETA.text = "ETA: ${packet.time} minutes"
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
                vibrator.vibrate(5000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}