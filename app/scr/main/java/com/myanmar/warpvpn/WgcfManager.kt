package com.myanmar.warpvpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class WgcfManager {
    private val client = OkHttpClient()
    private val apiBase = "https://api.cloudflareclient.com/v0i1909051800"

    suspend fun registerAndGetConfig(): String = withContext(Dispatchers.IO) {
        // 1. Register Account
        val regJson = JSONObject().apply {
            put("install_id", "")
            put("fcm_token", "")
            put("tos", java.time.Instant.now().toString())
            put("model", "Android")
            put("type", "Android")
            put("locale", "en_US")
        }

        val regRequest = Request.Builder()
            .url("$apiBase/reg")
            .post(regJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(regRequest).execute()
        val responseData = response.body?.string() ?: throw Exception("Registration failed")
        val json = JSONObject(responseData)

        val result = json.getJSONObject("result")
        val privateKey = result.getJSONObject("config").getJSONObject("peers").getString("public_key") // Simplified logic
        val ipv4 = result.getJSONObject("config").getJSONObject("interface").getJSONObject("addresses").getString("v4")
        val ipv6 = result.getJSONObject("config").getJSONObject("interface").getJSONObject("addresses").getString("v6")

        // 2. Myanmar Clean IP နှင့် WireGuard Config Content တည်ဆောက်ခြင်း
        val cleanIp = "162.159.192.1"
        val cleanPort = "500"

        return@withContext """
            [Interface]
            PrivateKey = $privateKey
            Address = $ipv4/32, $ipv6/128
            DNS = 1.1.1.1, 1.0.0.1

            [Peer]
            PublicKey = bmXOC+F1fxEMF9dyiK2H5/1SUtzH0Ju181UXVZsXA4A=
            Endpoint = $cleanIp:$cleanPort
            AllowedIPs = 0.0.0.0/0, ::/0
        """.trimIndent()
    }
}
