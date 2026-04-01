package com.satsvoucher.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import fi.iki.elonen.NanoHTTPD

class BridgeService : Service() {

    companion object {
        private const val TAG = "BridgeService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "satsvoucher_bridge"
    }

    private lateinit var printerManager: PrinterManager
    private lateinit var nfcManager: NfcManager
    private lateinit var printServer: PrintServer

    override fun onCreate() {
        super.onCreate()

        printerManager = PrinterManager(this)
        nfcManager = NfcManager(this)
        printServer = PrintServer(printerManager, nfcManager)

        // Connect to Sunmi printer service
        printerManager.connect()

        //Start the HTTP server
        printServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        Log.i(TAG, "Print server started on port ${PrintServer.PORT}")

        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle NFC tag intents forwarded from MainActivity
        intent?.let {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            tag?.let { t -> nfcManager.onTagDiscovered(t) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        printServer.stop()
        printerManager.disconnect()
        Log.i(TAG, "Bridge service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification (required for foreground service) ────────────────────────

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sats VOUCHER Bridge",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hardware bridge for printer and NFC"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Sats VOUCHER")
            .setContentText("Hardware bridge active on :${PrintServer.PORT}")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setOngoing(true)
            .build()
    }
}
