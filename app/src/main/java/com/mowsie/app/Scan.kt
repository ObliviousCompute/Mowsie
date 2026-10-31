package com.mowsie.app

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Camera
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Camera1 is used intentionally for Android 7+ with no new binary libraries.
 * ZXing core, which Mowsie already uses for QR generation, does the decoding.
 * The preview is always stopped when the chamber is hidden or the app locks. */
@Suppress("DEPRECATION")
internal class ScanPage(private val Host: ControlPanel) : FrameLayout(Host), TextureView.SurfaceTextureListener {
    companion object { const val PermissionCode = 771 }
    var OnRecognized: ((ScanIdentity) -> Unit)? = null
    // A TextureView preserves the camera's actual aspect ratio. Unlike a
    // SurfaceView it can be clipped by this rounded, square viewport on API 24.
    private val Preview = TextureView(Host).apply { surfaceTextureListener = this@ScanPage }
    private val Viewfinder = FrameLayout(Host).apply {
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#122334"))
            cornerRadius = Host.Dp(29).toFloat()
        }
        clipToOutline = true
        isClickable = true
        isFocusable = true
        contentDescription = "Scan a Mowsie identity. Tap to paste a copied QR."
        setOnClickListener { PasteQrImage() }
    }
    private var PreviewWidth = 0
    private var PreviewHeight = 0
    private var CameraDevice: Camera? = null
    private var Shown = false
    private var RotationDegrees = 90
    private var LastDecodeAt = 0L
    private val Busy = AtomicBoolean(false)
    private val Worker = Executors.newSingleThreadExecutor()
    private var Delivered = false
    private val Decoder = MultiFormatReader().apply {
        setHints(mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        ))
    }
    private val Status = PhosphorTextView(Host).apply {
        text = ""
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#93A5B6"))
        isClickable = false
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        Viewfinder.addView(Preview, LayoutParams(-1, -1))
        // One subtle center ring, no square aiming brackets.
        Viewfinder.addView(object : View(Host) {
            private val Ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#9FB9CA")
                alpha = 155
                style = Paint.Style.STROKE
                strokeWidth = Host.Dp(1.5f)
            }
            override fun onDraw(Canvas: Canvas) {
                super.onDraw(Canvas)
                val Radius = minOf(width, height) * 0.23f
                Canvas.drawCircle(width / 2f, height / 2f, Radius, Ring)
            }
        }, LayoutParams(-1, -1))
        addView(Viewfinder, LayoutParams(Host.Dp(260), Host.Dp(260), Gravity.CENTER))
        addView(Status, LayoutParams(-1, Host.Dp(27), Gravity.BOTTOM).apply {
            bottomMargin = Host.Dp(1)
        })
    }

    override fun onSizeChanged(W: Int, H: Int, OldW: Int, OldH: Int) {
        super.onSizeChanged(W, H, OldW, OldH)
        // Preserve a little chamber-blue space on every edge, including for
        // small devices. The scanner processes the full, uncropped frame.
        val Side = minOf(W - Host.Dp(28), H - Host.Dp(42)).coerceAtLeast(Host.Dp(100))
        Viewfinder.layoutParams = LayoutParams(Side, Side, Gravity.CENTER).apply {
            topMargin = -Host.Dp(7)
        }
    }

    private fun FitPreview() {
        if (PreviewWidth <= 0 || PreviewHeight <= 0 || Preview.width == 0 || Preview.height == 0) return
        // Camera1's display orientation rotates the texture for portrait.
        // TextureView otherwise scales it independently in X/Y and stretches it.
        val PhotoW = if (RotationDegrees % 180 == 0) PreviewWidth else PreviewHeight
        val PhotoH = if (RotationDegrees % 180 == 0) PreviewHeight else PreviewWidth
        val ViewAspect = Preview.width.toFloat() / Preview.height.toFloat()
        val SourceAspect = PhotoW.toFloat() / PhotoH.toFloat()
        val ScaleX = if (SourceAspect > ViewAspect) SourceAspect / ViewAspect else 1f
        val ScaleY = if (SourceAspect < ViewAspect) ViewAspect / SourceAspect else 1f
        Preview.setTransform(Matrix().apply {
            setScale(ScaleX, ScaleY, Preview.width / 2f, Preview.height / 2f)
        })
    }

    fun Enter() {
        Shown = true
        Delivered = false
        Status.text = ""
        if (Host.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Status.text = "Camera permission required"
            Host.requestPermissions(arrayOf(Manifest.permission.CAMERA), PermissionCode)
        } else StartCamera()
    }

    fun Exit() {
        Shown = false
        Delivered = false
        StopCamera()
        Status.text = ""
    }

    fun PermissionResult(Granted: Boolean) {
        if (!Shown) return
        if (Granted) { Status.text = ""; StartCamera() }
        else Status.text = "Camera permission denied. Tap to paste a QR."
    }

    override fun onSurfaceTextureAvailable(Texture: SurfaceTexture, W: Int, H: Int) { StartCamera() }
    override fun onSurfaceTextureSizeChanged(Texture: SurfaceTexture, W: Int, H: Int) {
        FitPreview()
    }
    override fun onSurfaceTextureDestroyed(Texture: SurfaceTexture): Boolean {
        StopCamera()
        return true
    }
    override fun onSurfaceTextureUpdated(Texture: SurfaceTexture) {}
    override fun onDetachedFromWindow() {
        Exit()
        Worker.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun StartCamera() {
        if (!Shown || !Preview.isAvailable || !Host.Authenticated || CameraDevice != null ||
            Host.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        try {
            val Back = (0 until Camera.getNumberOfCameras()).firstOrNull { Index ->
                Camera.CameraInfo().also { Camera.getCameraInfo(Index, it) }.facing ==
                    Camera.CameraInfo.CAMERA_FACING_BACK
            } ?: 0
            val Device = Camera.open(Back)
            CameraDevice = Device // Own the camera immediately, including on setup failure.
            val Parameters = Device.parameters
            val Sizes = Parameters.supportedPreviewSizes
            val Pick = Sizes.minByOrNull { kotlin.math.abs(it.width * it.height - 1280 * 720) }
            if (Pick != null) Parameters.setPreviewSize(Pick.width, Pick.height)
            if (Parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                Parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            } else if (Parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                Parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
            }
            Device.parameters = Parameters
            PreviewWidth = Device.parameters.previewSize.width
            PreviewHeight = Device.parameters.previewSize.height
            val Info = Camera.CameraInfo()
            Camera.getCameraInfo(Back, Info)
            val DisplayRotation = when (Host.windowManager.defaultDisplay.rotation) {
                android.view.Surface.ROTATION_90 -> 90
                android.view.Surface.ROTATION_180 -> 180
                android.view.Surface.ROTATION_270 -> 270
                else -> 0
            }
            RotationDegrees = (Info.orientation - DisplayRotation + 360) % 360
            Device.setDisplayOrientation(RotationDegrees)
            // The actual camera stream is uncropped. Only its visible texture
            // is center-cropped to the square, with no geometric distortion.
            Device.setPreviewTexture(Preview.surfaceTexture)
            Preview.post { FitPreview() }
            Device.setPreviewCallback { Bytes, Source ->
                if (!Shown || Delivered || Busy.get() ||
                    android.os.SystemClock.elapsedRealtime() - LastDecodeAt < 180L) return@setPreviewCallback
                LastDecodeAt = android.os.SystemClock.elapsedRealtime()
                if (!Busy.compareAndSet(false, true)) return@setPreviewCallback
                val Size = Source.parameters.previewSize
                val Data = Bytes.copyOf()
                val Width = Size.width
                val Height = Size.height
                val Rotate = RotationDegrees
                Worker.execute {
                    try {
                        val Text = DecodeLuma(Data, Width, Height, Rotate)
                        val Found = Text?.let(ScanProtocol::Recognize)
                        if (Found != null) post { Deliver(Found) }
                    } catch (_: Exception) { /* A non-QR frame is normal. */ }
                    finally { Busy.set(false) }
                }
            }
            Device.startPreview()
        } catch (_: Exception) {
            StopCamera()
            Status.text = "Camera unavailable. Tap to paste a QR."
        }
    }

    private fun StopCamera() {
        val Device = CameraDevice ?: return
        CameraDevice = null
        try { Device.setPreviewCallback(null) } catch (_: Exception) {}
        try { Device.stopPreview() } catch (_: Exception) {}
        try { Device.release() } catch (_: Exception) {}
    }

    private fun DecodeLuma(Data: ByteArray, Width: Int, Height: Int, Angle: Int): String? {
        if (Data.size < Width * Height) return null
        val Luma: ByteArray
        val W: Int
        val H: Int
        when (Angle) {
            90, 270 -> {
                W = Height; H = Width
                Luma = ByteArray(Width * Height)
                for (Y in 0 until Height) for (X in 0 until Width) {
                    val Dest = if (Angle == 90) X * Height + (Height - 1 - Y)
                        else (Width - 1 - X) * Height + Y
                    Luma[Dest] = Data[Y * Width + X]
                }
            }
            180 -> {
                W = Width; H = Height
                Luma = ByteArray(Width * Height) { Data[Width * Height - 1 - it] }
            }
            else -> { W = Width; H = Height; Luma = Data }
        }
        val Source = PlanarYUVLuminanceSource(Luma, W, H, 0, 0, W, H, false)
        for (Variant in listOf(Source, Source.invert())) {
            try {
                val Result = Decoder.decodeWithState(
                    BinaryBitmap(HybridBinarizer(Variant))).text
                return Result
            } catch (_: Exception) { /* Inverted QR codes are normal in Mowsie. */ }
            finally { Decoder.reset() }
        }
        return null
    }

    private fun PasteQrImage() {
        if (!Shown || Delivered) return
        val Clipboard = Host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val Item = Clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        if (Item?.uri == null) { Status.text = "No QR image copied"; return }
        try {
            val Uri = Item.uri
            val Bytes = Host.contentResolver.openInputStream(Uri)?.use { Stream ->
                // A bounded stream is decoded once, below, with downsampling.
                val Image = Stream.readBytes()
                if (Image.size <= 8_000_000) Image else null
            } ?: run { Status.text = "Unable to read QR image"; return }
            val Options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(Bytes, 0, Bytes.size, Options)
            val Scale = maxOf(1, maxOf(Options.outWidth, Options.outHeight) / 1300)
            val Bitmap = BitmapFactory.decodeByteArray(Bytes, 0, Bytes.size,
                BitmapFactory.Options().apply { inSampleSize = Scale })
                ?: run { Status.text = "Unable to read QR image"; return }
            try {
                val Pixels = IntArray(Bitmap.width * Bitmap.height)
                Bitmap.getPixels(Pixels, 0, Bitmap.width, 0, 0, Bitmap.width, Bitmap.height)
                val Source = RGBLuminanceSource(Bitmap.width, Bitmap.height, Pixels)
                for (Variant in listOf(Source, Source.invert())) {
                    try {
                        val Reader = MultiFormatReader().apply {
                            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                        }
                        val Result = Reader.decode(BinaryBitmap(HybridBinarizer(Variant))).text
                        ScanProtocol.Recognize(Result)?.let { Deliver(it); return }
                    } catch (_: Exception) { /* Try the opposite contrast. */ }
                }
                Status.text = "Not a Mowsie public QR"
            } finally { Bitmap.recycle() }
        } catch (_: Exception) { Status.text = "No readable Mowsie QR" }
    }

    private fun Deliver(Identity: ScanIdentity) {
        if (!Shown || Delivered || !Host.Authenticated) return
        Delivered = true
        StopCamera()
        OnRecognized?.invoke(Identity)
    }
}
