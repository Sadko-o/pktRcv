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
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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

        setInitialCardVisibility()
        listenForData()
    }

    private fun setInitialCardVisibility() {
        binding.firstCardView.visibility = View.GONE
        binding.secondCardView.visibility = View.GONE
        binding.thirdCardView.visibility = View.GONE
    }

//    private fun showAlert(title: String, message: String) {
//        AlertDialog.Builder(this)
//            .setTitle(title)
//            .setMessage(message)
//            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
//            .show()
//    }
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
                    isConnectedToWifi(wifiManager) -> tryReceivingData(wifiManager)
                    else -> showWifiError()
                }
                delay(5000)
            }
        }
    }

    private fun isConnectedToWifi(wifiManager: WifiManager): Boolean {
        return wifiManager.isWifiEnabled && wifiManager.connectionInfo.networkId != -1
    }

    private suspend fun tryReceivingData(wifiManager: WifiManager) {
        try {
            val ipAddressInt = wifiManager.connectionInfo.ipAddress
            val currIpAddress = String.format(
                "%d.%d.%d.%d",
                ipAddressInt and 0xff,
                ipAddressInt shr 8 and 0xff,
                ipAddressInt shr 16 and 0xff,
                ipAddressInt shr 24 and 0xff
            )
//            showAlert("Ip address", currIpAddress)
            val ipAddress = "192.168.4.1"
//            https://192.168.4.1:8000/
            val port = 8000
            Socket(ipAddress, port).use { socket ->
                val receivedData = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                handleReceivedData(receivedData)
            }
        } catch (e: Exception) {
            showError(e.message ?: "Unknown Error")
        }
    }


    private suspend fun handleReceivedData(data: String) {
        val jsonObject = JSONObject(data)
        val packet = PacketData(
            distance = jsonObject.getDouble("distance").toFloat(),
            time = jsonObject.getInt("time"),
            trainId = jsonObject.getInt("train_id"),
            direction = jsonObject.getString("direction")[0],
            timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        )
        packetsList.add(packet)

        while (packetsList.size > 3) {
            packetsList.removeAt(0)
        }

        updateUI()
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
            binding.firstCardContainer.removeAllViews()

            for (packet in packetsList) {
                val packetBinding = inflatePacketLayout()
                populatePacketView(packetBinding, packet)
                binding.firstCardContainer.addView(packetBinding.root)
            }
        }
    }



    private fun inflatePacketLayout(): com.example.pktrcv.databinding.PacketLayoutBinding {
        return com.example.pktrcv.databinding.PacketLayoutBinding.inflate(layoutInflater, binding.firstCardContainer, false)
    }

    @SuppressLint("SetTextI18n")
    private fun populatePacketView(packetBinding: com.example.pktrcv.databinding.PacketLayoutBinding, packet: PacketData) {
        packetBinding.textViewTrainId.text = "Train ID: ${packet.trainId}"
        packetBinding.textViewTimestamp.text = "Received packet time: ${packet.timestamp}"
        packetBinding.textViewDistance.text = "Distance: ${packet.distance} km"
        packetBinding.textViewETA.text = "ETA: ${packet.time} minutes"
    }



    @SuppressLint("SetTextI18n")
    private fun updateCardData(cardView: CardView, packet: PacketData) {
        val linearLayout = cardView.getChildAt(0) as LinearLayout
        (linearLayout.getChildAt(0) as TextView).text = "Received packet time: ${packet.timestamp}"
        (linearLayout.getChildAt(1) as TextView).text = "Train ID:             ${packet.trainId}"
        (linearLayout.getChildAt(2) as TextView).text = "Distance:             ${packet.distance}"
        (linearLayout.getChildAt(3) as TextView).text = "ETA:                  ${packet.time} minutes"
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
                vibrator.vibrate(VibrationEffect.createOneShot(5000, VibrationEffect.DEFAULT_AMPLITUDE))
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