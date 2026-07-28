package com.myanmar.warpvpn

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.wireguard.android.backend.GoBackend
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.InetAddress

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnMenu: ImageView
    private lateinit var btnConnectCard: MaterialCardView
    private lateinit var cardServer: MaterialCardView
    private lateinit var tvServerName: TextView
    private lateinit var imgPower: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvLogs: TextView
    private lateinit var cardLogs: MaterialCardView

    private lateinit var switchDarkMode: SwitchMaterial
    private lateinit var switchLogs: SwitchMaterial
    private lateinit var switchPing: SwitchMaterial
    private lateinit var btnRestoreDefaults: MaterialButton
    private lateinit var tvTelegram: TextView

    private var isConnected = false
    private var pingJob: Job? = null

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
        val prefs = getSharedPreferences("WARP_VPN_PREFS", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("DARK_MODE", true)

        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        btnMenu = findViewById(R.id.btnMenu)
        btnConnectCard = findViewById(R.id.btnConnectCard)
        cardServer = findViewById(R.id.cardServer)
        tvServerName = findViewById(R.id.tvServerName)
        imgPower = findViewById(R.id.imgPower)
        tvStatus = findViewById(R.id.tvStatus)
        tvLogs = findViewById(R.id.tvLogs)
        cardLogs = findViewById(R.id.cardLogs)

        switchDarkMode = findViewById(R.id.switchDarkMode)
        switchLogs = findViewById(R.id.switchLogs)
        switchPing = findViewById(R.id.switchPing)
        btnRestoreDefaults = findViewById(R.id.btnRestoreDefaults)
        tvTelegram = findViewById(R.id.tvTelegram)

        switchDarkMode.isChecked = isDark
        switchLogs.isChecked = prefs.getBoolean("SHOW_LOGS", true)
        switchPing.isChecked = prefs.getBoolean("AUTO_PING", true)

        cardLogs.visibility = if (switchLogs.isChecked) View.VISIBLE else View.GONE

        // WARP Server Card နှိပ်ပါက Select Location Bottom Sheet ပေါ်ရန်
        cardServer.setOnClickListener {
            showSelectLocationBottomSheet()
        }

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("DARK_MODE", isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        switchLogs.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("SHOW_LOGS", isChecked).apply()
            cardLogs.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        switchPing.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("AUTO_PING", isChecked).apply()
            if (isConnected) {
                startPingManager()
            }
        }

        btnRestoreDefaults.setOnClickListener {
            prefs.edit().clear().apply()
            Toast.makeText(this, "Defaults Restored!", Toast.LENGTH_SHORT).show()
            appendLog("Restored default settings. Saved Config cleared.")
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
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

    // --- Bottom Sheet Display (Select Location & Delete & Import) ---
    private fun showSelectLocationBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_select_location, null)
        bottomSheet.setContentView(dialogView)

        val btnAddConfig = dialogView.findViewById<MaterialCardView>(R.id.btnAddConfig)
        val btnDeleteConfig = dialogView.findViewById<ImageView>(R.id.btnDeleteConfig)
        val tvConfigTitle = dialogView.findViewById<TextView>(R.id.tvConfigTitle)
        val tvConfigSubtitle = dialogView.findViewById<TextView>(R.id.tvConfigSubtitle)

        val savedConfig = getSavedConfig()
        if (savedConfig != null) {
            tvConfigTitle.text = "WARP Custom / Saved Config"
            tvConfigSubtitle.text = "Saved WireGuard Config Active"
        } else {
            tvConfigTitle.text = "WARP Auto Clean IP"
            tvConfigSubtitle.text = "162.159.*****"
        }

        // Delete Config Event
        btnDeleteConfig.setOnClickListener {
            if (isConnected) {
                Toast.makeText(this, "Please disconnect VPN first!", Toast.LENGTH_SHORT).show()
            } else {
                deleteSavedConfig()
                appendLog("Config Deleted. Will generate new config on next connect.")
                Toast.makeText(this, "Config Deleted!", Toast.LENGTH_SHORT).show()
                bottomSheet.dismiss()
            }
        }

        // Add Config (+) Click Event
        btnAddConfig.setOnClickListener {
            bottomSheet.dismiss()
            showImportConfigDialog()
        }

        bottomSheet.show()
    }

    // --- Import Custom WireGuard Config Dialog ---
    private fun showImportConfigDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_server, null)
        val etConfigInput = dialogView.findViewById<EditText>(R.id.etConfigInput)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnImport = dialogView.findViewById<MaterialButton>(R.id.btnImport)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnImport.setOnClickListener {
            val inputText = etConfigInput.text.toString().trim()
            if (inputText.isNotEmpty() && inputText.contains("[Interface]")) {
                try {
                    Config.parse(ByteArrayInputStream(inputText.toByteArray()))
                    saveConfig(inputText)
                    appendLog("Custom WireGuard Config Imported Successfully!")
                    Toast.makeText(this, "Config Imported Successfully!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid Config Format!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please paste valid WireGuard Config!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
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

                    startPingManager()
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
                stopPingManager()
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

    private fun startPingManager() {
        stopPingManager()
        pingJob = lifecycleScope.launch(Dispatchers.IO) {
            val isAutoPing = switchPing.isChecked
            if (isAutoPing) {
                while (isActive && isConnected) {
                    runSinglePing()
                    delay(30000)
                }
            } else {
                runSinglePing()
            }
        }
    }

    private fun stopPingManager() {
        pingJob?.cancel()
        pingJob = null
    }

    private suspend fun runSinglePing() = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val address = InetAddress.getByName("1.1.1.1")
            val reachable = address.isReachable(3000)
            val pingTime = System.currentTimeMillis() - startTime

            if (reachable) {
                appendLog("Ping (1.1.1.1): $pingTime ms")
            } else {
                appendLog("Ping Timeout")
            }
        } catch (e: Exception) {
            appendLog("Ping Error: ${e.localizedMessage}")
        }
    }

    private fun resetUi() {
        stopPingManager()
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

    private fun deleteSavedConfig() {
        val prefs = getSharedPreferences("WARP_VPN_PREFS", Context.MODE_PRIVATE)
        prefs.edit().remove("WARP_CONFIG").apply()
    }

    class WgTunnel : com.wireguard.android.backend.Tunnel {
        override fun getName(): String = "WARPTunnel"
        override fun onStateChange(newState: com.wireguard.android.backend.Tunnel.State) {}
    }
}
