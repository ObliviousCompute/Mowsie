package com.mowsie.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import android.util.AttributeSet
import android.view.View
import android.view.MotionEvent

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
        color = Context.getColor(R.color.blast_door)
        style = Paint.Style.FILL
    }
    private val Rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#43515C")
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.1f
        alpha = 120
    }
    private val Highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#78868C")
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

/** Shared Airlock silhouette for its solid face and BOTH physical shadow passes. */
internal object AirlockGeometry {
    fun Face(W: Float, H: Float, D: Float): Path {
        return Edge(W, D).apply {
            lineTo(W, H)
            lineTo(0f, H)
            close()
        }
    }

    fun Edge(W: Float, D: Float): Path {
        // Door 3 uses the exact dimensional family of Door 2's Ω tooth,
        // inverted so its crown rises into the QR chamber. The wide, flat
        // The shoulder and crown use separate offsets to preserve the Ω relief.
        // and the 48dp airlock anchor / existing animation stops.
        val Shoulder = 34f * D
        val Crown = 3f * D
        val C = W / 2f
        val L = (C - 93f * D).coerceAtLeast(28f * D)
        val R = (C + 93f * D).coerceAtMost(W - 28f * D)
        return Path().apply {
            moveTo(0f, Shoulder)
            lineTo(L, Shoulder)
            // Same +/-93dp shoulder, +/-52dp crown and 25/20dp Bezier
            // handles as DoorGeometry.UpperEdge, turned crown-side-up.
            cubicTo(L + 25f * D, Shoulder, L + 20f * D, Crown, C - 52f * D, Crown)
            lineTo(C + 52f * D, Crown)
            cubicTo(R - 20f * D, Crown, R - 25f * D, Shoulder, R, Shoulder)
            lineTo(W, Shoulder)
        }
    }
}

/**
 * Matching blue-black obsidian airlock. The entire panel is a single solid shape,
 * with the inverted 41dp Ω tooth of Door 2. It is mounted BEHIND the
 * lower blast door, which occludes its lower face and preserves the existing
 * animated Trinity and motion.
 *
 * Geometry: the airlock is still anchored 48dp above Door 1's lip.
 * Its crown remains 3dp from the airlock top, while the new full-width
 * flat shoulder is 34dp from that top. This exposes a narrow horizontal
 * edge immediately above Door 1's own lip and gives both overlapping edges
 * the same clean, close-contact shadow geometry.
 */
