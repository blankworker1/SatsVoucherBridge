package com.satsvoucher.bridge

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    companion object {
        private const val TAG = "MainActivity"
        // Change this to your deployed Cloudflare Pages URL
        // During development you can point at a local dev server
        const val WEB_APP_URL = "https://satsvoucher-worker.bosaland.workers.dev/app"
    }

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start the bridge service
        startBridgeService(intent)

        // Full-screen WebView — this IS the UI
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
            loadUrl(WEB_APP_URL)
        }

        setContentView(webView)
    }

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

    override fun onBackPressed() {
        // Back navigates within the web app rather than exiting
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

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
