package com.sirbnl.vpeen.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.File

class VpeenVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private var localBridge: LocalSocksToHttpsBridge? = null

    companion object {
        init {
            runCatching { System.loadLibrary("hev-socks5-tunnel") }
        }
    }
    private external fun TProxyStartService(configPath: String, fd: Int)
    private external fun TProxyStopService()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopTunnel()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        val region = intent?.getStringExtra("REGION") ?: "nl"
        val protocol = intent?.getStringExtra("PROTOCOL") ?: "HTTP/3 (QUIC)"
        val ipMode = intent?.getStringExtra("IP_MODE") ?: "Dual-Stack (Default)"
        val doh = intent?.getStringExtra("DOH") ?: "Cloudflare (1.1.1.1)"
        val customEdge = intent?.getStringExtra("CUSTOM_EDGE").orEmpty()
        val tlsFrag = intent?.getBooleanExtra("TLS_FRAG", true) ?: true
        val lanSharing = intent?.getBooleanExtra("LAN_SHARING", false) ?: false
        val fcmFix = intent?.getBooleanExtra("FCM_FIX", true) ?: true

        val settings = VpnSettings(
            transportProtocol = protocol,
            ipProtocolMode = ipMode,
            customEdgeAddress = customEdge,
            bootstrapDoh = doh,
            tlsFragmentation = tlsFrag,
            lanSharing = lanSharing,
            fcmNotificationFix = fcmFix
        )

        Thread {
            runCatching {
                val upstream = VeePnApiClient().fetchServerForRegion(region, settings)

                if (!settings.proxyOnlyMode) {
                    localBridge = LocalSocksToHttpsBridge(
                        vpnService = this,
                        listenPort = 10808,
                        upstream = upstream,
                        settings = settings
                    ).also { it.start() }

                    val builder = Builder()
                        .setSession("My VeePN - $region")
                        .setMtu(1400)
                        .addAddress("10.10.10.2", 24)
                        .addRoute("0.0.0.0", 0)
                        .addDnsServer("1.1.1.1")
                        .addDisallowedApplication(packageName)

                    tunInterface = builder.establish() ?: error("VpnService.Builder returned null")

                    val bindIp = if (settings.lanSharing) "0.0.0.0" else "127.0.0.1"
                    val configFile = File(cacheDir, "tproxy.yml").apply {
                        writeText(
                            """
                            tunnel:
                              mtu: 1400
                              ipv4: 10.10.10.2
                            socks5:
                              port: 10808
                              address: $bindIp
                              udp: 'tcp'
                            """.trimIndent()
                        )
                    }

                    TProxyStartService(configFile.absolutePath, tunInterface!!.fd)
                }
            }
        }.start()

        return START_STICKY
    }

    private fun stopTunnel() {
        runCatching { TProxyStopService() }
        localBridge?.stop()
        runCatching { tunInterface?.close() }
        tunInterface = null
    }

    private fun startForegroundNotification() {
        val channelId = "my_veepn_tunnel"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "My VeePN Tunnel", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("My VeePN Active")
            .setContentText("Censorship-resistant system tunnel running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }
}
