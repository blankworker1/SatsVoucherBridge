package com.satsvoucher.bridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.sunmi.printerx.PrinterSdk
import com.sunmi.printerx.api.LineApi
import com.sunmi.printerx.enums.Align
import com.sunmi.printerx.style.BaseStyle
import com.sunmi.printerx.style.BitmapStyle
import com.sunmi.printerx.style.TextStyle
import com.sunmi.printerx.enums.DividingLine

class PrinterManager(private val context: Context) {

    companion object {
        private const val TAG = "PrinterManager"
        private const val LINE_WIDTH = 32
    }

    private var printer: PrinterSdk.Printer? = null
    private var connected = false

    fun connect() {
        try {
            PrinterSdk.getInstance().getPrinter(context, object : PrinterSdk.PrinterListen {
                override fun onDefPrinter(p: PrinterSdk.Printer) {
                    printer = p
                    connected = true
                    Log.i(TAG, "Printer service connected via PrinterX SDK")
                }
                override fun onPrinters(printers: List<PrinterSdk.Printer>) {
                    Log.d(TAG, "Available printers: ${printers.size}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}")
        }
    }

    fun disconnect() {
        try { PrinterSdk.getInstance().destroy() } catch (_: Exception) {}
        printer = null
        connected = false
    }

    fun isConnected(): Boolean = connected && printer != null

    fun getRawService(): Any? = printer

    // ── Print receipt ─────────────────────────────────────────────────────────

    fun print(job: PrintJob) {
        val p = printer ?: throw IllegalStateException("Printer not connected")
        val lineApi: LineApi = p.lineApi()

        // Init — clears state and sets defaults
        lineApi.initLine(BaseStyle.getStyle().setAlign(Align.CENTER))

        // Store name
        lineApi.printText(
            "${job.storeName}\n",
            TextStyle.getStyle().setAlign(Align.CENTER).setTextSize(28).enableBold(true)
        )

        if (job.headerLine.isNotBlank()) {
            lineApi.printText(
                "${job.headerLine}\n",
                TextStyle.getStyle().setAlign(Align.CENTER).setTextSize(22).enableBold(true)
            )
        }

        lineApi.printText("${divider()}\n", TextStyle.getStyle().enableBold(true))

        // Amount
        lineApi.printText(
            "${job.amount}\n",
            TextStyle.getStyle().setAlign(Align.CENTER).setTextSize(36).enableBold(true)
        )
        lineApi.printText(
            "${job.btcAmount}\n",
            TextStyle.getStyle().setAlign(Align.CENTER).setTextSize(25).enableBold(true)
        )

        // Details
        lineApi.printText("${divider()}\n", TextStyle.getStyle().setAlign(Align.LEFT))
        lineApi.printText(
            "${twoCol("Voucher ID:", job.voucherId)}\n",
            TextStyle.getStyle().setAlign(Align.LEFT).setTextSize(20).enableBold(true)
        )
        lineApi.printText(
            "${twoCol("Issued:", job.issuedDate)}\n",
            TextStyle.getStyle().setAlign(Align.LEFT).setTextSize(20).enableBold(true)
        )
        lineApi.printText(
            "${twoCol("Expires:", job.expiryDate)}\n",
            TextStyle.getStyle().setAlign(Align.LEFT).setTextSize(20).enableBold(true)
        )
        lineApi.printText("${divider()}\n", TextStyle.getStyle().setAlign(Align.LEFT))

        // QR code as bitmap
        if (job.qrData.isNotBlank()) {
            printQrBitmap(lineApi, job.qrData)
        }

        lineApi.printText(
            "\nScan to check or redeem\n",
            TextStyle.getStyle().setAlign(Align.CENTER).setTextSize(18)
        )
        lineApi.printText("${divider()}\n", TextStyle.getStyle())

        if (job.footerLine.isNotBlank()) {
            lineApi.printText(
                "${job.footerLine}\n",
                TextStyle.getStyle().setAlign(Align.CENTER).setTextSize(16)
            )
        }

        // autoOut() flushes and prints everything — this is the key call
        lineApi.printDividingLine(com.sunmi.printerx.enums.DividingLine.EMPTY, 80)
        lineApi.autoOut()

        Log.d(TAG, "print() completed successfully")
    }

    // ── QR bitmap ─────────────────────────────────────────────────────────────

    private fun printQrBitmap(lineApi: LineApi, data: String, sizePx: Int = 280) {
        try {
            Log.d(TAG, "Generating QR bitmap for: $data")
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
            )
            val matrix = QRCodeWriter().encode(
                data, BarcodeFormat.QR_CODE, sizePx, sizePx, hints
            )
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            lineApi.printBitmap(
                bmp,
                BitmapStyle.getStyle().setAlign(Align.CENTER).setWidth(280).setHeight(280)
            )
            Log.d(TAG, "QR bitmap printed OK")
        } catch (e: Exception) {
            Log.e(TAG, "QR bitmap failed: ${e.message}", e)
            lineApi.printText("${data}\n", TextStyle.getStyle().setTextSize(14))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun divider() = "-".repeat(LINE_WIDTH)

    private fun twoCol(left: String, right: String): String {
        val gap = LINE_WIDTH - left.length - right.length
        return if (gap > 0) left + " ".repeat(gap) + right
        else "$left $right"
    }
}
