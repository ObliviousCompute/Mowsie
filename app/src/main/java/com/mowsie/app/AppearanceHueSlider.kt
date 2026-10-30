package com.mowsie.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/**
 * One substantial, borderless color rail. All three thumbs share the
 * appearance's tertiary pigment while their positions select independent hues.
 * Uses Canvas only, so it adds no assets, libraries, or standard Android chrome.
 */
class AppearanceHueSlider @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null
) : View(Context, Attributes) {
    private val Density = resources.displayMetrics.density
    private fun Dp(Value: Float) = Value * Density
    private val Track = RectF()
    private val TrackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val Outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ECF2F8")
        style = Paint.Style.STROKE
        strokeWidth = Dp(2f)
    }
    private val Thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val KnobShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B900030A")
        style = Paint.Style.FILL
    }

    var Hue: Float = 0f
        set(Value) {
            field = ((Value % 360f) + 360f) % 360f
            invalidate()
        }
    var ThumbColor: Int = Color.parseColor("#E8B567")
        set(Value) { field = Value; invalidate() }
    var OnHueChanged: ((Float) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = false
        minimumHeight = Dp(45f).toInt()
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(W: Int, H: Int, OldW: Int, OldH: Int) {
        super.onSizeChanged(W, H, OldW, OldH)
        val Side = Dp(19f)
        Track.set(Side, H / 2f - Dp(7.5f), max(Side + 1f, W - Side), H / 2f + Dp(7.5f))
        TrackPaint.shader = LinearGradient(
            Track.left, 0f, Track.right, 0f,
            intArrayOf(
                Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                Color.BLUE, Color.MAGENTA, Color.RED
            ), null, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        val Radius = Dp(7.5f)
        Canvas.drawRoundRect(Track, Radius, Radius, TrackPaint)
        val X = Track.left + Track.width() * (Hue / 360f)
        val Y = height / 2f
        Canvas.drawCircle(X, Y + Dp(1.4f), Dp(14f), KnobShadow)
        Thumb.color = ThumbColor
        Canvas.drawCircle(X, Y, Dp(12f), Thumb)
        Canvas.drawCircle(X, Y, Dp(12f), Outline)
    }

    override fun onTouchEvent(Event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (Event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                UpdateFromTouch(Event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> { UpdateFromTouch(Event.x); return true }
            MotionEvent.ACTION_UP -> {
                UpdateFromTouch(Event.x)
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(Event)
    }

    private fun UpdateFromTouch(X: Float) {
        if (Track.width() <= 0f) return
        Hue = ((X - Track.left) / Track.width()).coerceIn(0f, 1f) * 359.99f
        OnHueChanged?.invoke(Hue)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
