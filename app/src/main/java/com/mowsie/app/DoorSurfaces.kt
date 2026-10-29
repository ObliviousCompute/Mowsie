package com.mowsie.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Both mating edges use one dimensional profile. The upper door's positive
 * tooth and the lower door's negative socket are exact mirror constructions.
 * All coordinates are in the receiving view's local pixels.
 */
internal object DoorGeometry {
    fun UpperShape(W: Float, H: Float, D: Float): Path {
        val Base = (H - 44f * D).coerceAtLeast(0f)
        val Tip = (H - 3f * D).coerceAtLeast(Base)
        val C = W / 2f
        val L = (C - 93f * D).coerceAtLeast(28f * D)
        val R = (C + 93f * D).coerceAtMost(W - 28f * D)
        return Path().apply {
            moveTo(0f, 0f)
            lineTo(W, 0f)
            lineTo(W, Base)
            lineTo(R, Base)
            cubicTo(R - 25f * D, Base, R - 20f * D, Tip, C + 52f * D, Tip)
            lineTo(C - 52f * D, Tip)
            cubicTo(L + 20f * D, Tip, L + 25f * D, Base, L, Base)
            lineTo(0f, Base)
            close()
        }
    }

    fun UpperEdge(W: Float, H: Float, D: Float): Path {
        val Base = (H - 44f * D).coerceAtLeast(0f)
        val Tip = (H - 3f * D).coerceAtLeast(Base)
        val C = W / 2f
        val L = (C - 93f * D).coerceAtLeast(28f * D)
        val R = (C + 93f * D).coerceAtMost(W - 28f * D)
        return Path().apply {
            moveTo(W, Base)
            lineTo(R, Base)
            cubicTo(R - 25f * D, Base, R - 20f * D, Tip, C + 52f * D, Tip)
            lineTo(C - 52f * D, Tip)
            cubicTo(L + 20f * D, Tip, L + 25f * D, Base, L, Base)
            lineTo(0f, Base)
        }
    }

    fun LowerShape(W: Float, H: Float, D: Float): Path {
        val Lip = 3f * D
        val Seat = 44f * D
        val C = W / 2f
        val L = (C - 93f * D).coerceAtLeast(28f * D)
        val R = (C + 93f * D).coerceAtMost(W - 28f * D)
        return Path().apply {
            moveTo(0f, Lip)
            lineTo(L, Lip)
            cubicTo(L + 25f * D, Lip, L + 20f * D, Seat, C - 52f * D, Seat)
            lineTo(C + 52f * D, Seat)
            cubicTo(R - 20f * D, Seat, R - 25f * D, Lip, R, Lip)
            lineTo(W, Lip)
            lineTo(W, H)
            lineTo(0f, H)
            close()
        }
    }

    fun LowerEdge(W: Float, D: Float): Path {
        val Lip = 3f * D
        val Seat = 44f * D
        val C = W / 2f
        val L = (C - 93f * D).coerceAtLeast(28f * D)
        val R = (C + 93f * D).coerceAtMost(W - 28f * D)
        return Path().apply {
            moveTo(0f, Lip)
            lineTo(L, Lip)
            cubicTo(L + 25f * D, Lip, L + 20f * D, Seat, C - 52f * D, Seat)
            lineTo(C + 52f * D, Seat)
            cubicTo(R - 20f * D, Seat, R - 25f * D, Lip, R, Lip)
            lineTo(W, Lip)
        }
    }
}

/** Foreground face of the movable lower door. It extends beyond the screen bottom. */
class BottomDoorView @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null
) : View(Context, Attributes) {
    private val Fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#101A28")
        style = Paint.Style.FILL
    }
    private val Rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334457")
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.1f
        alpha = 120
    }
    private val Highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6C8098")
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 0.85f
        alpha = 52
    }
    var Mate: Float = 0f
        set(Value) {
            val Updated = Value.coerceIn(0f, 1f)
            if (kotlin.math.abs(field - Updated) > 0.005f) {
                field = Updated
                invalidate()
            }
        }
    private val Crack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#030710")
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.25f
    }
    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        val W = width.toFloat()
        val H = height.toFloat()
        val D = resources.displayMetrics.density
        Canvas.drawPath(DoorGeometry.LowerShape(W, H, D), Fill)
        val Edge = DoorGeometry.LowerEdge(W, D)
        Rim.alpha = (120f * (1f - Mate)).toInt()
        Highlight.alpha = (52f * (1f - Mate)).toInt()
        Crack.alpha = (190f * Mate).toInt()
        Canvas.drawPath(Edge, Rim)
        if (Mate > 0f) Canvas.drawPath(Edge, Crack)

        // Subtle raised-metal highlight INSIDE the face, never a fake drop shadow.
        val Inset = 1.3f * D
        val Lip = 3f * D + Inset
        val Seat = 44f * D - Inset
        val C = W / 2f
        val L = (C - 93f * D).coerceAtLeast(28f * D)
        val R = (C + 93f * D).coerceAtMost(W - 28f * D)
        val Inner = Path().apply {
            moveTo(0f, Lip)
            lineTo(L, Lip)
            cubicTo(L + 25f * D, Lip, L + 20f * D, Seat, C - 52f * D, Seat)
            lineTo(C + 52f * D, Seat)
            cubicTo(R - 20f * D, Seat, R - 25f * D, Lip, R, Lip)
            lineTo(W, Lip)
        }
        Canvas.drawPath(Inner, Highlight)
    }
}

