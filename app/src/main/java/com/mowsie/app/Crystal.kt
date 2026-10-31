package com.mowsie.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min


internal class MountedCrystal(
    val Touch: View, val FloatView: FrameLayout,
    val Glow: DreamGlowView, val ErrorGlow: CrystalErrorGlowView,
    val Shook: ImageView, val Veil: CrystalErrorVeilView,
    val Dark: ImageView, val Light: ImageView
) {
    private var ContactAnimation: ValueAnimator? = null
    var Illuminated: Boolean = false
        private set
    // The receiving Trinity and moving orb follow this persistent crystal
    // state even if the crystal changes while the constellation is docked.
    var OnIlluminationChanged: ((Boolean) -> Unit)? = null
    fun PulseContact() {
        ContactAnimation?.cancel()
        // Return to the chosen persistent light state after each brief contact glow.
        ContactAnimation = ValueAnimator.ofFloat(Glow.Strength, 0.80f,
            if (Illuminated) .66f else .36f).apply {
            duration = 560L
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { Glow.Strength = it.animatedValue as Float }
            start()
        }
    }
    fun Illuminate(Lit: Boolean) {
        Illuminated = Lit
        OnIlluminationChanged?.invoke(Lit)
        ContactAnimation?.cancel()
        Light.animate().alpha(if (Lit) 1f else 0f).setDuration(260L).start()
        Dark.animate().alpha(if (Lit) 0f else 1f).setDuration(260L).start()
        Glow.AnimateStrength(if (Lit) .66f else .36f)
    }
    fun ToggleIllumination() = Illuminate(!Illuminated)
}

/** The large embossed Omega and the single shared crystal ride Door1 itself.
 * Threshold uses the exact same physical views rather than painting a duplicate.
 */
internal fun ControlPanel.MountFloatingCrystal(LowerDoor: FrameLayout): MountedCrystal {
    // Scale the Ω and crystal as one assembly, preserving the hit target
    // and floating animation.
    val OmegaAnchor = FrameLayout(this).apply {
        clipChildren = false
        clipToPadding = false
        scaleX = 0.90f
        scaleY = 0.90f
    }
    LowerDoor.addView(OmegaAnchor, FrameLayout.LayoutParams(Dp(318), Dp(355),
        Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Dp(24) })
    fun OmegaLayer(Tint: Int, Opacity: Float, X: Int, Y: Int) {
        val Image = ImageView(this).apply {
            setImageResource(R.drawable.mowsieomega)
            imageTintList = android.content.res.ColorStateList.valueOf(Tint)
            alpha = Opacity
            translationX = Dp(X).toFloat()
            translationY = Dp(Y).toFloat()
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        OmegaAnchor.addView(Image, FrameLayout.LayoutParams(Dp(318), Dp(318),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL))
    }
    OmegaLayer(Color.parseColor("#080E18"), .90f, 3, 4)
    OmegaLayer(Color.parseColor("#324458"), .55f, -2, -3)
    OmegaLayer(getColor(R.color.blast_door), 1f, 0, 0)
    val GemFloat = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
    OmegaAnchor.addView(GemFloat, FrameLayout.LayoutParams(Dp(244), Dp(240),
        Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Dp(34) })
    val GemGlow = DreamGlowView(this).apply { Strength = .36f }
    GemFloat.addView(GemGlow, FrameLayout.LayoutParams(-1, -1))
    val FailureGlow = CrystalErrorGlowView(this)
    GemFloat.addView(FailureGlow, FrameLayout.LayoutParams(Dp(292), Dp(296), Gravity.CENTER))
    val Dark = ImageView(this).apply {
        setImageResource(R.drawable.darkcrystal)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    val Light = ImageView(this).apply {
        setImageResource(R.drawable.lightcrystal)
        scaleType = ImageView.ScaleType.FIT_CENTER
        alpha = 0f
    }
    val Shook = ImageView(this).apply {
        setImageResource(R.drawable.shookcrystal)
        scaleType = ImageView.ScaleType.FIT_CENTER
        alpha = 0f
    }
    val Veil = CrystalErrorVeilView(this)
    listOf(Dark, Light, Shook).forEach { Art ->
        GemFloat.addView(Art, FrameLayout.LayoutParams(Dp(88), Dp(210), Gravity.CENTER))
    }
    GemFloat.addView(Veil, FrameLayout.LayoutParams(Dp(88), Dp(210), Gravity.CENTER))
    val GemTouch = View(this).apply {
        isClickable = true
        isFocusable = true
        contentDescription = "Crystal. Open or close the Lock"
        if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
    }
    OmegaAnchor.addView(GemTouch, FrameLayout.LayoutParams(Dp(100), Dp(210),
        Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Dp(45) })
    AttachCrystalBob(GemFloat, Dp(5.5f).toFloat())
    return MountedCrystal(GemTouch, GemFloat, GemGlow, FailureGlow, Shook, Veil, Dark, Light)
}

/** One lifecycle-aware float animation used by the Control Panel, Threshold,
 * and WideLock. The optional callback clears Threshold-only failure effects.
 */
internal fun AttachCrystalBob(Target: View, Amplitude: Float, OnDetach: () -> Unit = {}) {
    var Motion: ValueAnimator? = null
    Target.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(View: View) {
            Motion?.cancel()
            Motion = ValueAnimator.ofFloat(-Amplitude, Amplitude).apply {
                duration = 3400L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { Target.translationY = it.animatedValue as Float }
                start()
            }
        }
        override fun onViewDetachedFromWindow(View: View) {
            Motion?.cancel()
            Motion = null
            OnDetach()
        }
    })
}
/**
 * A transparent, slowly drifting prismatic haze. No external animation engine,
 * no pixel bitmaps, and no interaction with secret text or identity state.
 */
