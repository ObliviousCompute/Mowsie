package com.mowsie.app

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.text.method.PasswordTransformationMethod
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.math.sin
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.min


/** Masks every character immediately, regardless of the system's temporary
 * "show last character" preference. The underlying Editable still holds the
 * actual Secret so the crystal can reveal it on demand. */
private class NoPeekSecretMask : PasswordTransformationMethod() {
    override fun getTransformation(Source: CharSequence, View: View): CharSequence =
        object : CharSequence {
            override val length: Int get() = Source.length
            override fun get(index: Int): Char {
                Source[index] // Preserve normal index/bounds checking.
                return '\u2022'
            }
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
                require(startIndex >= 0 && endIndex >= startIndex && endIndex <= length)
                return "\u2022".repeat(endIndex - startIndex)
            }
            override fun toString(): String = "\u2022".repeat(length)
        }

    // Never insert Android's temporary Visible span after a keystroke.
    override fun onTextChanged(S: CharSequence?, Start: Int, Before: Int, Count: Int) = Unit
}

internal fun ControlPanel.ShowThreshold() {
        ExteriorQrExposed = false
        ThresholdKeyboardVisible = false
        UpdateExteriorFooter()
        findViewById<MowsieQrView>(R.id.qrPattern).visibility = View.GONE
        if (Root.findViewWithTag<View>("MowsieLock") != null) return
        HideRecovery()
        val NewNest = !Store.Exists()
        val Shared = ActiveCrystal ?: return
        val BottomDoor = findViewById<FrameLayout>(R.id.bottomDoorContainer)
        val Overlay = FrameLayout(this).apply {
            tag = "MowsieLock"
            // Render input above the REAL sealed machinery. No opaque duplicate scene.
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusableInTouchMode = true
        }

        // Position the whole scene ONCE against the unobstructed screen.
        // Gravity.TOP avoids the recentering that moved the Ω with the IME in V6.
        val Scene = FrameLayout(this)
        Overlay.addView(Scene, FrameLayout.LayoutParams(-1, Dp(405), Gravity.TOP))

        // All artwork belongs to the lower physical door in Lock.kt.
        // This invisible touch region aligns with its genuine floating crystal,
        // so Threshold has no second Omega or duplicated crystal animation.
        val CrystalRegion = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Crystal. Tap to enter Secret"
        }
        Scene.addView(CrystalRegion,
            FrameLayout.LayoutParams(Dp(244), Dp(240), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = Dp(34)
            })
        val CrystalFloat = Shared.FloatView
        val CrystalGlow = Shared.Glow
        val ErrorGlow = Shared.ErrorGlow
        val DarkCrystal = Shared.Dark
        val LightCrystal = Shared.Light
        val ShookCrystal = Shared.Shook
        val CrystalVeil = Shared.Veil
        var FailureAnimator: ValueAnimator? = null
        var ErrorFadeAnimator: ValueAnimator? = null

        // The Secret/Confirm label appears ONLY after the first crystal tap.
        // Secret sits just above the Ω feet, with typed characters just below.
        val Caption = PhosphorTextView(this).apply {
            text = "Enter your Secret"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#929EAD"))
            setShadowLayer(Dp(5).toFloat(), Dp(1).toFloat(), Dp(3).toFloat(), Color.BLACK)
            includeFontPadding = false
            alpha = 0f
            visibility = View.INVISIBLE
            isClickable = true
            contentDescription = "Secret. Tap to type"
        }
        Scene.addView(Caption, FrameLayout.LayoutParams(Dp(210), Dp(58), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = Dp(235)
        })

        // The only typing area. It is always fixed BELOW the actual Ω shape,
        // without an input box, underline, or flashing cursor.
        val Input = PhosphorEditText(this).apply {
            background = null
            hint = ""
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Accent)
            setShadowLayer(Dp(6).toFloat(), Dp(1).toFloat(), Dp(4).toFloat(), Color.BLACK)
            setPadding(Dp(5), 0, Dp(5), 0)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = NoPeekSecretMask()
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            isCursorVisible = false
            isFocusableInTouchMode = true
            showSoftInputOnFocus = true
            visibility = View.INVISIBLE // First interaction happens at the crystal.
            alpha = 0f
        }
        Scene.addView(Input, FrameLayout.LayoutParams(-1, Dp(62), Gravity.TOP).apply {
            topMargin = Dp(280)
            leftMargin = Dp(16)
            rightMargin = Dp(16)
        })
        val Feedback = PhosphorTextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.parseColor("#BAC7D8"))
            includeFontPadding = false
        }
        Scene.addView(Feedback, FrameLayout.LayoutParams(-1, Dp(31), Gravity.TOP).apply {
            topMargin = Dp(360)
        })

        // The footer is the real one attached to Door1 by ControlPanel.
        // Threshold must not draw a second, fixed footer over the machinery.
        Root.addView(Overlay, FrameLayout.LayoutParams(-1, -1))
        Overlay.requestFocus()

        // Anchor the scene for both keyboard states; never reposition it based
        // on IME height. Leave space for an ordinary keyboard on phone screens.
        var KeyboardVisible = false
        var Activated = false
        var EditingVisible = false

        // Fade only the editing UI. The Ω and crystal remain perfectly stationary,
        // and neither the entered Secret nor the chosen light state is discarded.
        // Secret remains touchable when invisible, so its original location can
        // also wake the keyboard again. The password field itself cannot be hit
        // while hidden.
        fun ShowEditing(Show: Boolean) {
            if (EditingVisible == Show) return
            EditingVisible = Show
            Caption.animate().cancel()
            Input.animate().cancel()
            if (Show) {
                Caption.visibility = View.VISIBLE
                Input.visibility = View.VISIBLE
            }
            val Target = if (Show) 1f else 0f
            val Duration = if (Show) 260L else 230L
            if (!Show) Caption.animate().alpha(0f).setDuration(Duration).start()
            Input.animate().alpha(Target).setDuration(Duration).withEndAction {
                if (!EditingVisible) Input.visibility = View.INVISIBLE
            }.start()
        }

        fun AnchorScene() {
            if (Overlay.height <= 0 || Overlay.width <= 0) return
            // One shared geometry: the Omega is 24dp down from Door1's top.
            // The overlay contains INPUTS ONLY, never a second piece of artwork.
            val Top = (BottomDoor.y + Dp(24)).toInt()
            val Layout = Scene.layoutParams as FrameLayout.LayoutParams
            if (Layout.topMargin != Top) {
                Layout.topMargin = Top
                Scene.layoutParams = Layout
            }
            val Scale = min(1f, (Overlay.width - Dp(16)).toFloat() / Dp(325).toFloat())
            Scene.pivotX = Scene.width / 2f
            Scene.pivotY = 0f
            Scene.scaleX = Scale
            Scene.scaleY = Scale
        }
        Overlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> AnchorScene() }
        BottomDoor.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> AnchorScene() }
        BottomDoor.post { AnchorScene() }
        ViewCompat.setOnApplyWindowInsetsListener(Overlay) { _, Insets ->
            KeyboardVisible = Insets.isVisible(WindowInsetsCompat.Type.ime())
            ThresholdKeyboardVisible = KeyboardVisible
            UpdateExteriorFooter()
            ShowEditing(KeyboardVisible && Activated)
            Insets // Do not pad or recenter the Ω when the keyboard appears.
        }
        ViewCompat.requestApplyInsets(Overlay)

        var Confirming = false
        var FirstSecret: CharArray? = null
        var Revealing = false
        var Working = false

        // Three independent concerns: bob = Y, rattle = X, and the error
        // treatment = temporary Shook artwork plus pink light behind/in front.
        // The base dark/light state is never overwritten by the error layer.
        fun RattleCrystal() {
            FailureAnimator?.cancel()
            ErrorFadeAnimator?.cancel()
            CrystalFloat.translationX = 0f
            CrystalFloat.scaleX = 1f
            CrystalFloat.scaleY = 1f
            ErrorGlow.Strength = 0f
            CrystalVeil.Strength = 0f
            ShookCrystal.alpha = 0f
            FailureAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 580L
                interpolator = LinearInterpolator()
                addUpdateListener { Animation ->
                    val T = Animation.animatedValue as Float
                    val Decay = 1f - T
                    // Pressure rather than a wide rattle. Small high-frequency
                    // tremor and a very faint tightening/releasing scale pulse.
                    val Pressure = kotlin.math.sin(T * Math.PI * 13.0).toFloat()
                    CrystalFloat.translationX = Pressure * Decay * Dp(2.5f)
                    val Pulse = kotlin.math.abs(Pressure) * Decay
                    CrystalFloat.scaleX = 1f + 0.009f * Pulse
                    CrystalFloat.scaleY = 1f - 0.006f * Pulse
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(Animation: android.animation.Animator) {
                        CrystalFloat.translationX = 0f
                        CrystalFloat.scaleX = 1f
                        CrystalFloat.scaleY = 1f
                        FailureAnimator = null
                    }
                })
                start()
            }
            ErrorFadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2700L
                interpolator = LinearInterpolator()
                addUpdateListener { Animation ->
                    val T = Animation.animatedValue as Float
                    fun Smooth(X: Float): Float {
                        val V = X.coerceIn(0f, 1f)
                        return V * V * (3f - 2f * V)
                    }
                    // The ember becomes fully visible after ~270ms. It holds
                    // briefly past the rattle and fades away by ~1.9 seconds.
                    val EmberIn = Smooth(T / 0.10f)
                    val EmberOut = Smooth((0.70f - T) / 0.28f)
                    ShookCrystal.alpha = EmberIn * EmberOut

                    // Rear aura and front mist share the same slow 2.7s tail.
                    val Bloom = Smooth(T / 0.14f)
                    val Fade = Smooth((1f - T) / 0.86f)
                    val Level = Bloom * Fade
                    ErrorGlow.Strength = 0.90f * Level
                    CrystalVeil.Strength = 0.85f * Level
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(Animation: android.animation.Animator) {
                        ShookCrystal.alpha = 0f
                        ErrorGlow.Strength = 0f
                        CrystalVeil.Strength = 0f
                        ErrorFadeAnimator = null
                    }
                })
                start()
            }
        }

        fun SetIllumination(Lit: Boolean) {
            Revealing = Lit
            Input.transformationMethod = if (Lit) null else NoPeekSecretMask()
            Input.setSelection(Input.text?.length ?: 0)
            LightCrystal.animate().alpha(if (Lit) 1f else 0f).setDuration(260L).start()
            DarkCrystal.animate().alpha(if (Lit) 0f else 1f).setDuration(260L).start()
            CrystalGlow.AnimateStrength(if (Lit) 0.66f else 0.015f)
        }
        fun Conceal() = SetIllumination(false)
        fun ToggleIllumination() {
            if (!Activated) return
            SetIllumination(!Revealing)
        }
        fun Activate() {
            if (!Activated) {
                Activated = true
                // Temporary gray prompt, with fixed input underneath.
                Caption.text = if (Confirming) "Confirm your Secret" else "Enter your Secret"
                Caption.visibility = View.VISIBLE
                Caption.alpha = 0f
                Caption.animate().alpha(0.88f).setDuration(200L)
                    .withEndAction {
                        Caption.animate().alpha(0f).setStartDelay(750L).setDuration(460L).start()
                    }.start()
                CrystalRegion.contentDescription = "Crystal. Tap to reveal or conceal Secret"
            }
            // Keep the invisible input ready to accept focus before IME appears.
            Input.visibility = View.VISIBLE
            Input.isCursorVisible = false
            Input.requestFocus()
            Input.post {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(Input, InputMethodManager.SHOW_IMPLICIT)
            }
            if (KeyboardVisible) ShowEditing(true)
        }
        fun HideKeyboardKeepState() {
            ShowEditing(false)
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(Input.windowToken, 0)
            Input.clearFocus()
            Overlay.requestFocus()
            // No Conceal() or input clearing: retain Secret and illumination.
        }
        CrystalRegion.setOnClickListener {
            if (Working) return@setOnClickListener
            // When the keyboard is down, a tap wakes input rather than
            // unexpectedly changing the light state of a hidden Secret.
            if (!Activated || !KeyboardVisible) Activate() else ToggleIllumination()
        }
        Caption.setOnClickListener {
            if (Working) return@setOnClickListener
            if (!Input.hasFocus() || !KeyboardVisible) Activate()
            else ToggleIllumination()
        }
        Input.setOnClickListener { if (!Working) Activate() }
        Overlay.setOnClickListener { if (!Working) HideKeyboardKeepState() }

        fun ResetToSecret(ResumeIllumination: Boolean = false) {
            FirstSecret?.fill('\u0000')
            FirstSecret = null
            Confirming = false
            Caption.text = "Enter your Secret"
            Caption.contentDescription = "Secret. Tap to type"
            Input.text?.clear()
            SetIllumination(ResumeIllumination)
        }
        ClearFrontDoor = {
            ResetToSecret() // Clear unfinished secret if the app backgrounds.
            Input.clearFocus()
            Overlay.requestFocus()
            Activated = false
            EditingVisible = false
            Input.animate().cancel()
            Input.alpha = 0f
            Input.visibility = View.INVISIBLE
            Caption.animate().cancel()
            Caption.alpha = 0f
            Caption.visibility = View.INVISIBLE
            CrystalRegion.contentDescription = "Crystal. Tap to enter Secret"
            Feedback.text = ""
            FailureAnimator?.cancel()
            FailureAnimator = null
            ErrorFadeAnimator?.cancel()
            ErrorFadeAnimator = null
            CrystalFloat.translationX = 0f
            CrystalFloat.scaleX = 1f
            CrystalFloat.scaleY = 1f
            ErrorGlow.Strength = 0f
            CrystalVeil.Strength = 0f
            ShookCrystal.alpha = 0f
        }
        fun Submit() {
            if (Working) return
            val Secret = Input.text.toString().toCharArray() // Spaces are valid, never trim.
            if (Secret.isEmpty()) {
                Secret.fill('\u0000')
                return
            }
            val WasIlluminated = Revealing
            Input.text?.clear()
            if (NewNest && !Confirming) {
                Conceal() // Confirm starts with the crystal dormant.
                FirstSecret = Secret
                Confirming = true
                Caption.text = "Confirm your Secret"
                Caption.contentDescription = "Confirm. Tap to type"
                Feedback.text = ""
                Activate() // Keep keyboard visible, don't introduce a dialog.
                return
            }
            val SelectedSecret: CharArray
            if (NewNest) {
                val Initial = FirstSecret
                if (Initial == null || !Initial.contentEquals(Secret)) {
                    Secret.fill('\u0000')
                    ResetToSecret(WasIlluminated)
                    Activate()
                    RattleCrystal() // The Shook state fades back to the previous lighting.
                    return
                }
                Secret.fill('\u0000')
                SelectedSecret = Initial
                FirstSecret = null
            } else SelectedSecret = Secret
            Working = true
            Input.isEnabled = false
            Feedback.text = if (NewNest) "Creating Genesis…" else "Unlocking…"
            Thread {
                var Error: String? = null
                var PublicKey: String? = null
                try {
                    if (NewNest) Store.GenerateAndSave(SelectedSecret)
                    val Entropy = Store.Unlock(SelectedSecret)
                    try { PublicKey = MowsieIdentity.Derive(Entropy).PublicKeyHex }
                    finally { Entropy.fill(0) }
                } catch (_: Exception) {
                    Error = if (NewNest) "Genesis creation failed. Recovery data unchanged." else
                        "Wrong Secret or device key unavailable."
                } finally { SelectedSecret.fill('\u0000') }
                runOnUiThread {
                    Working = false
                    Input.isEnabled = true
                    if (Error == null && PublicKey != null && Onscreen) {
                        ActivePublicKeyHex = PublicKey
                        RefreshPublicQr()
                        Authenticated = true
                        ThresholdKeyboardVisible = false
                        UpdateExteriorFooter() // Auto-unlock shutter has no QR or footer.
                        Shared.Illuminate(true) // The crystal lights as authentication succeeds.
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                            .hideSoftInputFromWindow(Input.windowToken, 0)
                        // Footer remains hidden through automatic unlock; it only
                        // returns for a deliberate QR reveal or the next Threshold.
                        UpdateExteriorFooter()
                        Root.removeView(Overlay)
                        ClearFrontDoor = null
                        Root.post {
                            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                UpdateLandscapeSeal(resources.configuration.orientation)
                            } else if (Authenticated) {
                                OpenDoorsOnUnlock?.invoke()
                            }
                        }
                        if (NewNest) Toast.makeText(this,
                            "Genesis created. Back up your twelve words in Recovery.", Toast.LENGTH_LONG).show()
                    } else {
                        ResetToSecret(WasIlluminated)
                        Feedback.text = ""
                        Activate()
                        RattleCrystal() // Temporary Shook state; no text dialog.
                    }
                }
            }.start()
        }
        Input.setOnEditorActionListener { _, ActionId, Event ->
            if (ActionId == EditorInfo.IME_ACTION_DONE || Event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                Submit()
                true
            } else false
        }
    }
