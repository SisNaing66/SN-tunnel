package com.myanmar.warpvpn

import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetAddress
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class WgcfManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Cloudflare API Base URLs (Multiple versions for fallback)
    private val cfApiBases = listOf(
        "https://api.cloudflareclient.com/v0i1909051800",
        "https://api.cloudflareclient.com/v0a2109151800",
        "https://api.cloudflareclient.com/v0a2409051800"
    )

    // WARP Endpoints (Multiple IPs for fallback)
    private val warpEndpoints = listOf(
        "162.159.192.1",
        "162.159.193.1",
        "162.159.194.1",
        "162.159.195.1",
        "162.159.196.1"
    )

    private val customApiUrl = "https://nyeinkokoaung.alwaysdata.net/wg/api.php"

    /**
     * Main function to register and get WARP config
     * @param engineMode: "CF_DIRECT" or "CUSTOM_API"
     * @param maxRetries: Number of retry attempts
     * @return WireGuard URI string
     */
    suspend fun registerAndGetConfig(
        engineMode: String = "CF_DIRECT",
        maxRetries: Int = 3
    ): String = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                return@withContext if (engineMode == "CUSTOM_API") {
                    fetchFromCustomApi()
                } else {
                    fetchFromCloudflareApiWithFallback()
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    delay(2000L * (attempt + 1)) // Exponential backoff
                }
            }
        }
        
        throw lastException ?: Exception("All retry attempts failed")
    }

    /**
     * Fetch config from Cloudflare API with multiple endpoint fallback
     */
    private fun fetchFromCloudflareApiWithFallback(): String {
        var lastException: Exception? = null

        for (apiBase in cfApiBases) {
            for (endpoint in warpEndpoints) {
                try {
                    return fetchFromCloudflareApi(apiBase, endpoint)
                } catch (e: Exception) {
                    lastException = e
                    // Log and continue to next endpoint
                    println("Failed with API: $apiBase, Endpoint: $endpoint - ${e.message}")
                }
            }
        }

        throw lastException ?: Exception("All Cloudflare API endpoints failed")
    }

    /**
     * Fetch config from specific Cloudflare API and endpoint
     */
    private fun fetchFromCloudflareApi(apiBase: String, endpoint: String): String {
        // Generate WireGuard key pair
        val keyPair = KeyPair()
        val privateKey = keyPair.privateKey.toBase64()
        val publicKey = keyPair.publicKey.toBase64()

        // Generate unique installation ID
        val installId = UUID.randomUUID().toString()

        // Build registration JSON
        val regJson = JSONObject().apply {
            put("key", publicKey)
            put("install_id", installId)
            put("fcm_token", "")
            put("tos", "2024-01-01T00:00:00.000Z")
            put("model", "Android")
            put("type", "Android")
            put("locale", "en_US")
        }

        // Create registration request
        val regRequest = Request.Builder()
            .url("$apiBase/reg")
            .header("User-Agent", "okhttp/3.12.1")
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Accept", "application/json")
            .post(regJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        // Execute request
        val response = client.newCall(regRequest).execute()
        val responseData = response.body?.string() ?: throw Exception("Empty response from Cloudflare API")

        if (!response.isSuccessful) {
            throw Exception("Cloudflare API error: ${response.code} - ${response.message}")
        }

        // Parse response
        val rootJson = JSONObject(responseData)
        val result = if (rootJson.has("result") && !rootJson.isNull("result")) {
            rootJson.getJSONObject("result")
        } else {
            rootJson
        }

        // Extract config data
        val config = result.getJSONObject("config")
        val peers = config.getJSONArray("peers").getJSONObject(0)
        val serverPublicKey = peers.getString("public_key")

        val interfaceObj = config.getJSONObject("interface")
        val addresses = interfaceObj.getJSONObject("addresses")
        val ipv4 = addresses.getString("v4")
        val ipv6 = addresses.getString("v6")

        // Build WireGuard URI without reserved field (to avoid parser errors)
        return buildWireGuardUri(
            privateKey = privateKey,
            endpoint = endpoint,
            port = "500",
            address = "$ipv4/32, $ipv6/128",
            publicKey = serverPublicKey,
            mtu = "1280"
        )
    }

    /**
     * Fetch config from custom backup API
     */
    private fun fetchFromCustomApi(): String {
        val userId = (100000..999999).random().toString()
        val requestUrl = "$customApiUrl?user_id=$userId"

        val request = Request.Builder()
            .url(requestUrl)
            .header("User-Agent", "okhttp/3.12.1")
            .header("Accept", "application/json")
            .get()
            .build()

        val response = client.newCall(request).execute()
        val responseData = response.body?.string() ?: throw Exception("Empty response from backup API")

        if (!response.isSuccessful) {
            throw Exception("Backup API error: ${response.code} - ${response.message}")
        }

        val json = JSONObject(responseData)
        val success = json.optBoolean("success", false)

        if (!success) {
            val errorMsg = json.optString("error", "Unknown error")
            throw Exception("Backup API failed: $errorMsg")
        }

        val configObj = json.getJSONObject("config")
        val clientPrivateKey = configObj.getString("private_key").trim()
        val rawAddress = configObj.getString("address").trim()
        val serverPublicKey = configObj.getString("public_key").trim()
        
        // Get reserved field (optional)
        val reservedStr = configObj.optString("reserved", "").trim()

        // Format address properly
        val formattedAddress = if (rawAddress.contains(",") && !rawAddress.contains(", ")) {
            rawAddress.replace(",", ", ")
        } else {
            rawAddress
        }

        // Find working endpoint
        val endpoint = findWorkingEndpoint()

        // Build WireGuard URI
        return buildWireGuardUri(
            privateKey = clientPrivateKey,
            endpoint = endpoint,
            port = "500",
            address = formattedAddress,
            publicKey = serverPublicKey,
            mtu = "1280",
            reserved = if (reservedStr.isNotEmpty()) reservedStr else null
        )
    }

    /**
     * Build WireGuard URI string
     */
    private fun buildWireGuardUri(
        privateKey: String,
        endpoint: String,
        port: String,
        address: String,
        publicKey: String,
        mtu: String,
        reserved: String? = null
    ): String {
        val encodedPrivateKey = URLEncoder.encode(privateKey, "UTF-8")
        val encodedAddress = URLEncoder.encode(address, "UTF-8")
        val encodedPublicKey = URLEncoder.encode(publicKey, "UTF-8")
        
        // Build base URI
        var uri = "wireguard://$encodedPrivateKey@$endpoint:$port"
        
        // Build query parameters
        val queryParams = mutableListOf<String>()
        queryParams.add("address=$encodedAddress")
        queryParams.add("publickey=$encodedPublicKey")
        queryParams.add("mtu=$mtu")
        
        // Add reserved if provided (only for custom API)
        if (reserved != null && reserved.isNotEmpty()) {
            val encodedReserved = URLEncoder.encode(reserved, "UTF-8")
            queryParams.add("reserved=$encodedReserved")
        }
        
        uri += "?" + queryParams.joinToString("&")
        
        // Add fragment
        uri += "#WARP-AUTO"
        
        return uri
    }

    /**
     * Find working endpoint by testing connectivity
     */
    private fun findWorkingEndpoint(): String {
        // Try all endpoints
        for (endpoint in warpEndpoints) {
            try {
                // Use InetAddress to check reachability
                val address = InetAddress.getByName(endpoint)
                if (address.isReachable(3000)) {
                    return endpoint
                }
            } catch (e: Exception) {
                // Continue to next endpoint
                continue
            }
        }
        
        // Return default if none work (will try fallback in MainActivity)
        return "162.159.195.1"
    }

    /**
     * Test if an endpoint is reachable
     */
    suspend fun testEndpoint(endpoint: String, timeout: Int = 3000): Boolean = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName(endpoint)
            return@withContext address.isReachable(timeout)
        } catch (e: Exception) {
            return@withContext false
        }
    }

    /**
     * Get all available endpoints
     */
    fun getAllEndpoints(): List<String> = warpEndpoints
}
