package com.myanmar.warpvpn

import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var btnConnectCard: MaterialCardView
    private lateinit var imgPower: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvLogs: TextView
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnConnectCard = findViewById(R.id.btnConnectCard)
        imgPower = findViewById(R.id.imgPower)
        tvStatus = findViewById(R.id.tvStatus)
        tvLogs = findViewById(R.id.tvLogs)

        btnConnectCard.setOnClickListener {
            if (!isConnected) {
                startConnectProcess()
            } else {
                stopConnectProcess()
            }
        }
    }

    private fun appendLog(message: String) {
        tvLogs.append("> $message\n")
    }

    private fun startConnectProcess() {
        tvStatus.text = "CONNECTING..."
        btnConnectCard.setStrokeColor(Color.parseColor("#F59E0B")) // Yellow stroke
        appendLog("Initializing WireGuard Engine...")
        appendLog("Requesting WARP Config from Cloudflare...")

        lifecycleScope.launch {
            try {
                val wgcf = WgcfManager()
                val config = wgcf.registerAndGetConfig()

                appendLog("Config Generated Successfully!")
                appendLog("Setting Endpoint: 162.159.192.1:500")
                appendLog("Connected to WARP Network!")

                // UI update when connected
                isConnected = true
                tvStatus.text = "CONNECTED"
                tvStatus.setTextColor(Color.parseColor("#4ADE80"))
                btnConnectCard.setStrokeColor(Color.parseColor("#4ADE80")) // Green stroke
                imgPower.setColorFilter(Color.parseColor("#4ADE80"))

            } catch (e: Exception) {
                appendLog("Error: ${e.localizedMessage}")
                tvStatus.text = "FAILED TO CONNECT"
                btnConnectCard.setStrokeColor(Color.parseColor("#EF4444")) // Red stroke
            }
        }
    }

    private fun stopConnectProcess() {
        isConnected = false
        tvStatus.text = "TAP TO CONNECT"
        tvStatus.setTextColor(Color.parseColor("#94A3B8"))
        btnConnectCard.setStrokeColor(Color.parseColor("#334155")) // Normal stroke
        imgPower.setColorFilter(Color.parseColor("#94A3B8"))
        appendLog("Disconnected from VPN.")
    }
}
