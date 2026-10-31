package com.mowsie.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.MotionEvent
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale


/** Android launcher and coordinator. Feature modules own their UI. */
class ControlPanel : AppCompatActivity() {
    internal enum class RecoveryMode { HIDDEN, REVEALED, EDITING }

    internal var SettingsActive = false
    internal var Authenticated = false
    internal var Onscreen = true
    internal var ChangingWords = false
    internal var RecoveryModeNow = RecoveryMode.HIDDEN
    internal var RevealedWords: List<String> = emptyList()
    internal val WordViews = mutableListOf<EditText>()
    internal val SuggestionViews = mutableListOf<TextView>()

    internal lateinit var Store: GenesisStore
    internal lateinit var Root: FrameLayout
    internal lateinit var RecoveryHint: TextView
    internal lateinit var RevealButton: TextView
    internal lateinit var WordGrid: GridLayout
    internal lateinit var RecoverySecret: EditText
    internal var RecoverySecretBusy = false
    internal var WordPulse: ObjectAnimator? = null
    internal var RecoveryNoticeRevision = 0
    internal var RecoveryAttemptRevision = 0
    internal var Accent = Color.parseColor("#73A9E4")
    internal var SecondaryAccent = Color.parseColor("#B3D0BF")
    internal var ActiveAirlock: AirlockHatchView? = null
    internal var ActiveCrystal: MountedCrystal? = null
    internal var ActiveFieldWheel: FieldWheelView? = null
    internal var ActivePeersWheel: PreviewWheel? = null
    internal var ActiveLanternsWheel: PreviewWheel? = null
    internal var ActiveCacheView: CacheActivityView? = null
    internal var ActiveScanView: ScanPage? = null
    internal var ActiveQrFeedback: TextView? = null
    internal var ActivePublicKeyHex: String? = null
    // A remembered public key does not authorize exterior QR display.
    // Only an authenticated tap on the crystal that FINISHES closing Door1 does.
    internal var ExteriorQrExposed = false
    // Threshold uses the same Door1 footer, but the keyboard may cover it.
    internal var ThresholdKeyboardVisible = false

    /** The footer is on Door1, but only Threshold or an explicitly exposed QR
     *  may show it. A normal Seal / Warp / Reveal must carry neither label. */
    internal fun UpdateExteriorFooter() {
        if (!::Root.isInitialized) return
        Root.findViewWithTag<View>("ExteriorFooter")?.visibility =
            if ((!Authenticated && !ThresholdKeyboardVisible) ||
                (Authenticated && ExteriorQrExposed)) View.VISIBLE else View.GONE
    }
    internal var ClearFrontDoor: (() -> Unit)? = null
    internal var ResetWorkspaceOnLock: (() -> Unit)? = null
    internal var OpenDoorsOnUnlock: (() -> Unit)? = null
    internal var LandscapeSealOverlay: FrameLayout? = null
    internal var LandscapeSealUpdateArtwork: (() -> Unit)? = null
    internal var LandscapeSealVisible = false
    internal var SealDoorsForRotation: (() -> Unit)? = null
    internal var ReflowDoorsAfterRotation: (() -> Unit)? = null
    // Only active on the Secret settings page. Let the Activity observe a tap
    // outside the editable word without consuming the tap's intended action.
    internal var SecretOutsideTap: ((Float, Float) -> Unit)? = null
    internal var ClearSecretPage: (() -> Unit)? = null

