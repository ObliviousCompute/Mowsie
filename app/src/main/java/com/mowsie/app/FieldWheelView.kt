package com.mowsie.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A quiet, tactile field wheel. Text only: balances stay engraved on the lower door.
 * Drag vertically to roll; tapping an off-center item focuses that field.
 */
class FieldWheelView @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null
) : View(Context, Attributes) {

    data class Field(val Name: String, val Balance: String)

    val Fields = listOf(
        Field("Tia's Tacos", "128,400.00 Salt"),
        Field("Sally's Coffee", "2,750.20 Salt"),
        Field("Bob's Donuts", "10,485.75 Salt"),
        Field("In-N-Out Burger", "8,430.50 Salt"),
        Field("City Wok", "3,750.00 Salt"),
        Field("Joe's FroYo", "1,234.25 Salt")
    )

    var OnFieldChanged: ((Field) -> Unit)? = null
    var OnCenterTapped: (() -> Unit)? = null

    private val D = resources.displayMetrics.density
    private val FontScale = resources.displayMetrics.scaledDensity
    private val Spacing = 57f * D
    private val TouchSlop = ViewConfiguration.get(Context).scaledTouchSlop
    var FieldInk: Int = Color.parseColor("#B3D0BF")
        set(Value) { field = Value; invalidate() }
    private val Brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }
    private val CenterGlow = Paint(Paint.ANTI_ALIAS_FLAG)

    private var Position = 2f
    private var Selected = 2
    private var LastY = 0f
    private var DownY = 0f
    private var Moved = false
    private var Snap: ValueAnimator? = null
    private var Velocity: VelocityTracker? = null

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Field wheel. Swipe up or down to select a field."
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun SetPosition(Value: Float) {
        Position = Value.coerceIn(0f, (Fields.size - 1).toFloat())
        val Nearest = Position.roundToInt().coerceIn(0, Fields.lastIndex)
        if (Nearest != Selected) {
            Selected = Nearest
            OnFieldChanged?.invoke(Fields[Selected])
            contentDescription = "Field wheel, ${Fields[Selected].Name}. Swipe to change field."
        }
        postInvalidateOnAnimation()
    }

    fun SelectField(Index: Int) {
        Snap?.cancel()
        SetPosition(Index.coerceIn(0, Fields.lastIndex).toFloat())
    }

    private fun Settle(Target: Float) {
        Snap?.cancel()
        val End = Target.coerceIn(0f, Fields.lastIndex.toFloat()).roundToInt().toFloat()
        Snap = ValueAnimator.ofFloat(Position, End).apply {
            duration = 245L
            interpolator = DecelerateInterpolator()
            addUpdateListener { SetPosition(it.animatedValue as Float) }
            start()
        }
    }

    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        if (width <= 0 || height <= 0) return
        val W = width.toFloat()
        val H = height.toFloat()
        val Middle = H * 0.5f
        CenterGlow.shader = LinearGradient(
            0f, Middle - 40f * D, 0f, Middle + 40f * D,
            intArrayOf(Color.TRANSPARENT, Color.argb(19, 112, 152, 195), Color.TRANSPARENT),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP
        )
        Canvas.drawRect(0f, Middle - 40f * D, W, Middle + 40f * D, CenterGlow)
        CenterGlow.shader = null

        Fields.forEachIndexed { Index, Field ->
            val Distance = Index - Position
            val Y = Middle + Distance * Spacing
            if (Y < -50f * D || Y > H + 50f * D) return@forEachIndexed
            val Visibility = (1f - abs(Distance) * .22f).coerceIn(.08f, 1f)
            val Focus = (1f - abs(Distance)).coerceIn(0f, 1f)
            Brush.color = FieldInk
            PhosphorInk.Apply(Brush, FieldInk, D)
            Brush.alpha = ((76f + Focus * 176f) * Visibility).toInt().coerceIn(0, 255)
            Brush.textSize = (17.5f + Focus * 5f) * FontScale
            Brush.typeface = android.graphics.Typeface.create("sans-serif",
                if (Focus > .6f) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            val Metrics = Brush.fontMetrics
            val BaseLine = Y - (Metrics.ascent + Metrics.descent) / 2f
            Canvas.save()
            Canvas.clipRect(6f * D, 0f, W - 6f * D, H)
            Canvas.drawText(Field.Name, W / 2f, BaseLine, Brush)
            Canvas.restore()
        }
    }

    override fun onTouchEvent(Event: MotionEvent): Boolean {
        when (Event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                Snap?.cancel()
                Velocity?.recycle()
                Velocity = VelocityTracker.obtain().also { it.addMovement(Event) }
                DownY = Event.y
                LastY = Event.y
                Moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                Velocity?.addMovement(Event)
                if (abs(Event.y - DownY) > TouchSlop) Moved = true
                if (Moved) SetPosition(Position + (LastY - Event.y) / Spacing)
                LastY = Event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                Velocity?.addMovement(Event)
                Velocity?.computeCurrentVelocity(1000)
                val Speed = Velocity?.yVelocity ?: 0f
                Velocity?.recycle()
                Velocity = null
                if (!Moved) {
                    val Offset = (Event.y - height * .5f) / Spacing
                    if (abs(Offset) < .48f) {
                        OnCenterTapped?.invoke()
                        performClick()
                    } else {
                        Settle(Position + Offset)
                    }
                } else {
                    Settle(Position - Speed / (Spacing * 7f))
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                Velocity?.recycle()
                Velocity = null
                Settle(Position)
                return true
            }
        }
        return super.onTouchEvent(Event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
