package com.myanmar.warpvpn

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.wireguard.android.backend.GoBackend
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.InetAddress

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnMenu: ImageView
    private lateinit var btnConnectCard: MaterialCardView
    private lateinit var imgPower: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvLogs: TextView
    private lateinit var cardLogs: MaterialCardView

    private lateinit var switchLogs: SwitchMaterial
    private lateinit var switchPing: SwitchMaterial
    private lateinit var tvTelegram: TextView

    private var isConnected = false
    private val backend by lazy { GoBackend(applicationContext) }
    private val tunnel = WgTunnel()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            appendLog("VPN Permission Granted!")
            connectVpn()
        } else {
            appendLog("VPN Permission Denied!")
            resetUi()
            Toast.makeText(this, "VPN Permission is required!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        btnMenu = findViewById(R.id.btnMenu)
        btnConnectCard = findViewById(R.id.btnConnectCard)
        imgPower = findViewById(R.id.imgPower)
        tvStatus = findViewById(R.id.tvStatus)
        tvLogs = findViewById(R.id.tvLogs)
        cardLogs = findViewById(R.id.cardLogs)

        switchLogs = findViewById(R.id.switchLogs)
        switchPing = findViewById(R.id.switchPing)
        tvTelegram = findViewById(R.id.tvTelegram)
        
        val prefs = getSharedPreferences("WARP_VPN_PREFS", Context.MODE_PRIVATE)
        switchLogs.isChecked = prefs.getBoolean("SHOW_LOGS", true)
        switchPing.isChecked = prefs.getBoolean("AUTO_PING", true)

        cardLogs.visibility = if (switchLogs.isChecked) View.VISIBLE else View.GONE

        // Switch Action Handlers
        switchLogs.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("SHOW_LOGS", isChecked).apply()
            cardLogs.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        switchPing.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("AUTO_PING", isChecked).apply()
        }
        
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        
        tvTelegram.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/premium_channel_404"))
            startActivity(intent)
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
                    appendLog("Requesting NEW WARP Config...")
                    val wgcf = WgcfManager()
                    configStr = wgcf.registerAndGetConfig()
                    saveConfig(configStr)
                    appendLog("NEW Config saved successfully!")
                } else {
                    appendLog("Using SAVED WARP Config...")
                }

                appendLog("Building Tunnel Session...")
                val wgConfig = Config.parse(ByteArrayInputStream(configStr.toByteArray()))

                backend.setState(tunnel, com.wireguard.android.backend.Tunnel.State.UP, wgConfig)

                withContext(Dispatchers.Main) {
                    isConnected = true
                    tvStatus.text = "CONNECTED"
                    tvStatus.setTextColor(Color.parseColor("#4ADE80"))
                    btnConnectCard.setStrokeColor(Color.parseColor("#4ADE80"))
                    imgPower.setColorFilter(Color.parseColor("#4ADE80"))

                    Toast.makeText(this@MainActivity, "WARP VPN Connected Successfully!", Toast.LENGTH_SHORT).show()
                    appendLog("Connected to WARP VPN!")
                }
                
                if (switchPing.isChecked) {
                    runPingTest()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendLog("Error: ${e.localizedMessage}")
                    Toast.makeText(this@MainActivity, "Connection Failed!", Toast.LENGTH_SHORT).show()
                    resetUi()
                }
            }
        }
    }

    private fun disconnectVpn() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                appendLog("Stopping VPN Tunnel...")
                backend.setState(tunnel, com.wireguard.android.backend.Tunnel.State.DOWN, null)

                withContext(Dispatchers.Main) {
                    appendLog("Disconnected from VPN.")
                    Toast.makeText(this@MainActivity, "WARP VPN Disconnected", Toast.LENGTH_SHORT).show()
                    resetUi()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendLog("Disconnect Error: ${e.localizedMessage}")
                    resetUi()
                }
            }
        }
    }

    private suspend fun runPingTest() = withContext(Dispatchers.IO) {
        try {
            appendLog("Running Ping Test to 1.1.1.1...")
            val startTime = System.currentTimeMillis()
            val address = InetAddress.getByName("1.1.1.1")
            val reachable = address.isReachable(3000)
            val pingTime = System.currentTimeMillis() - startTime

            if (reachable) {
                appendLog("Ping Success: $pingTime ms (Clean IP Working)")
            } else {
                appendLog("Ping Test Timeout")
            }
        } catch (e: Exception) {
            appendLog("Ping Test Failed: ${e.localizedMessage}")
        }
    }

    private fun resetUi() {
        isConnected = false
        tvStatus.text = "TAP TO CONNECT"
        tvStatus.setTextColor(Color.parseColor("#94A3B8"))
        btnConnectCard.setStrokeColor(Color.parseColor("#334155"))
        imgPower.setColorFilter(Color.parseColor("#94A3B8"))
    }

    private fun saveConfig(config: String) {
        val prefs = getSharedPreferences("WARP_VPN_PREFS", Context.MODE_PRIVATE)
        prefs.edit().putString("WARP_CONFIG", config).apply()
    }

    private fun getSavedConfig(): String? {
        val prefs = getSharedPreferences("WARP_VPN_PREFS", Context.MODE_PRIVATE)
        return prefs.getString("WARP_CONFIG", null)
    }

    class WgTunnel : com.wireguard.android.backend.Tunnel {
        override fun getName(): String = "WARPTunnel"
        override fun onStateChange(newState: com.wireguard.android.backend.Tunnel.State) {}
    }
}