    override fun dispatchTouchEvent(Event: MotionEvent): Boolean {
        if (Event.actionMasked == MotionEvent.ACTION_DOWN) {
            SecretOutsideTap?.invoke(Event.rawX, Event.rawY)
        }
        return super.dispatchTouchEvent(Event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The status-bar backdrop is the root's blast-door blue, not the
        // chamber. Force white time/battery glyphs across DayNight modes.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(getColor(R.color.blast_door)))
        setContentView(R.layout.activity_main)
        Store = GenesisStore(this)
        Root = findViewById(R.id.main)
        RecoveryHint = findViewById(R.id.recoveryHint)
        EnsureLandscapeSealOverlay()
        RevealButton = findViewById(R.id.revealButton)
        WordGrid = findViewById(R.id.wordGrid)
        RecoverySecret = findViewById(R.id.recoverySecret)

        val Preferences = getSharedPreferences("MowsieNest", MODE_PRIVATE)
        Accent = Preferences.getInt("StashColor", Accent)
        SecondaryAccent = Preferences.getInt("SecondaryColor", SecondaryAccent)
        val IdentityName = findViewById<TextView>(R.id.identityName)
        val QrPattern = findViewById<MowsieQrView>(R.id.qrPattern)
        val QrPlaceholder = findViewById<TextView>(R.id.qrPlaceholder)
        val SendButton = findViewById<Button>(R.id.sendButton)
        val LogoToggle = findViewById<FrameLayout>(R.id.logoToggle)
        val BottomDoor = findViewById<FrameLayout>(R.id.bottomDoorContainer)
        val StashDoorName = findViewById<TextView>(R.id.stashDoorName)
        val FieldName = findViewById<TextView>(R.id.fieldName)
        val BalanceText = findViewById<TextView>(R.id.balanceText)
        val Hood = findViewById<LinearLayout>(R.id.hoodPanel)
        // Reparent the existing Recovery UI, never duplicate sensitive inputs.
        // Settings.kt owns its display inside the QR chamber.
        val OriginalHoodParent = Hood.parent as FrameLayout
        OriginalHoodParent.removeView(Hood)
        Hood.visibility = View.GONE
        Hood.background = null
        Hood.elevation = 0f

        // Warm the small local English dictionary in the background, without network access.
        Thread { try { Bip39Dictionary.Load(applicationContext) } catch (_: Exception) {} }.start()
        BuildWordGrid()
        RecoverySecret.setOnEditorActionListener { _, ActionId, Event ->
            if (ActionId == EditorInfo.IME_ACTION_DONE || Event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                SubmitRecoverySecret()
                true
            } else false
        }

        InstallLock(LogoToggle, Hood, BottomDoor)
        InstallVersionFooter(BottomDoor)
        RevealButton.setOnClickListener {
            if (!Authenticated || !RevealButton.isEnabled) return@setOnClickListener
            when (RecoveryModeNow) {
                RecoveryMode.HIDDEN -> {
                    if (RecoverySecret.visibility == View.VISIBLE) SubmitRecoverySecret()
                    else BeginRecoverySecret()
                }
                RecoveryMode.REVEALED -> StartRestore()
                RecoveryMode.EDITING -> SaveRestore()
            }
        }

        fun RefreshQr() { RefreshPublicQr() }
        IdentityName.text = Preferences.getString("StashName", getString(R.string.stash_name))
        StashDoorName.text = IdentityName.text
        FieldName.text = "Bob's Donuts"
        BalanceText.text = "10,485.75 Salt"
        ApplySavedAppearance()
        RefreshQr()
        // The exterior QR is display-only on tap; its copy and color gestures remain.
        QrPattern.OnCopyRequest = { CopyPublicQrImage(QrPattern) }
        // The feedback is a visual overlay only. It is not drawn in the PNG.
        val QrCopied = PhosphorTextView(this).apply {
            text = "✓ Copied"
            textSize = 20f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.parseColor("#667587"))
            setShadowLayer(Dp(3).toFloat(), 0f, Dp(1).toFloat(), Color.parseColor("#0C1521"))
            visibility = View.GONE
            alpha = 0f
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // Copy feedback follows the actual QR onto Door1, rather than appearing
        // in whichever chamber happens to be behind the sealed door.
        ActiveQrFeedback = QrCopied
        val QrDoorForFeedback = QrPattern.parent as FrameLayout
        QrDoorForFeedback.addView(QrCopied,
            FrameLayout.LayoutParams(-1, Dp(44), Gravity.TOP))
        QrPattern.OnCopySucceeded = {
            QrCopied.animate().cancel()
            QrCopied.visibility = View.VISIBLE
            QrCopied.alpha = 0.78f
            QrCopied.animate().alpha(0f).setStartDelay(580L).setDuration(400L)
                .withEndAction { QrCopied.visibility = View.GONE }.start()
        }

        SendButton.visibility = View.GONE
        HideRecovery()
        ShowThreshold()
        ViewCompat.setOnApplyWindowInsetsListener(Root) { View, Insets ->
            val Bars = Insets.getInsets(WindowInsetsCompat.Type.systemBars())
            View.setPadding(Bars.left, Bars.top, Bars.right, Bars.bottom)
            Insets
        }
        Root.isFocusable = true
        Root.isFocusableInTouchMode = true
        if (android.os.Build.VERSION.SDK_INT >= 26) Root.defaultFocusHighlightEnabled = false
        Root.post {
            Root.requestFocus()
            findViewById<View>(R.id.qrWindow).clearFocus()
            UpdateLandscapeSeal(resources.configuration.orientation)
        }
    }

    /** Appearance is local to the identity, not a Field Genesis color. */
    internal fun ApplySavedAppearance() {
        findViewById<TextView>(R.id.stashDoorName).setTextColor(Accent)
        findViewById<TextView>(R.id.fieldName).setTextColor(SecondaryAccent)
        findViewById<TextView>(R.id.balanceText).setTextColor(Accent)
        ActiveAirlock?.let {
            it.PrimaryInk = Accent
            it.SecondaryInk = SecondaryAccent
        }
        // The first wheel entry is a user-originated action; other entries
        // remain neutral regardless of the selected appearance colors.
        ActiveFieldWheel?.ActionInk = Accent
        ActivePeersWheel?.ActionInk = Accent
        ActiveLanternsWheel?.ActionInk = Accent
        ActiveCacheView?.PrimaryInk = Accent
        RevealButton.setTextColor(Accent)
        if (RecoveryModeNow == RecoveryMode.REVEALED) {
            WordViews.forEach { it.setTextColor(SecondaryAccent) }
        }
    }

    /** Footer is anchored to Door1 so it moves with the exterior QR. */
    private fun InstallVersionFooter(BottomDoor: FrameLayout) {
        val Footer = LinearLayout(this).apply {
            tag = "ExteriorFooter"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        fun FooterLine(Label: String, Size: Float, Ink: Int) = PhosphorTextView(this).apply {
            text = Label
            textSize = Size
            gravity = Gravity.CENTER
            setTextColor(Ink)
            includeFontPadding = false
            isClickable = false
        }
        Footer.addView(FooterLine("Mowsie V1", 11f, Color.parseColor("#9AAFC7")),
            LinearLayout.LayoutParams(-1, Dp(21)))
        Footer.addView(FooterLine("Powered by Oblivious Compute", 10f,
            Color.parseColor("#7187A1")), LinearLayout.LayoutParams(-1, Dp(21)))
        BottomDoor.addView(Footer, FrameLayout.LayoutParams(-1, Dp(46), Gravity.BOTTOM).apply {
            bottomMargin = Dp(58) // Fixed offset from the bottom of Door1.
        })
        UpdateExteriorFooter()
    }

    override fun onConfigurationChanged(NewConfig: Configuration) {
        super.onConfigurationChanged(NewConfig)
        // Synchronous concealment: no frame of stretched live QR/door content.
        UpdateLandscapeSeal(NewConfig.orientation)
        if (NewConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Root.post { ReflowDoorsAfterRotation?.invoke() }
        }
    }


    override fun onRequestPermissionsResult(RequestCode: Int, Permissions: Array<out String>, Results: IntArray) {
        super.onRequestPermissionsResult(RequestCode, Permissions, Results)
        if (RequestCode == ScanPage.PermissionCode) {
            ActiveScanView?.PermissionResult(Results.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED)
        }
    }

    override fun onStart() { super.onStart(); Onscreen = true }
    override fun onStop() {
        ActiveScanView?.Exit()
        Onscreen = false
        ClearFrontDoor?.invoke()
        super.onStop()
        if (Authenticated && !isChangingConfigurations) {
            Authenticated = false
            ResetWorkspaceOnLock?.invoke()
            ShowThreshold()
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                UpdateLandscapeSeal(Configuration.ORIENTATION_LANDSCAPE)
            }
        } else HideRecovery()
    }

}

internal fun ControlPanel.TitleCase(Raw: String): String =
        Raw.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

internal fun ControlPanel.HideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(Root.windowToken, 0)
        Root.requestFocus()
    }

internal fun ControlPanel.Dp(Value: Int): Int = (Value * resources.displayMetrics.density + 0.5f).toInt()
internal fun ControlPanel.Dp(Value: Float): Float = Value * resources.displayMetrics.density
