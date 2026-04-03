package com.satsvoucher.bridge

import android.content.Context
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.util.Log
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

    // Queue for poll — tag is placed here when discovered
    private val tagQueue = LinkedBlockingQueue<Tag>(1)

    // Last seen tag — kept after poll so writeToTag can use it
    // poll() removes from queue but we preserve the Tag object here
    private var lastTag: Tag? = null

    fun isAvailable(): Boolean = nfcAdapter?.isEnabled == true

    // Called by BridgeService when a tag is discovered
    fun onTagDiscovered(tag: Tag) {
        tagQueue.clear()
        lastTag = tag
        tagQueue.offer(tag)
        Log.d(TAG, "Tag queued: ${bytesToHex(tag.id)}")
    }

    // ── Poll ──────────────────────────────────────────────────────────────────
    // Blocks until a tag arrives or timeout.
    // Tag is removed from queue but preserved in lastTag for subsequent write.
    fun pollForTag(timeoutMs: Long): NfcTagResult? {
        tagQueue.clear()
        lastTag = null
        val tag = tagQueue.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return null
        lastTag = tag  // preserve for writeToTag
        val uid = bytesToHex(tag.id)
        val techList = tag.techList.joinToString(", ") { it.substringAfterLast('.') }
        Log.i(TAG, "Poll result: uid=$uid techs=$techList")
        return NfcTagResult(uid = uid, tagType = techList)
    }

    // ── Write ─────────────────────────────────────────────────────────────────
    // Writes a URI NDEF record to lastTag.
    // Uses lastTag (preserved after poll) — coin must stay on reader.
    // URI record causes phone to auto-open browser on tap.
    fun writeToTag(uid: String, payload: String): Boolean {
        val tag = lastTag ?: run {
            Log.w(TAG, "No tag available — coin lifted before write?")
            return false
        }

        val tagUid = bytesToHex(tag.id)
        // Compare case-insensitively — poll returns uppercase, caller may vary
        if (tagUid.uppercase() != uid.uppercase()) {
            Log.w(TAG, "UID mismatch: tag=$tagUid requested=$uid")
            return false
        }

        return try {
            val record = NdefRecord.createUri(Uri.parse(payload))
            val message = NdefMessage(arrayOf(record))

            // Try NDEF first (tag already formatted — NTAG424 is formatted out of box)
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    Log.e(TAG, "Tag is read-only")
                    ndef.close()
                    return false
                }
                val messageSize = message.toByteArray().size
                if (messageSize > ndef.maxSize) {
                    Log.e(TAG, "Message too large: $messageSize bytes, max ${ndef.maxSize}")
                    ndef.close()
                    return false
                }
                ndef.writeNdefMessage(message)
                ndef.close()
                Log.i(TAG, "URI NDEF write OK to $tagUid — ${message.toByteArray().size} bytes")
                true
            } else {
                // Tag is unformatted — format and write
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    formatable.connect()
                    formatable.format(message)
                    formatable.close()
                    Log.i(TAG, "NdefFormatable URI write OK to $tagUid")
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
