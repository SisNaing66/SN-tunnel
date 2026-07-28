package com.myanmar.warpvpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import libv2ray.Libv2ray
import libv2ray.CoreController

class XrayVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var coreController: CoreController? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_VPN") {
            stopVpn()
            return START_NOT_STICKY
        }

        val configJson = intent?.getStringExtra("XRAY_CONFIG")
        if (!configJson.isNullOrEmpty() && !isRunning) {
            startVpn(configJson)
        }

        return START_STICKY
    }

    private fun startVpn(configJson: String) {
        try {
            val builder = Builder()
                .addAddress("26.26.26.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setMtu(1280)
                .setSession("WARP TUNNEL")

            vpnInterface = builder.establish()
            val fd = vpnInterface?.fd ?: return

            coreController = Libv2ray.newCoreController(null)
            coreController?.startLoop(configJson, fd)
            isRunning = true

        } catch (e: Exception) {
            e.printStackTrace()
            stopVpn()
        }
    }

    private fun stopVpn() {
        try {
            if (isRunning) {
                coreController?.stopLoop()
                coreController = null
                isRunning = false
            }
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