class AirlockHatchView @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null
) : View(Context, Attributes) {
    private val Density = resources.displayMetrics.density
    private fun Dp(Value: Float) = Value * Density

    private val Pearl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Context.getColor(R.color.blast_door)
    }
    private val Seam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Dp(1.15f)
        color = Color.parseColor("#202B32")
        alpha = 115
    }
    private val Rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Dp(0.75f)
        color = Color.parseColor("#455667")
        alpha = 43
    }
    private val ItemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f * resources.displayMetrics.scaledDensity
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        color = Color.parseColor("#F0F3FA")
        setShadowLayer(Dp(2f), 0f, Dp(1.4f), Color.parseColor("#A900050B"))
    }
    var PrimaryInk: Int = Color.parseColor("#73A9E4")
        set(Value) { field = Value; invalidate() }
    var SecondaryInk: Int = Color.parseColor("#B3D0BF")
        set(Value) { field = Value; invalidate() }

    var Mode: String = "SYNC"
        set(Value) {
            field = Value
            invalidate()
            contentDescription = "Omega airlock, $Value"
        }

    /** Four primary chamber choices live on the raised sheet. */
    var OnMenuChoice: ((String) -> Unit)? = null
    private val MenuChoices = listOf("Scan", "Peers", "Fields", "Lanterns")
    // Four choices fill the original exposed height more evenly; the final
    // row stays above Door1's notch without changing the selector travel.
    private val MenuFirstLine = 114f
    private val MenuLineStep = 52f
    private var PressedChoice = -1

    // Resting tooth carries only the floating Trinity, no lettering.
    // Gradual reveal begins only after the hatch clears the lower recess.
    var MenuReveal: Float = 0f
        set(Value) {
            val Next = Value.coerceIn(0f, 1f)
            if (kotlin.math.abs(field - Next) > .002f) {
                field = Next
                invalidate()
            }
        }

    /** Touch only the Trinity crown or an exposed menu choice. No invisible hit area. */
    override fun onTouchEvent(Event: MotionEvent): Boolean {
        val Centered = kotlin.math.abs(Event.x - width / 2f) <= Dp(113f)
        val Crown = Centered && Event.y >= 0f && Event.y <= Dp(87f)
        fun ChoiceAt(): Int {
            if (MenuReveal < 0.95f || !Centered) return -1
            return MenuChoices.indices.firstOrNull { Index ->
                kotlin.math.abs(Event.y - Dp(MenuFirstLine + MenuLineStep * Index)) <= Dp(19f)
            } ?: -1
        }
        return when (Event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                PressedChoice = ChoiceAt()
                if (PressedChoice >= 0) true
                else if (Crown) super.onTouchEvent(Event)
                else false
            }
            MotionEvent.ACTION_UP -> {
                val Index = PressedChoice
                PressedChoice = -1
                if (Index >= 0) {
                    if (ChoiceAt() == Index) OnMenuChoice?.invoke(MenuChoices[Index])
                    true
                } else super.onTouchEvent(Event)
            }
            MotionEvent.ACTION_CANCEL -> {
                PressedChoice = -1
                super.onTouchEvent(Event)
            }
            else -> if (PressedChoice >= 0) true else super.onTouchEvent(Event)
        }
    }


    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        val W = width.toFloat()
        val H = height.toFloat()
        if (W <= 0f || H <= 0f) return
        val C = W / 2f
        // Shared full-width silhouette, not an overlaid card. Its lateral
        // edges remain concealed behind the foreground lower blast door.
        val Face = AirlockGeometry.Face(W, H, Density)
        val Edge = AirlockGeometry.Edge(W, Density)
        // Uniform shared obsidian-blue material. Surface depth comes from the actual
        // contours, embossed Ω and contact shadows, never painted gradients.
        Canvas.drawPath(Face, Pearl)
        // Delicate upper rim; shadows are drawn independently so that the
        // blast door still casts its own shadow ON this raised material.
        Canvas.drawPath(Edge, Seam)
        Canvas.save()
        Canvas.translate(0f, Dp(1.5f))
        Canvas.drawPath(Edge, Rim)
        Canvas.restore()

        // The old Ω engraving is deliberately gone. The three moving Opal
        // orbs are hosted by AirlockAssembly directly above this solid tab.
        // Keep the floating Trinity unobstructed. When Door2 opens,
        // put all mode/menu lettering *below* the Ω, never across its face.
        // All four choices use the saved PRIMARY color.
        if (MenuReveal > 0f) {
            ItemPaint.color = PrimaryInk
            PhosphorInk.Apply(ItemPaint, PrimaryInk, Density)
            ItemPaint.alpha = (255f * MenuReveal).toInt().coerceIn(0, 255)
            // Four generously spaced rows finish above
            // Door1's recessed center notch. The QR chamber stays unobstructed.
            MenuChoices.forEachIndexed { Index, Label ->
                Canvas.drawText(Label, C, Dp(MenuFirstLine + MenuLineStep * Index), ItemPaint)
            }
        }
    }
}


