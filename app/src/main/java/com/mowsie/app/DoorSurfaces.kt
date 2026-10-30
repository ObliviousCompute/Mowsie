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
import androidx.appcompat.content.res.AppCompatResources
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
        // V48: Door 3 uses the exact dimensional family of Door 2's Ω tooth,
        // inverted so its crown rises into the QR chamber. The wide, flat
        // V50: raise only the horizontal shoulder another 5dp (39dp -> 34dp).
        // The center TOP stays at 3dp, preserving the Omega's original pixels
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
 * centered embossed Omega and motion.
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
    private val TitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f * resources.displayMetrics.scaledDensity
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        color = Color.parseColor("#F5F8FC")
        setShadowLayer(Dp(2.3f), 0f, Dp(1.4f), Color.parseColor("#B900050B"))
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
    private val OmegaShadow = AppCompatResources.getDrawable(Context, R.drawable.mowsieomega)?.mutate()
    private val OmegaBevel = AppCompatResources.getDrawable(Context, R.drawable.mowsieomega)?.mutate()
    private val OmegaFace = AppCompatResources.getDrawable(Context, R.drawable.mowsieomega)?.mutate()

    var Mode: String = "SYNC"
        set(Value) {
            field = Value
            invalidate()
            contentDescription = "Omega airlock, $Value"
        }

    // Resting tooth always carries JUST its embossed Omega, no letters.
    // Gradual reveal begins only after the hatch clears the lower recess.
    var MenuReveal: Float = 0f
        set(Value) {
            val Next = Value.coerceIn(0f, 1f)
            if (kotlin.math.abs(field - Next) > .002f) {
                field = Next
                invalidate()
            }
        }

    /** Only the actual crown is a tap target, never the invisible panel. */
    override fun onTouchEvent(Event: MotionEvent): Boolean {
        if (Event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (kotlin.math.abs(Event.x - width / 2f) > Dp(93f) ||
                Event.y < 0f || Event.y > Dp(87f)) return false
        }
        return super.onTouchEvent(Event)
    }

    private fun DrawOmega(Canvas: Canvas, Center: Float) {
        // The upper Ω uses a 108 x 88dp ImageView with FIT_CENTER.
        // The vector has a 1:1 viewport, so its ACTUAL rendered bounds are
        // 88 x 88dp, centered within that frame. Drawing the full 108 x 88dp
        // rectangle here stretched the lower Ω horizontally in V23.
        // Match the upper glyph's true rendered dimensions, not its view box.
        val Size = Dp(88f).toInt()
        val Left = (Center - Size / 2f).toInt()
        val Top = Dp(2f).toInt()
        fun Layer(Drawable: android.graphics.drawable.Drawable?, Tint: Int,
                  Alpha: Float, Dx: Float, Dy: Float) {
            Drawable ?: return
            Drawable.setTint(Tint)
            Drawable.alpha = (255f * Alpha).toInt().coerceIn(0, 255)
            val X = (Left + Dp(Dx)).toInt()
            val Y = (Top + Dp(Dy)).toInt()
            Drawable.setBounds(X, Y, X + Size, Y + Size)
            Drawable.draw(Canvas)
        }
        // Identical emboss and face pigments to the upper blast-door Ω.
        // The full-opacity face blends into the single shared door material.
        Layer(OmegaShadow, Color.parseColor("#070C14"), .95f, 2f, 3f)
        Layer(OmegaBevel, Color.parseColor("#334252"), .46f, -1f, -2f)
        Layer(OmegaFace, context.getColor(R.color.blast_door), 1f, 0f, 0f)
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

        DrawOmega(Canvas, C)
        // V52: keep the embossed Ω unobstructed. When Door 3 opens,
        // put all mode/menu lettering *below* the Ω, never across its face.
        // Mode uses the saved Primary color, options use Secondary, and the
        // existing static color-matched phosphor style follows both.
        if (MenuReveal > 0f) {
            TitlePaint.color = PrimaryInk
            ItemPaint.color = SecondaryInk
            PhosphorInk.Apply(TitlePaint, PrimaryInk, Density)
            PhosphorInk.Apply(ItemPaint, SecondaryInk, Density)
            TitlePaint.alpha = (255f * MenuReveal).toInt().coerceIn(0, 255)
            ItemPaint.alpha = TitlePaint.alpha
            // Door 3 has a shallower exposed panel than Door 2, so its
            // equally spaced text baselines use a compact 40dp rhythm.
            // This keeps the fourth LOAD row clear of Door 1's lip.
            val FirstLine = 115f
            val LineStep = 40f
            Canvas.drawText(Mode, C, Dp(FirstLine), TitlePaint)
            when (Mode) {
                "SYNC" -> {
                    Canvas.drawText("Current", C, Dp(FirstLine + LineStep), ItemPaint)
                    Canvas.drawText("All", C, Dp(FirstLine + LineStep * 2f), ItemPaint)
                }
                "LOAD" -> {
                    Canvas.drawText("Paste", C, Dp(FirstLine + LineStep), ItemPaint)
                    Canvas.drawText("Friends", C, Dp(FirstLine + LineStep * 2f), ItemPaint)
                    Canvas.drawText("Upload", C, Dp(FirstLine + LineStep * 3f), ItemPaint)
                }
                "SEND" -> {
                    Canvas.drawText("Preview", C, Dp(FirstLine + LineStep), ItemPaint)
                    Canvas.drawText("Confirm", C, Dp(FirstLine + LineStep * 2f), ItemPaint)
                }
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
 * V48 keeps the same shadow pigments and precise face clipping but makes
 * both lower-tier contact shadows narrower and sharper. Door 2 retains its
 * original broad 11dp chamber shadow. Each shadow follows its own geometry,
 * including the new flat, uninterrupted shoulders of Door 3.
 */
class AirlockDepthShadowsView(
    Context: Context, private val Airlock: View, private val Lower: View,
    private val Upper: View
) : View(Context) {
    private val Density = resources.displayMetrics.density
    private val ToothShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // V51: a broader, slightly softer lip shadow FROM Door 3 ONTO the QR chamber.
        color = Color.parseColor("#9C02070A")
        strokeWidth = Density * 1.35f
        setShadowLayer(3.25f * Density, 0f, -1.0f * Density, Color.parseColor("#D8000305"))
    }
    private val BlastShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // V51: Door 1 now uses the same diffuse lip-shadow language ONTO Door 3.
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
 * V36 sealed landscape presentation. It uses the SAME curved mating geometry
 * as the working portrait machine, but its doors extend across landscape width.
 * The screen stays fully covered. No QR, balance or second crystal can leak
 * through while the view is visible.
 */
class LandscapeSealedDoorsView @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null
) : View(Context, Attributes) {
    private val D = resources.displayMetrics.density
    private fun Dp(Value: Float) = Value * D

    private val Face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Context.getColor(R.color.blast_door)
        style = Paint.Style.FILL
    }
    private val Groove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#080F1A")
        style = Paint.Style.STROKE
        strokeWidth = Dp(1.2f)
        alpha = 185
    }
    private val LightRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A4B5B")
        style = Paint.Style.STROKE
        strokeWidth = Dp(0.75f)
        alpha = 72
    }
    private val OmegaShadow = AppCompatResources.getDrawable(Context, R.drawable.mowsieomega)?.mutate()
    private val OmegaBevel = AppCompatResources.getDrawable(Context, R.drawable.mowsieomega)?.mutate()
    private val OmegaFace = AppCompatResources.getDrawable(Context, R.drawable.mowsieomega)?.mutate()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun DrawOmega(Canvas: Canvas, CenterX: Float, CenterY: Float) {
        // Portrait door 2 renders its 1:1 vector into an 88dp square.
        val Size = Dp(88f).toInt()
        val Left = (CenterX - Size / 2f).toInt()
        val Top = (CenterY - Size / 2f).toInt()
        fun Layer(Drawable: android.graphics.drawable.Drawable?, Tint: Int,
                  Alpha: Float, Dx: Float, Dy: Float) {
            Drawable ?: return
            Drawable.setTint(Tint)
            Drawable.alpha = (255f * Alpha).toInt().coerceIn(0, 255)
            val X = Left + Dp(Dx).toInt()
            val Y = Top + Dp(Dy).toInt()
            Drawable.setBounds(X, Y, X + Size, Y + Size)
            Drawable.draw(Canvas)
        }
        Layer(OmegaShadow, Color.parseColor("#070C14"), .95f, 2f, 3f)
        Layer(OmegaBevel, Color.parseColor("#334252"), .46f, -1f, -2f)
        Layer(OmegaFace, context.getColor(R.color.blast_door), 1f, 0f, 0f)
    }

    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        val W = width.toFloat()
        val H = height.toFloat()
        if (W <= 0f || H <= 0f) return
        // One uniform material. Door 1 meets door 2 with the existing Ω tooth.
        Canvas.drawRect(0f, 0f, W, H, Face)
        val Seam = H / 2f - Dp(10f)
        val Edge = DoorGeometry.UpperEdge(W, Seam + Dp(44f), D)
        Canvas.drawPath(Edge, Groove)
        Canvas.save()
        Canvas.translate(0f, -Dp(1.35f))
        Canvas.drawPath(Edge, LightRim)
        Canvas.restore()
        DrawOmega(Canvas, W / 2f, Seam - Dp(2f))
    }
}
