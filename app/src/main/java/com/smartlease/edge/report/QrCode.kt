package com.smartlease.edge.report

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * The findings digest as a QR symbol.
 *
 * The point of this is not decoration. `FindingsDigest` already prints a 64-character hex
 * SHA-256 on every page, and nobody has ever verified a 64-character hex string by eye. A
 * QR makes the same commitment *checkable*: the other party scans it with their own phone's
 * stock camera app, no install, no network, and holds the digest in their own hand. Change
 * one finding, regenerate, and the code is visibly different.
 *
 * The payload is the **bare lowercase hex digest** — not a URL, not a custom scheme.
 * A URL would imply a server and would make a stock camera app offer to open a page that
 * does not exist; a custom scheme would need an app to interpret it. Lowercase (rather than
 * the uppercase QR alphanumeric charset, which would produce a slightly smaller symbol) so
 * that the scanned string is byte-identical to the hex printed underneath it and the two can
 * be compared without case-folding.
 */
object QrCode {

    /** Error correction M — ~15% recovery. Enough for a phone screen or a printed page. */
    private val HINTS = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )

    /**
     * Pure — no Android types — so the encode/decode round-trip is unit-testable on the JVM.
     * @param sizePx requested square size; zxing rounds up to a whole number of modules.
     */
    fun matrix(text: String, sizePx: Int): BitMatrix =
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, HINTS)

    /** Black-on-white bitmap for Compose and for the PDF canvas. */
    fun bitmap(text: String, sizePx: Int): Bitmap {
        val m = matrix(text, sizePx)
        val w = m.width
        val h = m.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                pixels[row + x] = if (m.get(x, y)) BLACK else WHITE
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}