class DreamGlowView @JvmOverloads constructor(
    Context: Context,
    Attributes: AttributeSet? = null
) : View(Context, Attributes) {
    var Strength = 0.25f
        set(Value) {
            val Previous = field
            field = Value.coerceIn(0f, 1f)
            // With alpha below ~1 pixel level the halo is invisible, so
            // there's no reason to keep a per-frame animator running.
            if (isAttachedToWindow && (Previous > 0.025f) != (field > 0.025f)) {
                if (field > 0.025f) Motion?.start() else Motion?.cancel()
            }
            if (field > 0.025f || Previous > 0.025f) invalidate()
        }
    private var Phase = 0f
    private var Motion: ValueAnimator? = null
    private var Fade: ValueAnimator? = null
    private val GlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    // The four color falloffs are immutable. Reuse their shaders instead of
    // allocating four new RadialGradient objects on every 60 Hz frame.
    private val Halos = intArrayOf(
        Color.rgb(154, 121, 220), Color.rgb(101, 189, 212),
        Color.rgb(143, 221, 186), Color.rgb(235, 146, 193)
    )
    private val HaloShaders = Array(Halos.size) { Index ->
        val C = Halos[Index]
        val Core = if (Index == 0) 39 else 28
        RadialGradient(0f, 0f, 1f,
            intArrayOf(Color.argb(Core, Color.red(C), Color.green(C), Color.blue(C)),
                Color.argb(Core / 3, Color.red(C), Color.green(C), Color.blue(C)), Color.TRANSPARENT),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP)
    }

    fun AnimateStrength(Target: Float) {
        Fade?.cancel()
        Fade = ValueAnimator.ofFloat(Strength, Target.coerceIn(0f, 1f)).apply {
            duration = 290L
            addUpdateListener { Strength = it.animatedValue as Float }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Motion = ValueAnimator.ofFloat(0f, 6.2831855f).apply {
            duration = 14000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                Phase = it.animatedValue as Float
                if (Strength > 0.025f) invalidate()
            }
            if (Strength > 0.025f) start()
        }
    }

    override fun onDetachedFromWindow() {
        Motion?.cancel()
        Motion = null
        Fade?.cancel()
        Fade = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        if (Strength <= 0.025f || width == 0 || height == 0) return
        val W = width.toFloat()
        val H = height.toFloat()
        val R = min(W * 0.37f, H * 0.66f)
        GlowPaint.alpha = (Strength * 255f).toInt().coerceIn(0, 255)
        for (Index in Halos.indices) {
            val T = Phase + Index * 1.57f
            val X = W * (0.5f + 0.13f * cos(T.toDouble()).toFloat())
            val Y = H * (0.50f + 0.11f * sin((T * 0.72f).toDouble()).toFloat())
            GlowPaint.shader = HaloShaders[Index]
            Canvas.save()
            Canvas.translate(X, Y)
            Canvas.scale(R, R)
            Canvas.drawCircle(0f, 0f, 1f, GlowPaint)
            Canvas.restore()
        }
        GlowPaint.shader = null
        GlowPaint.alpha = 255
    }
}