/** Full solid upper hood. Its Ω tooth travels down with the Settings surface. */
class HoodSheetView @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null
) : View(Context, Attributes) {
    private val Fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Context.getColor(R.color.blast_door)
        style = Paint.Style.FILL
    }
    private val Rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#202B32")
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.1f
        alpha = 110
    }
    private val Highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#27323A")
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 0.85f
        alpha = 35
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
        // Fixed magnetic receiving seat, halfway between the three stationary
        // opals. Its pigment is between the chamber and the door material.
        // The lower-rim bevel borrows the same faint metal highlight as Door1.
        val SeatCenterX = W / 2f
        val SeatY = 67f * D
        val SeatRadius = 15f * D
        val SeatFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F1925")
            style = Paint.Style.FILL
        }
        val SeatShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#080E16")
            style = Paint.Style.STROKE
            strokeWidth = D * 1.5f
            alpha = 105
        }
        val SeatBevel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#78868C")
            style = Paint.Style.STROKE
            strokeWidth = D * 1.05f
            alpha = 80
            strokeCap = Paint.Cap.ROUND
        }
        Canvas.drawCircle(SeatCenterX, SeatY, SeatRadius, SeatFill)
        Canvas.drawArc(SeatCenterX - SeatRadius, SeatY - SeatRadius,
            SeatCenterX + SeatRadius, SeatY + SeatRadius, 205f, 145f, false, SeatShadow)
        Canvas.drawArc(SeatCenterX - SeatRadius, SeatY - SeatRadius,
            SeatCenterX + SeatRadius, SeatY + SeatRadius, 20f, 140f, false, SeatBevel)
        val Edge = DoorGeometry.UpperEdge(W, H, D)
        Rim.alpha = (110f * (1f - Mate)).toInt()
        Highlight.alpha = (35f * (1f - Mate)).toInt()
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
 * Airlock depth pass between the third door and the LOWER blast door.
 * The raised pearl tooth casts outward onto the QR bay. The blast door
 * independently casts INWARD onto the pearl face wherever it overlaps.
 * Both lower-tier contact shadows have a narrow edge, while Door2 retains
 * a broad 11dp chamber shadow. Each shadow follows its own door geometry.
 */
class AirlockDepthShadowsView(
    Context: Context, private val Airlock: View, private val Lower: View,
    private val Upper: View
) : View(Context) {
    private val Density = resources.displayMetrics.density
    private val ToothShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // a broader, slightly softer lip shadow FROM Door 3 ONTO the QR chamber.
        color = Color.parseColor("#9C02070A")
        strokeWidth = Density * 1.35f
        setShadowLayer(3.25f * Density, 0f, -1.0f * Density, Color.parseColor("#D8000305"))
    }
    private val BlastShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // Door 1 now uses the same diffuse lip-shadow language ONTO Door 3.
        color = Color.parseColor("#9C02070A")
        strokeWidth = Density * 1.35f
        setShadowLayer(3.25f * Density, 0f, -1.0f * Density, Color.parseColor("#D8000305"))
    }
    private val UpperContactShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#80030809")
        strokeWidth = Density * 1.1f
        setShadowLayer(2f * Density, 0f, 1.5f * Density, Color.parseColor("#E0000305"))
    }
    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        if (Airlock.width <= 0 || Airlock.height <= 0 || Lower.width <= 0 || Lower.height <= 0) return
        val At = IntArray(2)
        val A = IntArray(2)
        val L = IntArray(2)
        val U = IntArray(2)
        getLocationOnScreen(At)
        Airlock.getLocationOnScreen(A)
        Lower.getLocationOnScreen(L)
        Upper.getLocationOnScreen(U)
        val Ax = (A[0] - At[0]).toFloat()
        val Ay = (A[1] - At[1]).toFloat()
        val Lx = (L[0] - At[0]).toFloat()
        val Ly = (L[1] - At[1]).toFloat()
        val Ux = (U[0] - At[0]).toFloat()
        val Uy = (U[1] - At[1]).toFloat()
        val InnerFace = AirlockGeometry.Face(Airlock.width.toFloat(), Airlock.height.toFloat(), Density)
            .apply { offset(Ax, Ay) }
        val InnerEdge = AirlockGeometry.Edge(Airlock.width.toFloat(), Density)
            .apply { offset(Ax, Ay) }
        val BlastFace = DoorGeometry.LowerShape(Lower.width.toFloat(), Lower.height.toFloat(), Density)
            .apply { offset(Lx, Ly) }
        val BlastEdge = DoorGeometry.LowerEdge(Lower.width.toFloat(), Density)
            .apply { offset(Lx, Ly) }
        val UpperFace = DoorGeometry.UpperShape(Upper.width.toFloat(), Upper.height.toFloat(), Density)
            .apply { offset(Ux, Uy) }
        val UpperEdge = DoorGeometry.UpperEdge(Upper.width.toFloat(), Upper.height.toFloat(), Density)
            .apply { offset(Ux, Uy) }

        // The inner tooth casts only OUTWARD. Nothing gets painted on its face.
        Canvas.save()
        @Suppress("DEPRECATION")
        Canvas.clipPath(InnerFace, Region.Op.DIFFERENCE)
        @Suppress("DEPRECATION")
        Canvas.clipPath(BlastFace, Region.Op.DIFFERENCE)
        Canvas.drawPath(InnerEdge, ToothShadow)
        Canvas.restore()

        // The foreground blast door casts only ONTO exposed Airlock material,
        // including across the contoured joint where the two sheets meet.
        Canvas.save()
        Canvas.clipPath(InnerFace)
        @Suppress("DEPRECATION")
        Canvas.clipPath(BlastFace, Region.Op.DIFFERENCE)
        Canvas.drawPath(BlastEdge, BlastShadow)
        Canvas.restore()

        // When the upper blast door approaches the Airlock's exposed crown,
        // its shadow lands on pearl, NOT on the distant chamber. The top hood
        // itself is drawn afterwards and naturally occludes its own shadow.
        Canvas.save()
        Canvas.clipPath(InnerFace)
        @Suppress("DEPRECATION")
        Canvas.clipPath(UpperFace, Region.Op.DIFFERENCE)
        Canvas.drawPath(UpperEdge, UpperContactShadow)
        Canvas.restore()
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
        setShadowLayer(11f * resources.displayMetrics.density, 0f,
            5f * resources.displayMetrics.density, Color.parseColor("#B4000309"))
    }
    private val LowerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#42030710")
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.6f
        setShadowLayer(11f * resources.displayMetrics.density, 0f,
            -5f * resources.displayMetrics.density, Color.parseColor("#B4000309"))
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


