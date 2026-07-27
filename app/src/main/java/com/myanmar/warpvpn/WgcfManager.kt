package com.myanmar.warpvpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.wireguard.crypto.KeyPair
import java.util.UUID

class WgcfManager {
    private val client = OkHttpClient()
    private val apiBase = "https://api.cloudflareclient.com/v0i1909051800"

    suspend fun registerAndGetConfig(): String = withContext(Dispatchers.IO) {
        // 1. WireGuard KeyPair (Private Key & Public Key)
        val keyPair = KeyPair()
        val privateKey = keyPair.privateKey.toBase64()
        val publicKey = keyPair.publicKey.toBase64()

        // 2. Cloudflare API Payload
        val installId = UUID.randomUUID().toString()
        val regJson = JSONObject().apply {
            put("key", publicKey)
            put("install_id", installId)
            put("fcm_token", "")
            put("tos", "2024-01-01T00:00:00.000Z")
            put("model", "Android")
            put("type", "Android")
            put("locale", "en_US")
        }

        val regRequest = Request.Builder()
            .url("$apiBase/reg")
            .header("User-Agent", "okhttp/3.12.1")
            .header("Content-Type", "application/json; charset=UTF-8")
            .post(regJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(regRequest).execute()
        val responseData = response.body?.string() ?: throw Exception("Response empty")

        if (!response.isSuccessful) {
            throw Exception("API Error Code: ${response.code}\n$responseData")
        }

        val rootJson = JSONObject(responseData)

        val result = if (rootJson.has("result") && !rootJson.isNull("result")) {
            rootJson.getJSONObject("result")
        } else {
            rootJson
        }

        // Config Data
        val config = result.getJSONObject("config")
        val peers = config.getJSONArray("peers").getJSONObject(0)
        val serverPublicKey = peers.getString("public_key")
        
        val interfaceObj = config.getJSONObject("interface")
        val addresses = interfaceObj.getJSONObject("addresses")
        val ipv4 = addresses.getString("v4")
        val ipv6 = addresses.getString("v6")

        // Clean IP & Port
        val cleanIp = "162.159.192.1"
        val cleanPort = "500"

        // WireGuard Config String
        return@withContext """
            [Interface]
            PrivateKey = $privateKey
            Address = $ipv4/32, $ipv6/128
            DNS = 1.1.1.1, 1.0.0.1

            [Peer]
            PublicKey = $serverPublicKey
            Endpoint = $cleanIp:$cleanPort
            AllowedIPs = 0.0.0.0/0, ::/0
        """.trimIndent()
    }
}
