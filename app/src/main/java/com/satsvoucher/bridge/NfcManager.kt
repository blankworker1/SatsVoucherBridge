package com.satsvoucher.bridge

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.util.Log
import java.nio.charset.Charset
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class NfcManager(private val context: Context) {

    companion object {
        private const val TAG = "NfcManager"
    }

    private val nfcAdapter: NfcAdapter? by lazy {
        val manager = context.getSystemService(Context.NFC_SERVICE) as? android.nfc.NfcManager
        manager?.defaultAdapter
    }

    // Queue that BridgeService pushes tags into when discovered
    private val tagQueue = LinkedBlockingQueue<Tag>(1)

    fun isAvailable(): Boolean = nfcAdapter?.isEnabled == true

    // Called by BridgeService when a tag is scanned
    fun onTagDiscovered(tag: Tag) {
        tagQueue.clear()
        tagQueue.offer(tag)
        Log.d(TAG, "Tag queued: ${bytesToHex(tag.id)}")
    }

    // ── Poll ──────────────────────────────────────────────────────────────────
    // Blocks until a tag arrives or timeout. Called from PrintServer handler thread.

    fun pollForTag(timeoutMs: Long): NfcTagResult? {
        tagQueue.clear() // discard any stale tag from before the poll started
        val tag = tagQueue.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return null

        val uid = bytesToHex(tag.id)
        val techList = tag.techList.joinToString(", ") { it.substringAfterLast('.') }

        return NfcTagResult(uid = uid, tagType = techList)
    }

    // ── Write ─────────────────────────────────────────────────────────────────
    // Writes a text NDEF record to the most recently seen tag matching uid.

    fun writeToTag(uid: String, payload: String): Boolean {
        val tag = tagQueue.peek() ?: run {
            Log.w(TAG, "No tag in queue to write to")
            return false
        }

        val tagUid = bytesToHex(tag.id)
        if (tagUid != uid.uppercase()) {
            Log.w(TAG, "UID mismatch: expected $uid got $tagUid")
            return false
        }

        return try {
            val record = NdefRecord.createTextRecord("en", payload)
            val message = NdefMessage(arrayOf(record))

            // Try NDEF first (tag already formatted)
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                ndef.writeNdefMessage(message)
                ndef.close()
                Log.i(TAG, "NDEF write OK to $tagUid")
                true
            } else {
                // Tag is unformatted — format and write
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    formatable.connect()
                    formatable.format(message)
                    formatable.close()
                    Log.i(TAG, "NdefFormatable write OK to $tagUid")
                    true
                } else {
                    Log.e(TAG, "Tag does not support NDEF")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Write failed: ${e.message}", e)
            false
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }
}

data class NfcTagResult(
    val uid: String,
    val tagType: String
)
