package com.satsvoucher.bridge

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "sv_prefs"
        private const val KEY_WORKER_URL = "sv_worker_url"
        private const val URL_SUFFIX = "/app"
        private const val URL_PLACEHOLDER = "https://yourworker.workers.dev/app"
        private const val POS_HOST = "pay.blink.sv"
    }

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences
    private lateinit var backOverlay: TextView
    private var nfcAdapter: NfcAdapter? = null
    private var isReaderModeActive = false

    // Reader mode callback — routes tags directly to BridgeService queue
    private val readerModeCallback = NfcAdapter.ReaderCallback { tag: Tag ->
        Log.i(TAG, "ReaderMode tag: ${tag.id.joinToString("") { "%02X".format(it) }}")
        val serviceIntent = Intent(this, BridgeService::class.java).apply {
            putExtra(NfcAdapter.EXTRA_TAG, tag)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        startBridgeService(intent)

        // Build WebView
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    handleUrlChange(url)
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    handleUrlChange(url)
                }
            }
        }

        // Build floating back button overlay — shown only on POS screen
        backOverlay = TextView(this).apply {
            text = "← Back"
            setTextColor(Color.parseColor("#FFD000"))
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            visibility = android.view.View.GONE
            setOnClickListener {
                val savedUrl = prefs.getString(KEY_WORKER_URL, null)
                if (!savedUrl.isNullOrBlank()) {
                    webView.loadUrl(savedUrl)
                }
            }
        }

        // Root layout — WebView fills screen, back button floats top-left
        val root = FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            val overlayParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(dp(12), dp(12), 0, 0)
            }
            addView(backOverlay, overlayParams)
        }

        setContentView(root)

        val savedUrl = prefs.getString(KEY_WORKER_URL, null)
        if (savedUrl.isNullOrBlank()) {
            showUrlPrompt()
        } else {
            webView.loadUrl(savedUrl)
            Log.i(TAG, "Loaded saved Worker URL: $savedUrl")
        }
    }

    // ── URL change handler ────────────────────────────────────────────────────

    private fun handleUrlChange(url: String) {
        val isPosScreen = url.contains(POS_HOST)
        val isSatsCashNfc = url.contains("/satscash/mint") || url.contains("/satscash/redeem")

        // Show/hide floating back button on POS screen
        backOverlay.visibility = if (isPosScreen) android.view.View.VISIBLE else android.view.View.GONE

        // Enable/disable NFC reader mode for SatsCASH NFC screens
        if (isSatsCashNfc) enableReaderMode() else disableReaderMode()

        Log.d(TAG, "URL changed: $url | POS=$isPosScreen | NFC=$isSatsCashNfc")
    }

    // ── NFC Reader Mode ───────────────────────────────────────────────────────

    private fun enableReaderMode() {
        nfcAdapter?.let { adapter ->
            if (!isReaderModeActive) {
                adapter.enableReaderMode(
                    this,
                    readerModeCallback,
                    NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B,
                    null
                )
                isReaderModeActive = true
                Log.i(TAG, "NFC reader mode enabled")
            }
        }
    }

    private fun disableReaderMode() {
        nfcAdapter?.let { adapter ->
            if (isReaderModeActive) {
                adapter.disableReaderMode(this)
                isReaderModeActive = false
                Log.i(TAG, "NFC reader mode disabled")
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        disableReaderMode()
    }

    override fun onResume() {
        super.onResume()
        webView.evaluateJavascript("window.location.href") { url ->
            val cleanUrl = url?.trim('"') ?: ""
            handleUrlChange(cleanUrl)
        }
    }

    // ── URL prompt ────────────────────────────────────────────────────────────

    private fun showUrlPrompt(errorMessage: String? = null) {
        val input = EditText(this).apply {
            hint = URL_PLACEHOLDER
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            textSize = 14f
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
            if (errorMessage != null) {
                addView(TextView(context).apply {
                    text = errorMessage
                    setTextColor(0xFFFF4444.toInt())
                    textSize = 12f
                    setPadding(0, 0, 0, dp(6))
                })
            }
            addView(input)
            addView(TextView(context).apply {
                text = "Example: https://yourname.workers.dev/app"
                setTextColor(0xFF888888.toInt())
                textSize = 11f
                setPadding(0, dp(4), 0, 0)
            })
        }

        AlertDialog.Builder(this)
            .setTitle("Sats VOUCHER Setup")
            .setMessage("Enter your Cloudflare Worker URL to get started.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Connect") { _, _ ->
                handleUrlInput(input.text.toString().trim())
            }
            .show()
    }

    private fun handleUrlInput(url: String) {
        when {
            url.isBlank() -> showUrlPrompt("URL cannot be empty.")
            !url.startsWith("https://") -> showUrlPrompt("URL must start with https://")
            !url.endsWith(URL_SUFFIX) -> showUrlPrompt("URL must end with /app")
            else -> {
                prefs.edit().putString(KEY_WORKER_URL, url).apply()
                webView.loadUrl(url)
                Log.i(TAG, "Worker URL saved and loaded: $url")
            }
        }
    }

    // ── Back button ───────────────────────────────────────────────────────────

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

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

    // ── NFC intent forwarding (normal mode) ───────────────────────────────────

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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
            intent?.extras?.let { putExtras(it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Log.i(TAG, "Bridge service started")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
