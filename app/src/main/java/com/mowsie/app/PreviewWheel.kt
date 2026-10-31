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
 * Shared illuminated selection wheel for Peers, Fields and Lanterns. The
 * accent-colored first row scrolls with every other wheel entry.
 * Presentation contents are synthetic until the underlying features exist.
 */
internal open class PreviewWheel @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null
) : View(Context, Attributes) {
    var ActionLabel: String = ""
        set(Value) { field = Value; invalidate() }
    var Entries: List<String> = emptyList()
        set(Value) {
            field = Value
            Position = Position.coerceIn(0f, (Value.size - 1).coerceAtLeast(0).toFloat())
            invalidate()
        }
    var ActionInk: Int = Color.parseColor("#73A9E4")
        set(Value) { field = Value; invalidate() }
    var FieldInk: Int = Color.parseColor("#8B929B")
        set(Value) { field = Value; invalidate() }
    var OnAction: (() -> Unit)? = null
    var OnSelected: ((Int) -> Unit)? = null
    var OnCenterTapped: (() -> Unit)? = null

    protected val D = resources.displayMetrics.density
    private val FontScale = resources.displayMetrics.scaledDensity
    private val Spacing = 57f * D
    private val TouchSlop = ViewConfiguration.get(Context).scaledTouchSlop
    private val Brush = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val CenterGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private var Position = 0f
    private var Selected = 0
    private var LastY = 0f
    private var DownY = 0f
    private var Moved = false
    private var Snap: ValueAnimator? = null
    private var Velocity: VelocityTracker? = null

    init {
        isClickable = true
        isFocusable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        contentDescription = "Selection wheel. Swipe vertically to explore."
    }

    fun SelectIndex(Index: Int, Notify: Boolean = false) {
        Snap?.cancel()
        SetPosition(Index.coerceIn(0, (Entries.size - 1).coerceAtLeast(0)).toFloat(), Notify)
    }

    private fun SetPosition(Value: Float, Notify: Boolean = true) {
        Position = Value.coerceIn(0f, (Entries.size - 1).coerceAtLeast(0).toFloat())
        val Nearest = Position.roundToInt().coerceIn(0, (Entries.size - 1).coerceAtLeast(0))
        if (Nearest != Selected) {
            Selected = Nearest
            if (Notify) OnSelected?.invoke(Selected)
            if (Entries.isNotEmpty()) contentDescription = "${Entries[Selected]}. Swipe for more choices."
        }
        postInvalidateOnAnimation()
    }

    private fun Settle(Target: Float) {
        Snap?.cancel()
        val End = Target.coerceIn(0f, (Entries.size - 1).coerceAtLeast(0).toFloat())
            .roundToInt().toFloat()
        Snap = ValueAnimator.ofFloat(Position, End).apply {
            duration = 245L
            interpolator = DecelerateInterpolator()
            addUpdateListener { SetPosition(it.animatedValue as Float) }
            start()
        }
    }

    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        if (width <= 0 || height <= 0 || Entries.isEmpty()) return
        val W = width.toFloat()
        val H = height.toFloat()
        val Middle = H * .5f
        // Same recessed band and phosphor treatment as the original Field wheel.
        CenterGlow.shader = LinearGradient(
            0f, Middle - 40f * D, 0f, Middle + 40f * D,
            intArrayOf(Color.TRANSPARENT, Color.argb(19, 112, 152, 195), Color.TRANSPARENT),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP
        )
        Canvas.drawRect(0f, Middle - 40f * D, W, Middle + 40f * D, CenterGlow)
        CenterGlow.shader = null
        // Item zero is the ordinary scrollable command. It uses the exact
        // same typography, spacing and highlight as every other wheel row.
        Entries.forEachIndexed { Index, Label ->
            val Distance = Index - Position
            val Y = Middle + Distance * Spacing
            if (Y < -50f * D || Y > H + 50f * D) return@forEachIndexed
            val Visibility = (1f - abs(Distance) * .22f).coerceIn(.08f, 1f)
            val Focus = (1f - abs(Distance)).coerceIn(0f, 1f)
            val Ink = if (Index == 0 && ActionLabel.isNotEmpty()) ActionInk else FieldInk
            Brush.color = Ink
            PhosphorInk.Apply(Brush, Ink, D)
            Brush.alpha = ((76f + Focus * 176f) * Visibility).toInt().coerceIn(0, 255)
            Brush.textSize = (17.5f + Focus * 5f) * FontScale
            Brush.typeface = android.graphics.Typeface.create("sans-serif",
                if (Focus > .6f) android.graphics.Typeface.BOLD
                else android.graphics.Typeface.NORMAL)
            val Metrics = Brush.fontMetrics
            Canvas.save()
            Canvas.clipRect(6f * D, 0f, W - 6f * D, H)
            Canvas.drawText(Label, W / 2f, Y - (Metrics.ascent + Metrics.descent) / 2f, Brush)
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
                        if (Position.roundToInt() == 0 && ActionLabel.isNotEmpty()) {
                            OnAction?.invoke()
                        } else OnCenterTapped?.invoke()
                        performClick()
                    } else Settle(Position + Offset)
                } else Settle(Position - Speed / (Spacing * 7f))
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

    override fun performClick(): Boolean { super.performClick(); return true }
}