/** A narrow vertical magenta aura that lingers after the crystal rattle. */
class CrystalErrorGlowView @JvmOverloads constructor(
    Context: Context,
    Attributes: AttributeSet? = null
) : View(Context, Attributes) {
    var Strength = 0f
        set(Value) {
            field = Value.coerceIn(0f, 1f)
            invalidate()
        }
    private val GlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }

    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        if (Strength <= 0f || width == 0 || height == 0) return
        val X = width / 2f
        val Y = height / 2f
        // Scale a radial falloff into a vertical ellipse. It hugs the tall
        // crystal instead of making a circular halo across the whole scene.
        // Larger than the gemstone in both directions, while remaining taller
        // than wide. The transparent outer edge is intentionally well beyond it.
        val RadiusX = width * 0.28f
        val RadiusY = height * 0.49f
        val HotPink = Color.rgb(255, 46, 143)
        val Core = (Strength * 100).toInt().coerceIn(0, 100)
        GlowPaint.shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(
                Color.argb(Core, Color.red(HotPink), Color.green(HotPink), Color.blue(HotPink)),
                Color.argb(Core / 3, 216, 51, 174),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.38f, 1f), Shader.TileMode.CLAMP
        )
        Canvas.save()
        Canvas.translate(X, Y)
        Canvas.scale(RadiusX, RadiusY)
        Canvas.drawCircle(0f, 0f, 1f, GlowPaint)
        Canvas.restore()
        GlowPaint.shader = null
    }
}


/** Very soft pink mist in FRONT of the stone, clipped to the exact SVG outline. */
class CrystalErrorVeilView @JvmOverloads constructor(
    Context: Context,
    Attributes: AttributeSet? = null
) : View(Context, Attributes) {
    var Strength = 0f
        set(Value) {
            field = Value.coerceIn(0f, 1f)
            invalidate()
        }
    private val MistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val CrystalOutline = Path().apply {
        moveTo(158f, 16f)
        lineTo(286f, 344f)
        lineTo(264f, 556f)
        lineTo(246f, 631f)
        lineTo(158f, 731f)
        lineTo(80f, 633f)
        lineTo(55f, 537f)
        lineTo(26f, 429f)
        lineTo(28f, 276f)
        close()
    }
    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        if (Strength <= 0f || width == 0 || height == 0) return
        Canvas.save()
        Canvas.scale(width / 312f, height / 750f)
        Canvas.clipPath(CrystalOutline)
        val Core = (Strength * 69f).toInt().coerceIn(0, 69)
        MistPaint.shader = RadialGradient(
            151f, 410f, 215f,
            intArrayOf(
                Color.argb(Core, 255, 72, 158),
                Color.argb(Core / 2, 230, 60, 171),
                Color.argb(0, 209, 66, 163)
            ),
            floatArrayOf(0f, 0.43f, 1f), Shader.TileMode.CLAMP
        )
        Canvas.drawCircle(151f, 410f, 215f, MistPaint)
        MistPaint.shader = null
        Canvas.restore()
    }
}
