package com.myanmar.warpvpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class WgcfManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cfApiBase = "https://api.cloudflareclient.com/v0i1909051800"
    private val customApiUrl = "https://nyeinkokoaung.alwaysdata.net/wg/api.php"

    suspend fun registerAndGetXrayJson(engineMode: String = "CF_DIRECT"): String = withContext(Dispatchers.IO) {
        if (engineMode == "CUSTOM_API") {
            return@withContext fetchFromCustomApi()
        } else {
            return@withContext fetchFromCloudflareApi()
        }
    }

    private fun fetchFromCloudflareApi(): String {
        val installId = UUID.randomUUID().toString()
        val privateKey = generateRandomPrivateKey()
        val publicKey = privateKey

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

        return buildXrayJson(
            privateKey = privateKey,
            ipv4 = ipv4,
            ipv6 = ipv6,
            serverPublicKey = serverPublicKey,
            reserved = listOf(0, 0, 0)
        )
    }

    private fun fetchFromCustomApi(): String {
        val userId = (100000..999999).random().toString()
        val requestUrl = "$customApiUrl?user_id=$userId"

        val request = Request.Builder().url(requestUrl).get().build()
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
        val reservedStr = configObj.optString("reserved", "0,0,0")

        val addresses = rawAddress.split(",").map { it.trim() }
        val ipv4 = addresses.getOrNull(0) ?: "172.16.0.2/32"
        val ipv6 = addresses.getOrNull(1) ?: ""

        val reservedList = try {
            reservedStr.split(",").map { it.trim().toInt() }
        } catch (e: Exception) {
            listOf(0, 0, 0)
        }

        return buildXrayJson(
            privateKey = clientPrivateKey,
            ipv4 = ipv4,
            ipv6 = ipv6,
            serverPublicKey = serverPublicKey,
            reserved = reservedList
        )
    }

    private fun buildXrayJson(
        privateKey: String,
        ipv4: String,
        ipv6: String,
        serverPublicKey: String,
        reserved: List<Int>
    ): String {
        val addressArray = JSONArray().apply {
            put(if (ipv4.contains("/")) ipv4 else "$ipv4/32")
            if (ipv6.isNotEmpty()) {
                put(if (ipv6.contains("/")) ipv6 else "$ipv6/128")
            }
        }

        val reservedArray = JSONArray().apply {
            reserved.forEach { put(it) }
        }

        val xrayConfig = JSONObject().apply {
            put("log", JSONObject().put("loglevel", "none"))

            put("inbounds", JSONArray().put(JSONObject().apply {
                put("tag", "proxy")
                put("port", 10808)
                put("listen", "127.0.0.1")
                put("protocol", "socks")
                put("settings", JSONObject().apply {
                    put("auth", "noauth")
                    put("udp", true)
                })
            }))

            put("outbounds", JSONArray().put(JSONObject().apply {
                put("tag", "outbound")
                put("protocol", "wireguard")
                put("settings", JSONObject().apply {
                    put("secretKey", privateKey)
                    put("address", addressArray)
                    put("reserved", reservedArray)
                    put("mtu", 1280)
                    put("peers", JSONArray().put(JSONObject().apply {
                        put("publicKey", serverPublicKey)
                        put("endpoint", "162.159.192.1:500")
                    }))
                })
            }))
        }

        return xrayConfig.toString()
    }

    private fun generateRandomPrivateKey(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
}
