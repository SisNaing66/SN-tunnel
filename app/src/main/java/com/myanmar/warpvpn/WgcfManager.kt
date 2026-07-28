package com.myanmar.warpvpn

import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class WgcfManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cfApiBase = "https://api.cloudflareclient.com/v0i1909051800"
    private val customApiUrl = "https://nyeinkokoaung.alwaysdata.net/wg/api.php"

    suspend fun registerAndGetConfig(engineMode: String = "CF_DIRECT"): String = withContext(Dispatchers.IO) {
        if (engineMode == "CUSTOM_API") {
            return@withContext fetchFromCustomApi()
        } else {
            return@withContext fetchFromCloudflareApi()
        }
    }

    private fun fetchFromCloudflareApi(): String {
        val keyPair = KeyPair()
        val privateKey = keyPair.privateKey.toBase64()
        val publicKey = keyPair.publicKey.toBase64()

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
            .url("$cfApiBase/reg")
            .header("User-Agent", "okhttp/3.12.1")
            .header("Content-Type", "application/json; charset=UTF-8")
            .post(regJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(regRequest).execute()
        val responseData = response.body?.string() ?: throw Exception("CF API Empty")

        if (!response.isSuccessful) {
            throw Exception("CF API Failed Code: ${response.code}")
        }

        val rootJson = JSONObject(responseData)
        val result = if (rootJson.has("result") && !rootJson.isNull("result")) {
            rootJson.getJSONObject("result")
        } else {
            rootJson
        }

        val config = result.getJSONObject("config")
        val peers = config.getJSONArray("peers").getJSONObject(0)
        val serverPublicKey = peers.getString("public_key")

        val interfaceObj = config.getJSONObject("interface")
        val addresses = interfaceObj.getJSONObject("addresses")
        val ipv4 = addresses.getString("v4")
        val ipv6 = addresses.getString("v6")

        val cleanIp = "162.159.192.1"
        val cleanPort = "500"
        
        val encodedPrivateKey = URLEncoder.encode(privateKey, "UTF-8")
        val encodedAddress = URLEncoder.encode("$ipv4/32, $ipv6/128", "UTF-8")
        val encodedPublicKey = URLEncoder.encode(serverPublicKey, "UTF-8")

        return "wireguard://$encodedPrivateKey@$cleanIp:$cleanPort?address=$encodedAddress&publickey=$encodedPublicKey&reserved=0%2C0%2C0&mtu=1280#WARP-AUTO"
    }

    private fun fetchFromCustomApi(): String {
        val userId = (100000..999999).random().toString()
        val requestUrl = "$customApiUrl?user_id=$userId"

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .build()

        val response = client.newCall(request).execute()
        val responseData = response.body?.string() ?: throw Exception("Backup API Empty")

        val json = JSONObject(responseData)
        val success = json.optBoolean("success", false)

        if (!success) {
            val errorMsg = json.optString("error", "Backup API Error")
            throw Exception("Backup API Failed: $errorMsg")
        }

        val configObj = json.getJSONObject("config")
        val clientPrivateKey = configObj.getString("private_key").trim()
        val rawAddress = configObj.getString("address").trim()
        val serverPublicKey = configObj.getString("public_key").trim()
        val reservedStr = configObj.optString("reserved", "0,0,0").trim()

        val formattedAddress = if (rawAddress.contains(",") && !rawAddress.contains(", ")) {
            rawAddress.replace(",", ", ")
        } else {
            rawAddress
        }

        val cleanIp = "162.159.192.1"
        val cleanPort = "500"

        val encodedPrivateKey = URLEncoder.encode(clientPrivateKey, "UTF-8")
        val encodedAddress = URLEncoder.encode(formattedAddress, "UTF-8")
        val encodedPublicKey = URLEncoder.encode(serverPublicKey, "UTF-8")
        val encodedReserved = URLEncoder.encode(reservedStr, "UTF-8")

        return "wireguard://$encodedPrivateKey@$cleanIp:$cleanPort?address=$encodedAddress&publickey=$encodedPublicKey&reserved=$encodedReserved&mtu=1280#WARP-AUTO"
    }
}
