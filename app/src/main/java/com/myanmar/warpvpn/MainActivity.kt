package com.myanmar.warpvpn

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.wireguard.android.backend.GoBackend
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var btnConnectCard: MaterialCardView
    private lateinit var imgPower: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvLogs: TextView

    private var isConnected = false
    private val backend by lazy { GoBackend(applicationContext) }

    // Android VPN Permission Request Handler
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            appendLog("VPN Permission Granted!")
            connectVpn()
        } else {
            appendLog("VPN Permission Denied by User!")
            resetUi()
            Toast.makeText(this, "VPN Permission is required to connect!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnConnectCard = findViewById(R.id.btnConnectCard)
        imgPower = findViewById(R.id.imgPower)
        tvStatus = findViewById(R.id.tvStatus)
        tvLogs = findViewById(R.id.tvLogs)
        
        val savedConfig = getSavedConfig()
        if (savedConfig != null) {
            appendLog("Found saved WARP Config in device memory.")
        } else {
            appendLog("No saved config. Will generate a new one on connect.")
        }

        btnConnectCard.setOnClickListener {
            if (!isConnected) {
                prepareAndConnectVpn()
            } else {
                disconnectVpn()
            }
        }
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            tvLogs.append("> $message\n")
        }
    }
    
    private fun prepareAndConnectVpn() {
        tvStatus.text = "CONNECTING..."
        btnConnectCard.setStrokeColor(Color.parseColor("#F59E0B"))

        val intent = VpnService.prepare(this)
        if (intent != null) {
            appendLog("Requesting VPN Permission...")
            vpnPermissionLauncher.launch(intent)
        } else {
            appendLog("VPN Permission already granted.")
            connectVpn()
        }
    }
    
    private fun connectVpn() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var configStr = getSavedConfig()

                if (configStr == null) {
                    appendLog("Requesting NEW WARP Config from Cloudflare...")
                    val wgcf = WgcfManager()
                    configStr = wgcf.registerAndGetConfig()
                    
                    saveConfig(configStr)
                    appendLog("NEW Config saved successfully!")
                } else {
                    appendLog("Using SAVED WARP Config...")
                }

                appendLog("Building Tunnel Session...")
                val wgConfig = Config.parse(ByteArrayInputStream(configStr.toByteArray()))

                // WireGuard Tunnel
                backend.setState(
                    TunnelTunnel(),
                    com.wireguard.android.backend.Tunnel.State.UP,
                    wgConfig
                )

                withContext(Dispatchers.Main) {
                    isConnected = true
                    tvStatus.text = "CONNECTED"
                    tvStatus.setTextColor(Color.parseColor("#4ADE80"))
                    btnConnectCard.setStrokeColor(Color.parseColor("#4ADE80"))
                    imgPower.setColorFilter(Color.parseColor("#4ADE80"))
                    appendLog("Connected to WARP VPN!")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendLog("Error: ${e.localizedMessage}")
                    resetUi()
                }
            }
        }
    }

    private fun disconnectVpn() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                backend.setState(
                    TunnelTunnel(),
                    com.wireguard.android.backend.Tunnel.State.DOWN,
                    null
                )
                withContext(Dispatchers.Main) {
                    appendLog("Disconnected from VPN.")
                    resetUi()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendLog("Disconnect Error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun resetUi() {
        isConnected = false
        tvStatus.text = "TAP TO CONNECT"
        tvStatus.setTextColor(Color.parseColor("#94A3B8"))
        btnConnectCard.setStrokeColor(Color.parseColor("#334155"))
        imgPower.setColorFilter(Color.parseColor("#94A3B8"))
    }

    // Local Data Storage Helper Methods
    private fun saveConfig(config: String) {
        val prefs = getSharedPreferences("WARP_VPN_PREFS", Context.MODE_PRIVATE)
        prefs.edit().putString("WARP_CONFIG", config).apply()
    }

    private fun getSavedConfig(): String? {
        val prefs = getSharedPreferences("WARP_VPN_PREFS", Context.MODE_PRIVATE)
        return prefs.getString("WARP_CONFIG", null)
    }

    // Dummy Tunnel Class for WireGuard Backend
    class TunnelTunnel : com.wireguard.android.backend.Tunnel {
        override fun getName(): String = "WARPTunnel"
        override fun onStateChange(newState: com.wireguard.android.backend.Tunnel.State) {}
    }
}
