package com.myanmar.warpvpn

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnConnect: Button
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnConnect = findViewById(R.id.btnConnect)

        btnConnect.setOnClickListener {
            if (!isConnected) {
                startVpnProcess()
            } else {
                stopVpnProcess()
            }
        }
    }

    private fun startVpnProcess() {
        tvStatus.text = "Status: Generating Config..."
        btnConnect.isEnabled = false

        lifecycleScope.launch {
            try {
                val wgcf = WgcfManager()
                val config = wgcf.registerAndGetConfig()
                
                // Config ထွက်လာလျှင် VPN စတင်ပေးခြင်း
                tvStatus.text = "Status: Connected to WARP!"
                btnConnect.text = "Disconnect"
                btnConnect.isEnabled = true
                isConnected = true
                
                Toast.makeText(this@MainActivity, "WARP Connected Successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                tvStatus.text = "Status: Error - ${e.localizedMessage}"
                btnConnect.isEnabled = true
            }
        }
    }

    private fun stopVpnProcess() {
        tvStatus.text = "Status: Disconnected"
        btnConnect.text = "Generate Config & Connect"
        isConnected = false
    }
}
