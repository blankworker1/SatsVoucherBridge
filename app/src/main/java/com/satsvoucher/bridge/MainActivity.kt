package com.satsvoucher.bridge

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "sv_prefs"
        private const val KEY_WORKER_URL = "sv_worker_url"
        private const val URL_SUFFIX = "/app"
        private const val URL_PLACEHOLDER = "https://yourworker.workers.dev/app"
    }

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Start the bridge service immediately — printer and NFC ready before URL prompt
        startBridgeService(intent)

        // Build the WebView — we load the URL after setup (or after prompt)
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                // Allow fetch to localhost (the bridge)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    // Keep navigation inside the WebView
                    return false
                }
            }
        }

        setContentView(webView)

        // Load saved URL or show first-launch prompt
        val savedUrl = prefs.getString(KEY_WORKER_URL, null)
        if (savedUrl.isNullOrBlank()) {
            showUrlPrompt()
        } else {
            webView.loadUrl(savedUrl)
            Log.i(TAG, "Loaded saved Worker URL: $savedUrl")
        }
    }

    // ── URL prompt ────────────────────────────────────────────────────────────

    private fun showUrlPrompt(errorMessage: String? = null) {
        // Input field
        val input = EditText(this).apply {
            hint = URL_PLACEHOLDER
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            textSize = 14f
            setPadding(24, 16, 24, 16)
        }

        // Container with padding and optional error message
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            if (errorMessage != null) {
                addView(TextView(context).apply {
                    text = errorMessage
                    setTextColor(0xFFFF4444.toInt())
                    textSize = 12f
                    setPadding(0, 0, 0, 12)
                })
            }
            addView(input)
            addView(TextView(context).apply {
                text = "Example: https://yourname.workers.dev/app"
                setTextColor(0xFF888888.toInt())
                textSize = 11f
                setPadding(0, 8, 0, 0)
            })
        }

        AlertDialog.Builder(this)
            .setTitle("Sats VOUCHER Setup")
            .setMessage("Enter your Cloudflare Worker URL to get started.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Connect") { _, _ ->
                val url = input.text.toString().trim()
                handleUrlInput(url)
            }
            .show()
    }

    private fun handleUrlInput(url: String) {
        // Basic validation
        when {
            url.isBlank() -> {
                showUrlPrompt("URL cannot be empty.")
            }
            !url.startsWith("https://") -> {
                showUrlPrompt("URL must start with https://")
            }
            !url.endsWith(URL_SUFFIX) -> {
                showUrlPrompt("URL must end with /app\nExample: https://yourname.workers.dev/app")
            }
            else -> {
                // Save and load
                prefs.edit().putString(KEY_WORKER_URL, url).apply()
                webView.loadUrl(url)
                Log.i(TAG, "Worker URL saved and loaded: $url")
            }
        }
    }

    // ── Long-press back to reset URL ──────────────────────────────────────────

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // Hold back for 2 seconds to trigger URL reset
    private var backPressTime = 0L

    @Deprecated("Deprecated in Java")
    override fun onKeyLongPress(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            showResetConfirmation()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    private fun showResetConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Reset Worker URL?")
            .setMessage("This will clear the saved URL and show the setup screen again.")
            .setPositiveButton("Reset") { _, _ ->
                prefs.edit().remove(KEY_WORKER_URL).apply()
                webView.loadUrl("about:blank")
                showUrlPrompt()
                Log.i(TAG, "Worker URL reset")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── NFC forwarding ────────────────────────────────────────────────────────

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // NFC tag was tapped while app is in foreground — forward to service
        if (intent.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            startBridgeService(intent)
        }
    }

    // ── Service ───────────────────────────────────────────────────────────────

    private fun startBridgeService(intent: Intent?) {
        val serviceIntent = Intent(this, BridgeService::class.java).apply {
            // Forward any NFC extras to the service
            intent?.extras?.let { putExtras(it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Log.i(TAG, "Bridge service started")
    }
}
