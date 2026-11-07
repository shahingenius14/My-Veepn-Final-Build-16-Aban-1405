package com.sirbnl.vpeen.android

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class UpstreamProxyNode(
    val regionName: String,
    val host: String,
    val port: Int,
    val proxyUsername: String,
    val proxyPassword: String,
    val publicIp: String = "Detecting...",
    val countryFlag: String = "🌐"
)

data class VpnSettings(
    val transportProtocol: String = "HTTP/3 (QUIC)",
    val ipProtocolMode: String = "Dual-Stack (Default)",
    val customEdgeAddress: String = "",
    val bootstrapDoh: String = "Cloudflare (1.1.1.1)",
    val proxyOnlyMode: Boolean = false,
    val lanSharing: Boolean = false,
    val tunnelMtu: Int = 1400,
    val tlsFragmentation: Boolean = true,
    val lanBypass: Boolean = true,
    val fcmNotificationFix: Boolean = true
)

class VeePnApiClient {
    companion object {
        private const val API_BASE = "https://api.veepn.com/api/v2"
        private const val EXTENSION_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val deviceUuid: String = UUID.randomUUID().toString()

    fun fetchServerForRegion(regionCode: String, settings: VpnSettings): UpstreamProxyNode {
        val payload = JSONObject().apply {
            put("udid", deviceUuid)
            put("platform", "chrome")
            put("app_version", "3.1.0")
        }.toString()

        val tokenReq = Request.Builder()
            .url("$API_BASE/users/guest")
            .header("User-Agent", EXTENSION_UA)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val token = http.newCall(tokenReq).execute().use { resp ->
            val body = JSONObject(resp.body?.string() ?: "{}")
            body.optJSONObject("data")?.optString("token") ?: body.optString("token")
        }

        val req = Request.Builder()
            .url("$API_BASE/servers?location=$regionCode&free=1")
            .header("User-Agent", EXTENSION_UA)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        http.newCall(req).execute().use { resp ->
            val json = JSONObject(resp.body?.string() ?: "{}")
            val serverObj = json.getJSONArray("data").getJSONObject(0)
            
            val host = if (settings.customEdgeAddress.isNotBlank()) {
                settings.customEdgeAddress
            } else {
                serverObj.getString("host")
            }

            var resolvedIp = host
            var flagEmoji = "🛡️"
            runCatching {
                val ipCheckReq = Request.Builder().url("https://ipapi.co/json/").build()
                http.newCall(ipCheckReq).execute().use { ipResp ->
                    val ipJson = JSONObject(ipResp.body?.string() ?: "{}")
                    resolvedIp = ipJson.optString("ip", host)
                    val countryCode = ipJson.optString("country_code", "NL").uppercase()
                    flagEmoji = countryCode.codePoints().map { c -> c + 0x1F1E6 - 'A'.code }.collect(
                        StringBuilder(), { sb, c -> sb.appendCodePoint(c) }, { _, _ -> }
                    ).toString()
                }
            }

            return UpstreamProxyNode(
                regionName = regionCode,
                host = host,
                port = serverObj.optInt("port", 443),
                proxyUsername = serverObj.getString("username"),
                proxyPassword = serverObj.getString("password"),
                publicIp = resolvedIp,
                countryFlag = flagEmoji
            )
        }
    }
}
