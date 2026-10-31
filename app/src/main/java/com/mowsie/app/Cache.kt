package com.mowsie.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.content.FileProvider
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.HapticFeedbackConstants
import java.io.File
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.min


internal fun ControlPanel.RefreshPublicQr() {
        // Retain only the Ed25519 PUBLIC key so the QR can be prepared locally.
        // Its visibility is independently gated: no QR on Threshold, during
        // ordinary chamber changes, or on the automatic unlock/reveal.
        val PublicPrefs = getSharedPreferences("MowsiePublicIdentity", android.content.Context.MODE_PRIVATE)
        val Key = ActivePublicKeyHex ?: PublicPrefs.getString("PublicKey", null)
        if (ActivePublicKeyHex != null) PublicPrefs.edit()
            .putString("PublicKey", ActivePublicKeyHex).apply()
        val Qr = findViewById<MowsieQrView>(R.id.qrPattern)
        Qr.visibility = if (Authenticated && ExteriorQrExposed && Key != null)
            android.view.View.VISIBLE else android.view.View.GONE
        if (Key == null) return
        val Name = findViewById<TextView>(R.id.identityName).text.toString()
        val Payload = if (Key == null) {
            "MOWSIE CONTACT V1\nUnlock to show public key"
        } else {
            // This is a REAL deterministic Ed25519 public key, but not yet a Salt address.
            "MOWSIE PUBLIC KEY V1\n" +
                "Algorithm: Ed25519\n" +
                "Derivation: m/0'/0'\n" +
                "Stash: $Name\n" +
                "Public Key: $Key"
        }
        try { findViewById<MowsieQrView>(R.id.qrPattern).SetPayload(Payload) }
        catch (_: WriterException) {
            Toast.makeText(this, "Unable to generate public key QR", Toast.LENGTH_LONG).show()
        }
    }

    /** Copy an image, not a string. The PNG contains only the encoded QR and its
     * quiet zone, with the current visual color, never the on-screen Copied text. */
internal fun ControlPanel.CopyPublicQrImage(Qr: MowsieQrView): Boolean {
        val Image = Qr.MakeClipboardBitmap() ?: return false
        return try {
            val Folder = File(cacheDir, "qrcopies").apply { mkdirs() }
            val Filename = "MowsieQr${System.currentTimeMillis()}.png"
            val FileOut = File(Folder, Filename)
            FileOut.outputStream().use { Stream ->
                if (!Image.compress(Bitmap.CompressFormat.PNG, 100, Stream)) {
                    throw java.io.IOException("PNG encoding failed")
                }
            }
            val Uri = FileProvider.getUriForFile(this, "$packageName.qrfiles", FileOut)
            val Clip = ClipData.newUri(contentResolver, "Mowsie public QR", Uri)
            val Clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            Clipboard.setPrimaryClip(Clip)
            Qr.OnCopySucceeded?.invoke()
            true
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to copy QR image", Toast.LENGTH_SHORT).show()
            false
        } finally {
            Image.recycle()
        }
    }


/**
 * Crisp ZXing QR modules, rendered independently of the Stash's two colors.
 * Tap is passive. Hold then drag adjusts HSL color without copying.
 * A stationary long-press still copies the public QR as a PNG.
 * The BitMatrix and encoded public identity never change during color adjustment.
 */