/**
 * Sealed landscape backplate. The portrait Ω and crystal are mounted
 * separately as the SAME 318dp/244dp layered artwork, not miniatures drawn
 * into this canvas. This view owns only the wide mating seam and, when the
 * full assembly fits, the same recessed Trinity socket as HoodSheetView.
 * Nothing behind this opaque scene can expose the Cache or the public QR.
 */
class LandscapeSealedDoorsView @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null
) : View(Context, Attributes) {
    private val Density = resources.displayMetrics.density
    private fun Dp(Value: Float) = Value * Density

    var SeamDp = 50f
        set(Value) { field = Value; invalidate() }
    var ShowTrinity = true
        set(Value) { field = Value; invalidate() }

    private val Face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Context.getColor(R.color.blast_door)
        style = Paint.Style.FILL
    }
    // Match the closed portrait doors: same dark crack ink as
    // BottomDoorView/HoodSheetView and the same subtle lower lip highlight.
    private val Groove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#030710")
        style = Paint.Style.STROKE
        strokeWidth = Dp(1.25f)
        alpha = 190
    }
    private val LightRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#78868C")
        style = Paint.Style.STROKE
        strokeWidth = Dp(0.85f)
        alpha = 52
    }
    private val SeatFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F1925")
        style = Paint.Style.FILL
    }
    private val SeatShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#080E16")
        style = Paint.Style.STROKE
        strokeWidth = Dp(1.5f)
        alpha = 105
    }
    private val SeatBevel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#78868C")
        style = Paint.Style.STROKE
        strokeWidth = Dp(1.05f)
        alpha = 80
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        val W = width.toFloat()
        val H = height.toFloat()
        if (W <= 0f || H <= 0f) return
        Canvas.drawRect(0f, 0f, W, H, Face)

        // Same curved mating family as portrait, but its shoulders run across
        // the full landscape width instead of stopping at a narrow tab.
        val Edge = DoorGeometry.UpperEdge(W, Dp(SeamDp + 44f), Density)
        Canvas.drawPath(Edge, Groove)
        Canvas.save()
        Canvas.translate(0f, -Dp(1.35f))
        Canvas.drawPath(Edge, LightRim)
        Canvas.restore()

        // Recessed Trinity socket; the docked orb is a separate foreground
        // image in Lock.kt.
        if (ShowTrinity) {
            val X = W / 2f
            val Y = Dp(47f)
            val R = Dp(15f)
            Canvas.drawCircle(X, Y, R, SeatFill)
            Canvas.drawArc(X - R, Y - R, X + R, Y + R,
                205f, 145f, false, SeatShadow)
            Canvas.drawArc(X - R, Y - R, X + R, Y + R,
                20f, 140f, false, SeatBevel)
        }
    }
}