/** Full solid upper hood. Its Ω tooth travels down with the Settings surface. */
class HoodSheetView @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null
) : View(Context, Attributes) {
    private val Fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#101A28")
        style = Paint.Style.FILL
    }
    private val Rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334457")
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.1f
        alpha = 120
    }
    private val Highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6C8098")
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 0.85f
        alpha = 52
    }
    var Mate: Float = 0f
        set(Value) {
            val Updated = Value.coerceIn(0f, 1f)
            if (kotlin.math.abs(field - Updated) > 0.005f) {
                field = Updated
                invalidate()
            }
        }
    private val Crack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#030710")
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.25f
    }
    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        val W = width.toFloat()
        val H = height.toFloat()
        val D = resources.displayMetrics.density
        Canvas.drawPath(DoorGeometry.UpperShape(W, H, D), Fill)
        val Edge = DoorGeometry.UpperEdge(W, H, D)
        Rim.alpha = (120f * (1f - Mate)).toInt()
        Highlight.alpha = (52f * (1f - Mate)).toInt()
        Crack.alpha = (110f * Mate).toInt()
        Canvas.drawPath(Edge, Rim)
        if (Mate > 0f) Canvas.drawPath(Edge, Crack)
        val Inset = 1.3f * D
        val Base = (H - 44f * D).coerceAtLeast(0f) - Inset
        val Tip = (H - 3f * D).coerceAtLeast(Base) - Inset
        val C = W / 2f
        val L = (C - 93f * D).coerceAtLeast(28f * D)
        val R = (C + 93f * D).coerceAtMost(W - 28f * D)
        val Inner = Path().apply {
            moveTo(W, Base)
            lineTo(R, Base)
            cubicTo(R - 25f * D, Base, R - 20f * D, Tip, C + 52f * D, Tip)
            lineTo(C - 52f * D, Tip)
            cubicTo(L + 20f * D, Tip, L + 25f * D, Base, L, Base)
            lineTo(0f, Base)
        }
        Canvas.drawPath(Inner, Highlight)
    }
}

/**
 * Shadow pass behind the Ω hood, in front of the recessed world, and strictly
 * OUTSIDE the UNION of the two doors. Shadows cannot darken either door face.
 * Closing the gap fades both shadows to zero before the contours interlock.
 */
class DoorShadowsView(
    Context: Context, private val Upper: View, private val Lower: View
) : View(Context) {
    private val UpperShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#42030710")
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.6f
        setShadowLayer(9f * resources.displayMetrics.density, 0f,
            5f * resources.displayMetrics.density, Color.parseColor("#B9000309"))
    }
    private val LowerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#42030710")
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.6f
        setShadowLayer(9f * resources.displayMetrics.density, 0f,
            -5f * resources.displayMetrics.density, Color.parseColor("#B9000309"))
    }
    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        if (width == 0 || height == 0 || Upper.width == 0 || Lower.width == 0) return
        val D = resources.displayMetrics.density
        val ThisPos = IntArray(2)
        val UpperPos = IntArray(2)
        val LowerPos = IntArray(2)
        getLocationOnScreen(ThisPos)
        Upper.getLocationOnScreen(UpperPos)
        Lower.getLocationOnScreen(LowerPos)
        val UpperX = (UpperPos[0] - ThisPos[0]).toFloat()
        val UpperY = (UpperPos[1] - ThisPos[1]).toFloat()
        val LowerX = (LowerPos[0] - ThisPos[0]).toFloat()
        val LowerY = (LowerPos[1] - ThisPos[1]).toFloat()

        // Two precise geometries in the same coordinate space as the shadow pass.
        val UpperFace = DoorGeometry.UpperShape(Upper.width.toFloat(), Upper.height.toFloat(), D)
            .apply { offset(UpperX, UpperY) }
        val LowerFace = DoorGeometry.LowerShape(Lower.width.toFloat(), Lower.height.toFloat(), D)
            .apply { offset(LowerX, LowerY) }
        val UpperEdge = DoorGeometry.UpperEdge(Upper.width.toFloat(), Upper.height.toFloat(), D)
            .apply { offset(UpperX, UpperY) }
        val LowerEdge = DoorGeometry.LowerEdge(Lower.width.toFloat(), D)
            .apply { offset(LowerX, LowerY) }

        val UpperTip = UpperY + Upper.height - 3f * D
        val LowerSeat = LowerY + 44f * D
        val Gap = LowerSeat - UpperTip
        val Strength = (Gap / (24f * D)).coerceIn(0f, 1f)
        if (Strength <= 0f) return

        val Doors = Path().apply {
            op(UpperFace, LowerFace, Path.Op.UNION)
        }
        Canvas.save()
        // Difference clipping is available on our minimum Android API (24).
        // The actual solid foreground door views then render ABOVE this pass.
        @Suppress("DEPRECATION")
        Canvas.clipPath(Doors, Region.Op.DIFFERENCE)
        UpperShadow.alpha = (255f * Strength).toInt().coerceIn(0, 255)
        LowerShadow.alpha = (255f * Strength).toInt().coerceIn(0, 255)
        Canvas.drawPath(UpperEdge, UpperShadow)
        Canvas.drawPath(LowerEdge, LowerShadow)
        Canvas.restore()
    }
}
