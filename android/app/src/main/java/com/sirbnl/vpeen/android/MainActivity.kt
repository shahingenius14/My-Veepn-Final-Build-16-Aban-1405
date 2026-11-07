package com.sirbnl.vpeen.android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var isConnected = false
    private lateinit var statusCard: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusDetails: TextView
    private lateinit var regionSpinner: Spinner
    private lateinit var protocolSpinner: Spinner
    private lateinit var ipModeSpinner: Spinner
    private lateinit var dohSpinner: Spinner
    private lateinit var tlsFragCheck: CheckBox
    private lateinit var lanSharingCheck: CheckBox
    private lateinit var fcmFixCheck: CheckBox
    private lateinit var customEdgeInput: EditText
    private lateinit var connectBtn: Button
    private lateinit var logTextView: TextView

    private val regions = arrayOf(
        "🇳🇱 Amsterdam, Netherlands (Fastly Edge)",
        "🇺🇸 Virginia, United States",
        "🇬🇧 London, United Kingdom",
        "🇫🇷 Paris, France",
        "🇸🇬 Singapore"
    )

    private val protocols = arrayOf("HTTP/3 (QUIC / MASQUE)", "HTTP/2 (Multiplexed)", "HTTP/1.1 over TLS (Compatibility)")
    private val ipModes = arrayOf("Dual-Stack (Default)", "Prefer IPv4", "Prefer IPv6", "IPv4 Only", "IPv6 Only")
    private val dohProviders = arrayOf("Cloudflare (1.1.1.1)", "Google (8.8.8.8)", "Quad9 (9.9.9.9)", "Custom DoH")

    private val logBuffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 48)
            setBackgroundColor(Color.parseColor("#0B132B"))
        }

        val scrollContainer = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        val innerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val brandTitle = TextView(this).apply {
            text = "⚡ MY VEEPN"
            textSize = 26f
            setTextColor(Color.parseColor("#00F5D4"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        innerLayout.addView(brandTitle)

        val brandSubtitle = TextView(this).apply {
            text = "Next-Gen Censorship-Resistant Shield v2.0.1"
            textSize = 12f
            setTextColor(Color.parseColor("#8D99AE"))
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 24)
        }
        innerLayout.addView(brandSubtitle)

        statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#1C2541"))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        
        statusTitle = TextView(this).apply {
            text = "PROTECTION OFF"
            textSize = 18f
            setTextColor(Color.parseColor("#EF4444"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        statusCard.addView(statusTitle)

        statusDetails = TextView(this).apply {
            text = "Tap connect below to tunnel all system traffic securely."
            textSize = 12f
            setTextColor(Color.parseColor("#8D99AE"))
            setPadding(0, 8, 0, 0)
            gravity = Gravity.CENTER
        }
        statusCard.addView(statusDetails)

        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 28 }
        innerLayout.addView(statusCard, cardParams)

        connectBtn = Button(this).apply {
            text = "CONNECT MY VEEPN"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#00F5D4"))
            setTextColor(Color.parseColor("#0B132B"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setOnClickListener { toggleVpn() }
        }
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            120
        ).apply { bottomMargin = 28 }
        innerLayout.addView(connectBtn, btnParams)

        innerLayout.addView(createSectionHeader("🌍 Server Location"))
        regionSpinner = createStyledSpinner(regions)
        innerLayout.addView(regionSpinner)

        innerLayout.addView(createSectionHeader("🔒 Transport Protocol (FoxyVPN Engine)"))
        protocolSpinner = createStyledSpinner(protocols)
        innerLayout.addView(protocolSpinner)

        innerLayout.addView(createSectionHeader("🌐 IP Protocol Mode"))
        ipModeSpinner = createStyledSpinner(ipModes)
        innerLayout.addView(ipModeSpinner)

        innerLayout.addView(createSectionHeader("🛡️ Bootstrap DNS-over-HTTPS (DoH)"))
        dohSpinner = createStyledSpinner(dohProviders)
        innerLayout.addView(dohSpinner)

        innerLayout.addView(createSectionHeader("⚙️ Advanced Anti-Censorship & Sharing"))

        tlsFragCheck = createStyledCheckbox("Enable TLS Fragmentation (DPI/SNI Bypass)", true)
        innerLayout.addView(tlsFragCheck)

        lanSharingCheck = createStyledCheckbox("LAN Sharing (Bind Proxy to 0.0.0.0)", false)
        innerLayout.addView(lanSharingCheck)

        fcmFixCheck = createStyledCheckbox("FCM Push Notification Redirect (Ports 5228-5230)", true)
        innerLayout.addView(fcmFixCheck)

        innerLayout.addView(createSectionHeader("🔗 Custom Edge Host (Optional)"))
        customEdgeInput = EditText(this).apply {
            hint = "e.g. custom.veepn.edge"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#475569"))
            textSize = 13f
            setBackgroundColor(Color.parseColor("#1C2541"))
            setPadding(24, 20, 24, 20)
        }
        val editParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 24 }
        innerLayout.addView(customEdgeInput, editParams)

        innerLayout.addView(createSectionHeader("📋 Live Connection Diagnostic Logs"))
        logTextView = TextView(this).apply {
            text = "[INIT] My VeePN v2.0.1 Ultimate initialized successfully.\n[READY] Select your region and tap connect.\n"
            textSize = 10f
            setTextColor(Color.parseColor("#00F5D4"))
            setBackgroundColor(Color.parseColor("#10172A"))
            setPadding(20, 20, 20, 20)
        }
        
        val logScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 220).apply {
                bottomMargin = 24
            }
            addView(logTextView)
        }
        innerLayout.addView(logScroll)

        scrollContainer.addView(innerLayout)
        root.addView(scrollContainer)
        setContentView(root)
    }

    private fun createSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(Color.parseColor("#3A86EF"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 16, 0, 6)
        }
    }

    private fun createStyledSpinner(items: Array<String>): Spinner {
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
            setBackgroundColor(Color.parseColor("#1C2541"))
            setPadding(12, 12, 12, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
    }

    private fun createStyledCheckbox(label: String, checked: Boolean): CheckBox {
        return CheckBox(this).apply {
            text = label
            isChecked = checked
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 13f
            setPadding(8, 8, 8, 8)
        }
    }

    private fun appendLog(msg: String) {
        runOnUiThread {
            logBuffer.append("$msg\n")
            logTextView.text = logBuffer.toString()
        }
    }

    private fun toggleVpn() {
        if (isConnected) {
            val stopIntent = Intent(this, VpeenVpnService::class.java).apply { action = "STOP" }
            startService(stopIntent)
            isConnected = false
            statusTitle.text = "PROTECTION OFF"
            statusTitle.setTextColor(Color.parseColor("#EF4444"))
            statusDetails.text = "Tap connect below to tunnel all system traffic securely."
            connectBtn.text = "CONNECT MY VEEPN"
            connectBtn.setBackgroundColor(Color.parseColor("#00F5D4"))
            connectBtn.setTextColor(Color.parseColor("#0B132B"))
            appendLog("[DISCONNECT] Tunnel safely terminated.")
        } else {
            val prepIntent = VpnService.prepare(this)
            if (prepIntent != null) {
                startActivityForResult(prepIntent, 1001)
            } else {
                onActivityResult(1001, Activity.RESULT_OK, null)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            val regionCode = when (regionSpinner.selectedItemPosition) {
                0 -> "nl"
                1 -> "us-east"
                2 -> "uk"
                3 -> "fr"
                4 -> "sg"
                else -> "nl"
            }

            val startIntent = Intent(this, VpeenVpnService::class.java).apply {
                putExtra("REGION", regionCode)
                putExtra("PROTOCOL", protocols[protocolSpinner.selectedItemPosition])
                putExtra("IP_MODE", ipModes[ipModeSpinner.selectedItemPosition])
                putExtra("DOH", dohProviders[dohSpinner.selectedItemPosition])
                putExtra("CUSTOM_EDGE", customEdgeInput.text.toString().trim())
                putExtra("TLS_FRAG", tlsFragCheck.isChecked)
                putExtra("LAN_SHARING", lanSharingCheck.isChecked)
                putExtra("FCM_FIX", fcmFixCheck.isChecked)
            }
            startForegroundService(startIntent)
            isConnected = true
            statusTitle.text = "PROTECTED & SECURE"
            statusTitle.setTextColor(Color.parseColor("#00F5D4"))
            statusDetails.text = "Tunnel Active • Exit IP & Push Notifications Routed"
            connectBtn.text = "DISCONNECT"
            connectBtn.setBackgroundColor(Color.parseColor("#EF4444"))
            connectBtn.setTextColor(Color.WHITE)
            appendLog("[CONNECT] Handshake started for $regionCode via ${protocols[protocolSpinner.selectedItemPosition]}...")
        }
    }
}