class MowsieQrView @JvmOverloads constructor(
    Context: Context,
    Attributes: AttributeSet? = null,
    Style: Int = 0
) : View(Context, Attributes, Style) {
    private var CurrentMatrix: BitMatrix? = null
    // QR styling is separate from Primary/Secondary and never changes the
    // encoded public identity. Restore the last chosen appearance on startup.
    private val QrAppearance = Context.getSharedPreferences(
        "MowsieQrAppearance", android.content.Context.MODE_PRIVATE)
    private val Saturation = 0.88f
    private var Hue = QrAppearance.getFloat("Hue", 270f)
    private var Lightness = QrAppearance.getFloat("Lightness", 1f).coerceIn(0f, 1f)
    private var StartingWhite = QrAppearance.getBoolean("StartingWhite", true)
    private var ModuleColor: Int = if (StartingWhite) Color.WHITE else
        ColorUtils.HSLToColor(floatArrayOf(Hue, Saturation, Lightness))
    var OnCopyRequest: (() -> Boolean)? = null
    var OnCopySucceeded: (() -> Unit)? = null

    private val ModulePaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }
    private var Pressed = false
    private var Holding = false
    private var Dragged = false
    private var StartX = 0f
    private var StartY = 0f
    private var FingerX = 0f
    private var FingerY = 0f
    private var LastX = 0f
    private var LastY = 0f
    private val Slop = ViewConfiguration.get(Context).scaledTouchSlop
    private val HoldRunnable = Runnable {
        if (Pressed) {
            Holding = true
            LastX = FingerX
            LastY = FingerY
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    @Throws(WriterException::class)
    fun SetPayload(Payload: String) {
        val Hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4
        )
        CurrentMatrix = QRCodeWriter().encode(Payload, BarcodeFormat.QR_CODE, 0, 0, Hints)
        invalidate()
    }

    private fun DragColor(Dx: Float, Dy: Float) {
        if (kotlin.math.abs(Dx) < 0.01f && kotlin.math.abs(Dy) < 0.01f) return
        // White has no hue. First horizontal adjustment reveals a saturated
        // color, while vertical motion still spans pure white down to black.
        if (StartingWhite && kotlin.math.abs(Dx) > 0.01f) {
            Lightness = 0.62f
            StartingWhite = false
        }
        if (kotlin.math.abs(Dy) > 0.01f) StartingWhite = false
        Hue = ((Hue + Dx * 360f / width.coerceAtLeast(1)) % 360f + 360f) % 360f
        Lightness = (Lightness - Dy * 1.40f / height.coerceAtLeast(1)).coerceIn(0f, 1f)
        ModuleColor = ColorUtils.HSLToColor(floatArrayOf(Hue, Saturation, Lightness))
        invalidate()
    }

    override fun onTouchEvent(Event: MotionEvent): Boolean {
        when (Event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                Pressed = true
                Holding = false
                Dragged = false
                StartX = Event.x
                StartY = Event.y
                FingerX = Event.x
                FingerY = Event.y
                LastX = Event.x
                LastY = Event.y
                removeCallbacks(HoldRunnable)
                postDelayed(HoldRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!Pressed) return false
                FingerX = Event.x
                FingerY = Event.y
                if (Holding) {
                    if (kotlin.math.abs(FingerX - StartX) > Slop ||
                        kotlin.math.abs(FingerY - StartY) > Slop) Dragged = true
                    DragColor(FingerX - LastX, FingerY - LastY)
                    LastX = FingerX
                    LastY = FingerY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!Pressed) return false
                removeCallbacks(HoldRunnable)
                val WasHolding = Holding
                Pressed = false
                Holding = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (WasHolding) {
                    // Save only on release, never continuously while the
                    // finger moves. The very next app launch uses this color.
                    QrAppearance.edit()
                        .putFloat("Hue", Hue)
                        .putFloat("Lightness", Lightness)
                        .putBoolean("StartingWhite", StartingWhite)
                        .apply()
                    // Drag changes color; only a stationary hold requests a copy.
                    if (!Dragged) performLongClick()
                } else if (kotlin.math.abs(Event.x - StartX) <= Slop &&
                    kotlin.math.abs(Event.y - StartY) <= Slop) {
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(HoldRunnable)
                Pressed = false
                Holding = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(Event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun performLongClick(): Boolean {
        return OnCopyRequest?.invoke() ?: super.performLongClick()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(HoldRunnable)
        Pressed = false
        Holding = false
        super.onDetachedFromWindow()
    }

    /** High-resolution PNG with a dark background and the matrix's quiet zone.
     * Draw directly from the BitMatrix so no text or focus overlay is exported. */
    fun MakeClipboardBitmap(): Bitmap? {
        val Matrix = CurrentMatrix ?: return null
        val Cell = (960 / Matrix.width).coerceAtLeast(1)
        val Padding = Cell * 2
        val Side = Matrix.width * Cell
        val Size = Side + Padding * 2
        val BitmapOut = Bitmap.createBitmap(Size, Size, Bitmap.Config.ARGB_8888)
        val CanvasOut = Canvas(BitmapOut)
        CanvasOut.drawColor(Color.parseColor("#0C1521"))
        ModulePaint.color = ModuleColor
        for (Y in 0 until Matrix.height) {
            for (X in 0 until Matrix.width) {
                if (Matrix.get(X, Y)) {
                    val Left = Padding + X * Cell
                    val Top = Padding + Y * Cell
                    CanvasOut.drawRect(Left.toFloat(), Top.toFloat(),
                        (Left + Cell).toFloat(), (Top + Cell).toFloat(), ModulePaint)
                }
            }
        }
        return BitmapOut
    }

    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        val Matrix = CurrentMatrix ?: return
        val AvailableWidth = width - paddingLeft - paddingRight
        val AvailableHeight = height - paddingTop - paddingBottom
        // Old integer cell division could waste dozens of pixels INSIDE the
        // QR view, making every size increase look ineffective. Fit the full
        // QR matrix, including ZXing's built-in four-module quiet zone, into
        // the measured square. Round boundaries to pixels to avoid hairline
        // seams without deforming or cropping any QR module.
        val Cell = min(AvailableWidth, AvailableHeight).toFloat() / Matrix.width
        if (Cell < 1f) return
        val Side = Cell * Matrix.width
        val Left = paddingLeft + (AvailableWidth - Side) * 0.5f
        val Top = paddingTop + (AvailableHeight - Side) * 0.5f
        ModulePaint.color = ModuleColor
        for (Y in 0 until Matrix.height) {
            for (X in 0 until Matrix.width) {
                if (Matrix.get(X, Y)) {
                    val X0 = kotlin.math.round(Left + X * Cell)
                    val Y0 = kotlin.math.round(Top + Y * Cell)
                    val X1 = kotlin.math.round(Left + (X + 1) * Cell)
                    val Y1 = kotlin.math.round(Top + (Y + 1) * Cell)
                    Canvas.drawRect(X0, Y0, X1, Y1, ModulePaint)
                }
            }
        }
    }
}
