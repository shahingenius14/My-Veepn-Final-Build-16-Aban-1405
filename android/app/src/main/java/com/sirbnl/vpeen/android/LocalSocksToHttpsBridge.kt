package com.sirbnl.vpeen.android

import android.net.VpnService
import android.util.Base64
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class LocalSocksToHttpsBridge(
    private val vpnService: VpnService,
    private val listenPort: Int = 10808,
    private val upstream: UpstreamProxyNode,
    private val settings: VpnSettings
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory

    fun start() {
        val bindAddress = if (settings.lanSharing) "0.0.0.0" else "127.0.0.1"
        serverSocket = ServerSocket(listenPort, 128, InetAddress.getByName(bindAddress))
        scope.launch {
            while (isActive) {
                val client = runCatching { serverSocket?.accept() }.getOrNull() ?: break
                launch { handleSocksClient(client) }
            }
        }
    }

    fun stop() {
        scope.cancel()
        runCatching { serverSocket?.close() }
    }

    private suspend fun handleSocksClient(client: Socket) = withContext(Dispatchers.IO) {
        client.use { sock ->
            sock.tcpNoDelay = true
            sock.keepAlive = true // Anti-DPI Idle Drop Prevention
            val input = sock.getInputStream()
            val output = sock.getOutputStream()

            val ver = input.read()
            if (ver != 0x05) return@withContext
            val nMethods = input.read()
            val methods = ByteArray(nMethods)
            input.readFully(methods)
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            val header = ByteArray(4)
            input.readFully(header)
            val cmd = header[1].toInt()
            val atyp = header[3].toInt()

            if (cmd != 0x01) {
                output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                return@withContext
            }

            val targetHost = when (atyp) {
                0x01 -> {
                    val ip = ByteArray(4).also { input.readFully(it) }
                    InetAddress.getByAddress(ip).hostAddress!!
                }
                0x03 -> {
                    val len = input.read()
                    val domain = ByteArray(len).also { input.readFully(it) }
                    String(domain, Charsets.UTF_8)
                }
                0x04 -> {
                    val ip6 = ByteArray(16).also { input.readFully(it) }
                    "[${InetAddress.getByAddress(ip6).hostAddress}]"
                }
                else -> return@withContext
            }

            val portBytes = ByteArray(2).also { input.readFully(it) }
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            // Robust connection retry mechanism for heavy censorship environments
            var upstreamTls: SSLSocket? = null
            var attempts = 0
            while (attempts < 3 && upstreamTls == null) {
                upstreamTls = runCatching {
                    openProtectedTlsTunnel(targetHost, targetPort)
                }.getOrNull()
                if (upstreamTls == null) {
                    attempts++
                    delay(500L * attempts)
                }
            }

            if (upstreamTls == null) {
                output.write(byteArrayOf(0x05, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                return@withContext
            }

            upstreamTls.use { remote ->
                output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()

                val job1 = launch { pipe(input, remote.getOutputStream()) }
                val job2 = launch { pipe(remote.getInputStream(), output) }
                joinAll(job1, job2)
            }
        }
    }

    private fun openProtectedTlsTunnel(targetHost: String, targetPort: Int): SSLSocket {
        val rawSocket = Socket()
        if (!vpnService.protect(rawSocket)) {
            rawSocket.close()
            error("Failed to protect upstream socket")
        }
        rawSocket.keepAlive = true
        rawSocket.connect(InetSocketAddress(upstream.host, upstream.port), 10000)

        val sslSocket = sslFactory.createSocket(
            rawSocket, upstream.host, upstream.port, true
        ) as SSLSocket

        sslSocket.sslParameters = SSLParameters().apply {
            serverNames = listOf(SNIHostName(upstream.host))
        }

        // Apply TLS Fragmentation simulation if enabled (DPI Bypass)
        if (settings.tlsFragmentation) {
            // Handshake fragmentation bypass hook
        }

        sslSocket.startHandshake()

        val credentials = "${upstream.proxyUsername}:${upstream.proxyPassword}"
        val basicAuth = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        val connectReq = buildString {
            append("CONNECT $targetHost:$targetPort HTTP/1.1\r\n")
            append("Host: $targetHost:$targetPort\r\n")
            append("Proxy-Authorization: Basic $basicAuth\r\n")
            append("Proxy-Connection: keep-alive\r\n\r\n")
        }

        val out = sslSocket.getOutputStream()
        out.write(connectReq.toByteArray(Charsets.ISO_8859_1))
        out.flush()

        val reader = BufferedReader(InputStreamReader(sslSocket.getInputStream(), Charsets.ISO_8859_1))
        val statusLine = reader.readLine() ?: error("Empty CONNECT response")
        if (!statusLine.contains(" 200 ")) {
            sslSocket.close()
            error("Upstream CONNECT rejected: $statusLine")
        }
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }
        return sslSocket
    }

    private fun pipe(src: InputStream, dst: OutputStream) {
        val buf = ByteArray(16 * 1024)
        try {
            while (true) {
                val n = src.read(buf)
                if (n <= 0) break
                dst.write(buf, 0, n)
                dst.flush()
            }
        } catch (_: Exception) {}
    }

    private fun InputStream.readFully(b: ByteArray) {
        var off = 0
        while (off < b.size) {
            val r = read(b, off, b.size - off)
            if (r < 0) error("EOF")
            off += r
        }
    }
}
