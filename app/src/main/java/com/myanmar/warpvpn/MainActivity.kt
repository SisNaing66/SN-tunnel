package com.myanmar.warpvpn

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.InetAddress

data class ConfigModel(val id: String, val name: String, val content: String, val endpoint: String)

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

    private lateinit var btnClearLogs: ImageView
    private lateinit var btnCopyLogs: ImageView

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

        btnClearLogs = findViewById(R.id.btnClearLogs)
        btnCopyLogs = findViewById(R.id.btnCopyLogs)

        switchDarkMode = findViewById(R.id.switchDarkMode)
        switchLogs = findViewById(R.id.switchLogs)
        switchPing = findViewById(R.id.switchPing)
        btnRestoreDefaults = findViewById(R.id.btnRestoreDefaults)
        tvTelegram = findViewById(R.id.tvTelegram)

        switchDarkMode.isChecked = isDark
        switchLogs.isChecked = prefs.getBoolean("SHOW_LOGS", true)
        switchPing.isChecked = prefs.getBoolean("AUTO_PING", true)

        cardLogs.visibility = if (switchLogs.isChecked) View.VISIBLE else View.GONE

        // --- Logs Copy & Clear Events ---
        btnClearLogs.setOnClickListener {
            tvLogs.text = "> Logs cleared.\n"
            Toast.makeText(this, "Logs Cleared", Toast.LENGTH_SHORT).show()
        }

        btnCopyLogs.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Connection Logs", tvLogs.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Logs Copied to Clipboard!", Toast.LENGTH_SHORT).show()
        }

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
            appendLog("Restored default settings. Saved Configs cleared.")
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

        updateActiveServerName()
    }

    private fun updateActiveServerName() {
        val configs = getAllConfigs()
        if (configs.isNotEmpty()) {
            tvServerName.text = "${configs[0].name} [${configs[0].endpoint}]"
        } else {
            tvServerName.text = "WARP Auto Clean IP [Auto]"
        }
    }

    // --- Bottom Sheet Display (Config List & Empty State) ---
    private fun showSelectLocationBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_select_location, null)
        bottomSheet.setContentView(dialogView)

        val btnAddConfig = dialogView.findViewById<MaterialCardView>(R.id.btnAddConfig)
        val tvEmptyState = dialogView.findViewById<TextView>(R.id.tvEmptyState)
        val rvConfigs = dialogView.findViewById<RecyclerView>(R.id.rvConfigs)

        rvConfigs.layoutManager = LinearLayoutManager(this)

        fun refreshList() {
            val configList = getAllConfigs()
            if (configList.isEmpty()) {
                tvEmptyState.visibility = View.VISIBLE
                rvConfigs.visibility = View.GONE
            } else {
                tvEmptyState.visibility = View.GONE
                rvConfigs.visibility = View.VISIBLE
                rvConfigs.adapter = ConfigAdapter(configList, { selectedConfig ->
                    updateActiveServerName()
                    bottomSheet.dismiss()
                }, { deleteConfig ->
                    if (isConnected) {
                        Toast.makeText(this, "Please disconnect VPN first!", Toast.LENGTH_SHORT).show()
                    } else {
                        deleteConfigById(deleteConfig.id)
                        appendLog("Deleted config: ${deleteConfig.name}")
                        refreshList()
                        updateActiveServerName()
                    }
                })
            }
        }

        refreshList()

        btnAddConfig.setOnClickListener {
            bottomSheet.dismiss()
            showImportConfigDialog()
        }

        bottomSheet.show()
    }

    // --- Import Custom Config Dialog ---
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

            if (inputText.isNotEmpty()) {
                try {
                    val parsedConfig = if (inputText.startsWith("wireguard://", ignoreCase = true)) {
                        parseWireGuardUri(inputText)
                    } else {
                        inputText
                    }

                    Config.parse(ByteArrayInputStream(parsedConfig.toByteArray()))
                    
                    val name = "Imported Server #${getAllConfigs().size + 1}"
                    val endpoint = extractEndpoint(parsedConfig)
                    
                    saveNewConfig(ConfigModel(System.currentTimeMillis().toString(), name, parsedConfig, endpoint))
                    
                    appendLog("Config Imported Successfully!")
                    Toast.makeText(this, "Config Imported Successfully!", Toast.LENGTH_SHORT).show()
                    updateActiveServerName()
                    dialog.dismiss()

                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid WireGuard Config Format!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please paste valid Config!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun extractEndpoint(configStr: String): String {
        val match = Regex("Endpoint\\s*=\\s*(\\S+)").find(configStr)
        return match?.groupValues?.get(1) ?: "162.159.192.1:500"
    }

    private fun parseWireGuardUri(uriString: String): String {
        val uri = Uri.parse(uriString)
        val privateKey = Uri.decode(uri.userInfo ?: throw Exception("Missing PrivateKey"))
        val host = uri.host ?: throw Exception("Missing Endpoint Host")
        val port = if (uri.port != -1) uri.port else 500
        val endpoint = "$host:$port"

        val address = Uri.decode(uri.getQueryParameter("address") ?: "")
        val publicKey = Uri.decode(uri.getQueryParameter("publickey") ?: "")
        val mtu = uri.getQueryParameter("mtu") ?: "1280"

        return """
            [Interface]
            PrivateKey = $privateKey
            Address = $address
            DNS = 1.1.1.1, 1.0.0.1
            MTU = $mtu

            [Peer]
            PublicKey = $publicKey
            Endpoint = $endpoint
            AllowedIPs = 0.0.0.0/0, ::/0
        """.trimIndent()
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
                val activeConfigs = getAllConfigs()
                var configStr: String

                if (activeConfigs.isEmpty()) {
                    appendLog("No config found. Requesting NEW WARP Config...")
                    val wgcf = WgcfManager()
                    configStr = wgcf.registerAndGetConfig()
                    
                    val newModel = ConfigModel("warp_default", "WARP Auto Clean IP", configStr, extractEndpoint(configStr))
                    saveNewConfig(newModel)
                    appendLog("NEW WARP Config saved!")
                } else {
                    configStr = activeConfigs[0].content
                    appendLog("Using Active Config [${activeConfigs[0].name}]...")
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

    // --- Multi Config Data Manager ---
    private fun getAllConfigs(): List<ConfigModel> {
        val prefs = getSharedPreferences("WARP_VPN_PREFS", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("CONFIG_LIST_JSON", "[]")
        val list = mutableListOf<ConfigModel>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(ConfigModel(obj.getString("id"), obj.getString("name"), obj.getString("content"), obj.getString("endpoint")))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    private fun saveNewConfig(model: ConfigModel) {
        val list = getAllConfigs().toMutableList()
        list.add(0, model) // Add to top
        saveConfigList(list)
    }

    private fun deleteConfigById(id: String) {
        val list = getAllConfigs().filter { it.id != id }
        saveConfigList(list)
    }

    private fun saveConfigList(list: List<ConfigModel>) {
        val array = JSONArray()
        list.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            obj.put("content", it.content)
            obj.put("endpoint", it.endpoint)
            array.put(obj)
        }
        val prefs = getSharedPreferences("WARP_VPN_PREFS", Context.MODE_PRIVATE)
        prefs.edit().putString("CONFIG_LIST_JSON", array.toString()).apply()
    }

    // --- RecyclerView Adapter ---
    class ConfigAdapter(
        private val list: List<ConfigModel>,
        private val onItemClick: (ConfigModel) -> Unit,
        private val onDeleteClick: (ConfigModel) -> Unit
    ) : RecyclerView.Adapter<ConfigAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvEndpoint: TextView = view.findViewById(R.id.tvEndpoint)
            val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
            val cardItem: View = view.findViewById(R.id.cardItem)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_config, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvName.text = item.name
            holder.tvEndpoint.text = item.endpoint

            holder.cardItem.setOnClickListener { onItemClick(item) }
            holder.btnDelete.setOnClickListener { onDeleteClick(item) }
        }

        override fun getItemCount(): Int = list.size
    }

    class WgTunnel : com.wireguard.android.backend.Tunnel {
        override fun getName(): String = "WARPTunnel"
        override fun onStateChange(newState: com.wireguard.android.backend.Tunnel.State) {}
    }
}
