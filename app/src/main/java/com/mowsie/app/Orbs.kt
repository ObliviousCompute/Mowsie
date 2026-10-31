package com.mowsie.app

import android.animation.ValueAnimator
import android.graphics.drawable.TransitionDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.cos
import kotlin.math.sin

/** Four approved crystal-family light states. */
internal enum class OrbLight { Dormant, Active, Bright, Shook }

/** One rigid upward Trinity mounted in the upper tab, and one floating orb
 * carried by the lower selector tab. Only the single orb has a hover animator.
 * The divot itself is part of HoodSheetView, not a painted orb background.
 */
internal class Orbs(private val Host: ControlPanel, UpperHost: FrameLayout,
                    LowerTabForeground: FrameLayout) {
    private fun Dp(Value: Float) = Host.Dp(Value).toFloat()
    private val Trinity: ImageView
    private val Orb: ImageView
    private var Motion: ValueAnimator? = null
    private var Docked = true
    private var TrinityLight = OrbLight.Bright
    private var OrbLightState = OrbLight.Bright
    private var FadeOnContact: Runnable? = null
    private var FinishContactFade: Runnable? = null
    private var ContactTransitionRunning = false
    private var CrystalLit = false
    var OnUpperTap: (() -> Unit)? = null
    var OnTrinityTap: (() -> Unit)? = null

    // Fixed tab artwork geometry in dp. Trinity centers are (80,33),
    // (48,82), (112,82), with the recessed socket centered at (80,67).
    private val UpperArtWidth = 160
    private val UpperArtHeight = 110
    // Upper divot center is 67dp below the stationary tab's top.
    // When Door1 is closed, the selector's origin lies 15dp below the upper
    // tab's origin, so the moving orb center at 52dp docks at 67dp.
    private val MovingOrbCenter = 52f
    private val OrbFrame = 54
    private val OrbInset = 11

    init {
        Trinity = ImageView(Host).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = true
            isFocusable = true
            contentDescription = "Fixed Trinity. Open Settings or Cache"
            setOnClickListener { IlluminateTrinity(); OnUpperTap?.invoke() }
        }
        UpperHost.addView(Trinity,
            FrameLayout.LayoutParams(Host.Dp(UpperArtWidth), Host.Dp(UpperArtHeight),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        Orb = ImageView(Host).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(Host.Dp(OrbInset), Host.Dp(OrbInset),
                Host.Dp(OrbInset), Host.Dp(OrbInset))
            isClickable = true
            isFocusable = true
            contentDescription = "Floating orb. Chamber selector"
            setOnClickListener { IlluminateOrb(); OnTrinityTap?.invoke() }
        }
        LowerTabForeground.addView(Orb,
            FrameLayout.LayoutParams(Host.Dp(OrbFrame), Host.Dp(OrbFrame),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = Host.Dp((MovingOrbCenter - OrbFrame / 2f).toInt())
            })
        SetDocked(true)
        Orb.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(View: View) { StartMotion() }
            override fun onViewDetachedFromWindow(View: View) {
                Motion?.cancel()
                Motion = null
            }
        })
    }

    private fun TrinityAsset(State: OrbLight) = when (State) {
        OrbLight.Dormant -> R.drawable.orbdormanttrinityfixed
        OrbLight.Active -> R.drawable.orbactivetrinityfixed
        OrbLight.Bright -> R.drawable.orbbrighttrinityfixed
        OrbLight.Shook -> R.drawable.orbshooktrinityfixed
    }
    private fun OrbAsset(State: OrbLight) = when (State) {
        OrbLight.Dormant -> R.drawable.orbdormantupper
        OrbLight.Active -> R.drawable.orbactiveupper
        OrbLight.Bright -> R.drawable.orbbrightupper
        OrbLight.Shook -> R.drawable.orbshookupper
    }
    private fun Render() {
        Trinity.setImageResource(TrinityAsset(TrinityLight))
        Orb.setImageResource(OrbAsset(OrbLightState))
    }
    private fun CancelContactFade() {
        FadeOnContact?.let { Trinity.removeCallbacks(it) }
        FinishContactFade?.let { Trinity.removeCallbacks(it) }
        FadeOnContact = null
        FinishContactFade = null
        ContactTransitionRunning = false
    }

    private fun RestingLight(): OrbLight =
        if (CrystalLit) OrbLight.Active else OrbLight.Dormant

    /** Crossfade existing artwork into a new state without changing geometry. */
    private fun FadeToRestingLight(Duration: Int) {
        val Target = RestingLight()
        fun FadeImage(Image: ImageView, Resource: Int) {
            val From = Image.drawable ?: return
            val To = Host.getDrawable(Resource)?.mutate() ?: return
            val Blend = TransitionDrawable(arrayOf(From, To)).apply {
                isCrossFadeEnabled = true
            }
            Image.setImageDrawable(Blend)
            Blend.startTransition(Duration)
        }
        FadeImage(Trinity, TrinityAsset(Target))
        FadeImage(Orb, OrbAsset(Target))
        TrinityLight = Target
        OrbLightState = Target
    }

    /** A long press or successful unlock changes the settled orb illumination. */
    fun SetCrystalIlluminated(Lit: Boolean) {
        if (CrystalLit == Lit) return
        CrystalLit = Lit
        if (FadeOnContact != null) return // Contact pulse will use the new target.
        // If the glow is already fading, redirect its endpoint gracefully.
        val WasFading = ContactTransitionRunning
        CancelContactFade()
        if (Docked || WasFading) {
            FadeToRestingLight(260)
        } else {
            val State = if (Lit) OrbLight.Active else OrbLight.Dormant
            TrinityLight = State
            OrbLightState = State
            Render()
        }
    }

    fun SetDocked(Joined: Boolean) {
        CancelContactFade()
        Docked = Joined
        TrinityLight = if (Joined) OrbLight.Bright else OrbLight.Dormant
        OrbLightState = if (Joined) OrbLight.Bright else OrbLight.Dormant
        if (Joined) { Orb.translationX = 0f; Orb.translationY = 0f }
        Render()
    }
    fun IlluminateUpper() = IlluminateTrinity()
    fun IlluminateTrinity() {
        if (!Docked) { TrinityLight = OrbLight.Active; Render() }
    }
    fun IlluminateOrb() {
        if (!Docked) { OrbLightState = OrbLight.Active; Render() }
    }
    fun PulseContact() {
        CancelContactFade()
        TrinityLight = OrbLight.Bright
        OrbLightState = OrbLight.Bright
        Render() // Jump directly to Bright at physical contact, no rapid stepping.
        // Contact cadence: a 120ms Bright beat followed by a 1500ms fade.
        // A dormant crystal fades to Dormant;
        // an illuminated crystal settles at Active and stays illuminated.
        val Fade = Runnable {
            FadeOnContact = null
            // A quick Reveal can separate the tabs before the fade finishes.
            // Complete the full resonance even while the orb is traveling.
            ContactTransitionRunning = true
            FadeToRestingLight(1500)
            val Finish = Runnable {
                ContactTransitionRunning = false
                FinishContactFade = null
            }
            FinishContactFade = Finish
            Trinity.postDelayed(Finish, 1500L)
        }
        FadeOnContact = Fade
        Trinity.postDelayed(Fade, 120L)
        Trinity.animate().cancel()
        Orb.animate().cancel()
        Trinity.animate().scaleX(1.035f).scaleY(1.035f).setDuration(125L)
            .withEndAction {
                Trinity.animate().scaleX(1f).scaleY(1f).setDuration(330L).start()
            }.start()
        Orb.animate().scaleX(1.08f).scaleY(1.08f).setDuration(125L)
            .withEndAction {
                Orb.animate().scaleX(1f).scaleY(1f).setDuration(330L).start()
            }.start()
    }
    fun UpdateMating(IsJoined: Boolean, OpenFraction: Float) {
        if (IsJoined) {
            if (!Docked) SetDocked(true)
            return
        }
        val WasDocked = Docked
        Docked = false
        // Keep the original 1.5-second contact glow alive through separation.
        if (FadeOnContact != null || ContactTransitionRunning) return
        val Next = if (CrystalLit || OpenFraction < .33f)
            OrbLight.Active else OrbLight.Dormant
        if (WasDocked || TrinityLight != Next || OrbLightState != Next) {
            TrinityLight = Next
            OrbLightState = Next
            Render()
        }
    }
    private fun StartMotion() {
        Motion?.cancel()
        Motion = ValueAnimator.ofFloat(0f, (Math.PI * 2 * 12).toFloat()).apply {
            duration = 39000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val T = animatedValue as Float
                // In the magnetic seat the orb becomes perfectly stationary;
                // outside it, preserve the gentle XY squiggle.
                val Undocked = if (Docked) 0f else 1f
                Orb.translationX = Dp(Undocked * 1.7f * sin(T * .91f))
                Orb.translationY = Dp(Undocked * 2.6f * cos(T * 1.13f))
                // Trinity is structural and has no positional animation.
            }
            start()
        }
    }
}
