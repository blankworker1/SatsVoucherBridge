# SatsVoucherBridge

**Android bridge APK for the SatsVOUCHER platform.**

Runs as a background service on the Sunmi V2S POS terminal, exposing the built-in thermal printer and NFC reader to the SatsVOUCHER web app via a localhost HTTP API.

Part of the [SatsVOUCHER](https://github.com/blankworker1/SatsVOUCHER) platform.

---

## What It Does

The SatsVOUCHER web app runs in a WebView on the Sunmi terminal. It needs access to the Sunmi's thermal printer and NFC reader — hardware that isn't accessible from a browser directly. This APK bridges that gap.

On launch it:
1. Starts a foreground service that persists in the background
2. Binds to the Sunmi PrinterX SDK for thermal printing
3. Initialises the NFC reader for tag polling and writing
4. Starts a lightweight HTTP server on `localhost:8765`
5. Loads the SatsVOUCHER web app in a full-screen WebView

The web app calls `localhost:8765` for all hardware operations. If the bridge is unreachable the web app continues working without print functionality — useful for browser-based access from any device.

---

## First Launch Setup

On first launch a setup prompt appears asking for your Cloudflare Worker URL.

Enter your deployed Worker URL in the format:

```
https://yourname.workers.dev/app
```

The URL is saved to SharedPreferences and loaded automatically on every subsequent launch. No rebuild required — the APK is generic and works with any SatsVOUCHER Worker deployment.

**To change the URL later:** long-press the back button on the Sunmi and confirm reset. The setup prompt will appear again.

---

## Bridge Endpoints

The HTTP server runs on `localhost:8765` and is only accessible from within the device.

| Method | Route | Description |
|--------|-------|-------------|
| `GET` | `/status` | `{"ok":true,"printer":true,"nfc":true}` |
| `POST` | `/print` | Print receipt from JSON payload |
| `GET` | `/print/test` | Print a test page |
| `GET` | `/nfc/poll` | Long-poll up to 10s for NFC tag tap — returns UID |
| `POST` | `/nfc/write` | Write NDEF payload to tapped NFC tag |

### Print Job JSON

```json
{
  "storeName": "MY STORE",
  "headerLine": "Thank you for your purchase",
  "amount": "€10.00",
  "btcAmount": "0.00017260 BTC",
  "voucherId": "ABC123",
  "qrData": "https://yourname.workers.dev/v/abc123",
  "issuedDate": "01/04/2026",
  "expiryDate": "01/07/2026",
  "footerLine": "Non-refundable. Valid for stated period."
}
```

---

## Hardware

Tested on: **Sunmi V2S** (Android 11, firmware 6.0.30)

- 58mm thermal printer — linerless, no cutter
- Built-in NFC reader
- Portrait locked

The web app layout is optimised for the Sunmi V2S logical resolution (480×854px). The bridge detects automatically — the web app checks `/status` on load and degrades gracefully if unreachable.

---

## Building

**Prerequisites:**
- Android Studio
- JDK 17
- USB debugging enabled on the Sunmi V2S

**Steps:**
1. Clone this repo and open the project in Android Studio
2. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
3. Install via ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No code changes are required before building. The Worker URL is configured at runtime on first launch.

---

## Dependencies

```groovy
implementation 'com.sunmi:printerx:1.0.14'
implementation 'com.google.zxing:core:3.5.2'
implementation 'org.nanohttpd:nanohttpd:2.3.1'
implementation 'androidx.core:core-ktx:1.12.0'
```

**Key technical decisions:**

The bridge uses the **Sunmi PrinterX SDK** (`com.sunmi:printerx:1.0.14`) rather than the older AIDL `IWoyouService` interface. The AIDL interface proved unreliable on firmware 6.0.30 — `printerInit`, `lineWrap`, and `cutPaper` throw security exceptions from third-party apps. The PrinterX SDK is Sunmi's modern replacement and works correctly via `lineApi.autoOut()` to flush and print.

QR codes are generated as ZXing bitmaps in Kotlin and printed via `lineApi.printBitmap()`, bypassing the firmware's built-in QR renderer which crashed on certain input strings.

---

## Project Structure

```
app/src/main/java/com/satsvoucher/bridge/
├── MainActivity.kt        WebView + first launch URL prompt + NFC forwarding
├── BridgeService.kt       Foreground service — starts printer, NFC, HTTP server
├── PrinterManager.kt      PrinterX SDK integration
├── PrintServer.kt         NanoHTTPD HTTP server — routes /print and /nfc/*
└── NfcManager.kt          NFC tag polling and NDEF write

app/src/main/aidl/woyou/aidlservice/jiuiv5/
├── IWoyouService.aidl     Sunmi printer AIDL (kept for reference only)
└── ICallback.aidl
```

---

## Related

- **SatsVOUCHER platform:** [github.com/blankworker1/SatsVOUCHER](https://github.com/blankworker1/SatsVOUCHER)
- **Live platform:** [satsvoucher-worker.bosaland.workers.dev](https://satsvoucher-worker.bosaland.workers.dev)

---

## Licence

MIT
