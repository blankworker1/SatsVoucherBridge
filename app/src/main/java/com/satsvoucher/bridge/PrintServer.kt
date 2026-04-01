package com.satsvoucher.bridge

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class PrintServer(
    private val printerManager: PrinterManager,
    private val nfcManager: NfcManager
) : NanoHTTPD(PORT) {

    companion object {
        const val PORT = 8765
        private const val TAG = "PrintServer"
    }

    override fun serve(session: IHTTPSession): Response {
        val cors = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type"
        )

        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "").apply {
                cors.forEach { (k, v) -> addHeader(k, v) }
            }
        }

        val path = session.uri
        Log.d(TAG, "${session.method} $path")

        return try {
            val response = when {
                path == "/status" && session.method == Method.GET ->
                    handleStatus()

                path == "/print" && session.method == Method.POST ->
                    handlePrint(session)

                // ── Minimal test: print one line only ──
                path == "/print/test" && session.method == Method.GET ->
                    handlePrintTest()

                path == "/nfc/poll" && session.method == Method.GET ->
                    handleNfcPoll()

                path == "/nfc/write" && session.method == Method.POST ->
                    handleNfcWrite(session)

                else -> errorResponse(404, "Not found: $path")
            }
            response.apply { cors.forEach { (k, v) -> addHeader(k, v) } }
        } catch (e: Exception) {
            Log.e(TAG, "Handler error: ${e.message}", e)
            errorResponse(500, e.message ?: "Internal error").apply {
                cors.forEach { (k, v) -> addHeader(k, v) }
            }
        }
    }

    // ── GET /status ───────────────────────────────────────────────────────────

    private fun handleStatus(): Response {
        val obj = JSONObject().apply {
            put("ok", true)
            put("version", BuildConfig.VERSION_NAME)
            put("printer", printerManager.isConnected())
            put("nfc", nfcManager.isAvailable())
        }
        return jsonResponse(obj)
    }

    // ── GET /print/test ───────────────────────────────────────────────────────
    // Simplest possible print — one line of text, nothing else.
    // Open http://localhost:8765/print/test in Sunmi Chrome to trigger.

    private fun handlePrintTest(): Response {
        Log.d(TAG, "Print test requested")
        return try {
            val job = PrintJob(
                storeName  = "** PRINT TEST OK **",
                headerLine = "",
                amount     = "TEST",
                btcAmount  = "",
                voucherId  = "TEST-001",
                qrData     = "https://satsvoucher-worker.bosaland.workers.dev",
                issuedDate = "01/04/2026",
                expiryDate = "01/07/2026",
                footerLine = "Bridge test successful"
            )
            printerManager.print(job)
            Log.d(TAG, "Print test complete")
            jsonResponse(JSONObject().put("ok", true).put("msg", "Test sent to printer"))
        } catch (e: Exception) {
            Log.e(TAG, "Print test failed: ${e.message}", e)
            errorResponse(500, "Print test failed: ${e.message}")
        }
    }


    // ── POST /print ───────────────────────────────────────────────────────────

    private fun handlePrint(session: IHTTPSession): Response {
        val body = readBody(session)
        val json = JSONObject(body)

        val job = PrintJob(
            storeName  = json.optString("storeName", "Sats VOUCHER"),
            headerLine = json.optString("headerLine", ""),
            amount     = json.optString("amount", ""),
            btcAmount  = json.optString("btcAmount", ""),
            voucherId  = json.optString("voucherId", ""),
            qrData     = json.optString("qrData", ""),
            issuedDate = json.optString("issuedDate", ""),
            expiryDate = json.optString("expiryDate", ""),
            footerLine = json.optString("footerLine", ""),
            qrSizeHint = json.optInt("qrSize", 8)
        )
        Log.d(TAG, "PrintJob received: storeName=${job.storeName} header=${job.headerLine} amount=${job.amount} btc=${job.btcAmount} id=${job.voucherId} issued=${job.issuedDate} expiry=${job.expiryDate} footer=${job.footerLine} qr=${job.qrData.take(30)}")
        printerManager.print(job)
        return jsonResponse(JSONObject().put("ok", true))
    }

    // ── GET /nfc/poll ─────────────────────────────────────────────────────────

    private fun handleNfcPoll(): Response {
        val result = nfcManager.pollForTag(timeoutMs = 10_000)
        return if (result != null) {
            jsonResponse(JSONObject().apply {
                put("ok", true)
                put("uid", result.uid)
                put("type", result.tagType)
            })
        } else {
            jsonResponse(JSONObject().apply {
                put("ok", false)
                put("reason", "timeout")
            })
        }
    }

    // ── POST /nfc/write ───────────────────────────────────────────────────────

    private fun handleNfcWrite(session: IHTTPSession): Response {
        val body = readBody(session)
        val json = JSONObject(body)
        val uid = json.optString("uid")
        val payload = json.optString("payload")

        if (uid.isEmpty() || payload.isEmpty()) {
            return errorResponse(400, "uid and payload are required")
        }

        val success = nfcManager.writeToTag(uid, payload)
        return jsonResponse(JSONObject().apply {
            put("ok", success)
            if (!success) put("reason", "Tag not found or write failed")
        })
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun readBody(session: IHTTPSession): String {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        val buf = ByteArray(contentLength)
        session.inputStream.read(buf, 0, contentLength)
        return String(buf)
    }

    private fun jsonResponse(obj: JSONObject): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", obj.toString())

    private fun errorResponse(status: Int, message: String): Response {
        val httpStatus = Response.Status.values().find { it.requestStatus == status }
            ?: Response.Status.INTERNAL_ERROR
        return newFixedLengthResponse(
            httpStatus,
            "application/json",
            JSONObject().put("ok", false).put("error", message).toString()
        )
    }
}

data class PrintJob(
    val storeName: String,
    val headerLine: String,
    val amount: String,
    val btcAmount: String,
    val voucherId: String,
    val qrData: String,
    val issuedDate: String,
    val expiryDate: String,
    val footerLine: String,
    val qrSizeHint: Int = 8
)