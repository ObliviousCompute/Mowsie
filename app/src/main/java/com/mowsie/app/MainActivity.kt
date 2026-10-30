package com.mowsie.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.content.FileProvider
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.HapticFeedbackConstants
import java.io.File
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Rect
import android.graphics.RectF
import android.animation.ValueAnimator
import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import android.text.method.PasswordTransformationMethod
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.PersistableBundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.cos
import kotlin.math.sin
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Locale
import kotlin.math.min

/** Mowsie V58: compact color rails and centered Recovery copy/paste notices. */
class MainActivity : AppCompatActivity() {
    private enum class RecoveryMode { HIDDEN, REVEALED, EDITING }

    private var HoodOpen = false
    private var Authenticated = false
    private var Onscreen = true
    private var ChangingWords = false
    private var RecoveryModeNow = RecoveryMode.HIDDEN
    private var RevealedWords: List<String> = emptyList()
    private val WordViews = mutableListOf<EditText>()
    private val SuggestionViews = mutableListOf<TextView>()

    private lateinit var Store: GenesisStore
    private lateinit var Root: FrameLayout
    private lateinit var RecoveryHint: TextView
    private lateinit var RevealButton: TextView
    private lateinit var WordGrid: GridLayout
    private lateinit var RecoverySecret: EditText
    private var RecoverySecretBusy = false
    private var WordPulse: ObjectAnimator? = null
    private var RecoveryNoticeRevision = 0
    private var RecoveryAttemptRevision = 0
    private var Accent = Color.parseColor("#73A9E4")
    private var SecondaryAccent = Color.parseColor("#B3D0BF")
    private var ActiveAirlock: AirlockHatchView? = null
    private var ActiveFieldWheel: FieldWheelView? = null
    private var ActivePublicKeyHex: String? = null
    private var ClearFrontDoor: (() -> Unit)? = null
    private var CloseHoodOnLock: (() -> Unit)? = null
    private var OpenDoorsOnUnlock: (() -> Unit)? = null
    private var LandscapeSealOverlay: FrameLayout? = null
    private var LandscapeSealVisible = false
    private var SealDoorsForRotation: (() -> Unit)? = null
    private var ReflowDoorsAfterRotation: (() -> Unit)? = null
    // Only active on the Secret settings page. Let the Activity observe a tap
    // outside the editable word without consuming the tap's intended action.
    private var SecretOutsideTap: ((Float, Float) -> Unit)? = null
    private var ClearSecretPage: (() -> Unit)? = null

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
        // Keep the existing recovery controls intact, but show them only from
        // Stash → Recovery inside the new, full-height hood.
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

        SetupHoodNavigation(LogoToggle, Hood, BottomDoor)
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
        var ShowingViewfinder = false
        fun ToggleQrWindow() {
            ShowingViewfinder = !ShowingViewfinder
            QrPattern.visibility = if (ShowingViewfinder) View.GONE else View.VISIBLE
            QrPlaceholder.visibility = if (ShowingViewfinder) View.VISIBLE else View.GONE
            QrPlaceholder.text = if (ShowingViewfinder) getString(R.string.scan_placeholder) else ""
        }
        QrPattern.setOnClickListener { ToggleQrWindow() }
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
        val QrWindowForFeedback = findViewById<FrameLayout>(R.id.qrWindow)
        QrWindowForFeedback.addView(QrCopied,
            FrameLayout.LayoutParams(-1, Dp(66), Gravity.CENTER))
        QrPattern.OnCopySucceeded = {
            QrCopied.animate().cancel()
            QrCopied.visibility = View.VISIBLE
            QrCopied.alpha = 0.78f
            QrCopied.animate().alpha(0f).setStartDelay(580L).setDuration(400L)
                .withEndAction { QrCopied.visibility = View.GONE }.start()
        }
        QrPlaceholder.setOnClickListener { ToggleQrWindow() }
        SendButton.visibility = View.GONE
        HideRecovery()
        ShowLockOverlay()
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

    /** Appearance is local to the Stash, never a Cache Genesis field color. */
    private fun ApplySavedAppearance() {
        findViewById<TextView>(R.id.stashDoorName).setTextColor(Accent)
        findViewById<TextView>(R.id.fieldName).setTextColor(SecondaryAccent)
        findViewById<TextView>(R.id.balanceText).setTextColor(Accent)
        ActiveAirlock?.let {
            it.PrimaryInk = Accent
            it.SecondaryInk = SecondaryAccent
        }
        ActiveFieldWheel?.FieldInk = SecondaryAccent
        RevealButton.setTextColor(Accent)
        if (RecoveryModeNow == RecoveryMode.REVEALED) {
            WordViews.forEach { it.setTextColor(SecondaryAccent) }
        }
    }

    /**
     * The hood is ONE continuous surface. Its inverted Ω lip moves down with
     * the panel, while the illuminated crystal stays pinned to the top.
     * The root heading is Settings; tapping that heading closes the hood.
     */
    private fun SetupHoodNavigation(LogoToggle: FrameLayout, RecoveryPanel: LinearLayout, BottomDoor: FrameLayout) {
        val Ink = Color.parseColor("#F0F3FA")
        val Muted = Color.parseColor("#9BABBF")
        val Path = mutableListOf<String>()

        // Keep the original 110dp header slot in the underlying Stash layout,
        // but draw the physical hood and gem as independent overlays.
        LogoToggle.visibility = View.INVISIBLE
        val Navigator = FrameLayout(this).apply {
            visibility = View.VISIBLE
            clipChildren = false
            clipToPadding = false
            isClickable = true
            contentDescription = "Retractable Mowsie settings hood"
        }
        Root.addView(Navigator, FrameLayout.LayoutParams(-1, Dp(110), Gravity.TOP))
        val UpperSurface = HoodSheetView(this)
        Navigator.addView(UpperSurface, FrameLayout.LayoutParams(-1, -1))

        // Lift the entire lower door, INCLUDING its text, out of the ordinary
        // vertical layout. This creates two true independent solid overlays.
        // Ordering: QR backdrop, shared shadows, lower door, upper hood, gem.
        val DoorShadows = DoorShadowsView(this, Navigator, BottomDoor)
        Root.addView(DoorShadows, 1, FrameLayout.LayoutParams(-1, -1))
        (BottomDoor.parent as android.view.ViewGroup).removeView(BottomDoor)
        BottomDoor.visibility = View.INVISIBLE
        Root.addView(BottomDoor, 2, FrameLayout.LayoutParams(-1, -1, Gravity.TOP))
        val LowerSurface = BottomDoor.findViewById<BottomDoorView>(R.id.bottomDoorSurface)

        val Scroll = ScrollView(this).apply {
            visibility = View.GONE
            setFillViewport(true)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        Navigator.addView(Scroll, FrameLayout.LayoutParams(-1, -1))
        // Every Door 2 screen uses one transparent, scrollable typographic
        // assembly. The title is the first row, not an independent overlay
        // tied to the crystal's 110dp touch/clip region.
        val MenuRowDp = 48
        val MenuFontSp = 20f
        val MenuTopDp = 72
        val Page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(Dp(22), Dp(MenuTopDp), Dp(22), Dp(84))
        }
        Scroll.addView(Page, FrameLayout.LayoutParams(-1, -2))

        // These layers ride the moving tab itself, NOT the stationary crystal.
        val OmegaHandle = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Omega. Return to Settings or close hood"
        }
        // V49: lift Door 2's embossed Omega 4dp without moving the door profile.
        // The clickable relief remains on the moving tab and retains its shape.
        OmegaHandle.translationY = -Dp(4).toFloat()
        Navigator.addView(OmegaHandle,
            FrameLayout.LayoutParams(Dp(190), Dp(92), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        fun OmegaLayer(Tint: Int, Alpha: Float, X: Int, Y: Int) {
            val Image = ImageView(this).apply {
                setImageResource(R.drawable.mowsieomega)
                imageTintList = ColorStateList.valueOf(Tint)
                alpha = Alpha
                scaleType = ImageView.ScaleType.FIT_CENTER
                translationX = Dp(X).toFloat()
                translationY = Dp(Y).toFloat()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            OmegaHandle.addView(Image,
                FrameLayout.LayoutParams(Dp(108), Dp(88), Gravity.CENTER))
        }
        OmegaLayer(Color.parseColor("#070C14"), 0.95f, 2, 3)
        OmegaLayer(Color.parseColor("#334252"), 0.46f, -1, -2)
        OmegaLayer(getColor(R.color.blast_door), 1f, 0, 0)

        // The crystal is separate from the sliding hood. Its vertical bob
        // continues at the same screen coordinates throughout navigation.
        val GemAnchor = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            // The transparent full-width anchor must not intercept presses
            // on the title row below it. Only the crystal itself is a button.
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        Root.addView(GemAnchor, FrameLayout.LayoutParams(-1, Dp(110), Gravity.TOP))
        val GemFloat = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // V49: lift the crystal's resting anchor by 4dp. The 3dp bob remains intact.
        GemAnchor.addView(GemFloat,
            FrameLayout.LayoutParams(Dp(138), Dp(98), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = -Dp(4)
            })
        GemFloat.addView(DreamGlowView(this).apply { Strength = 0.36f },
            FrameLayout.LayoutParams(Dp(116), Dp(101), Gravity.CENTER))
        GemFloat.addView(ImageView(this).apply {
            setImageResource(R.drawable.lightcrystal)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, FrameLayout.LayoutParams(Dp(26), Dp(62), Gravity.CENTER))
        // Tight hit area covers the actual floating crystal, but leaves
        // the first menu row clickable underneath the shared assembly.
        val GemTouch = View(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Crystal. Back one menu level"
            if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
        }
        GemAnchor.addView(GemTouch,
            FrameLayout.LayoutParams(Dp(78), Dp(78), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        var GemBob: ValueAnimator? = null
        GemFloat.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(View: View) {
                GemBob?.cancel()
                GemBob = ValueAnimator.ofFloat(-Dp(3).toFloat(), Dp(3).toFloat()).apply {
                    duration = 3600L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    addUpdateListener { GemFloat.translationY = it.animatedValue as Float }
                    start()
                }
            }
            override fun onViewDetachedFromWindow(View: View) { GemBob?.cancel(); GemBob = null }
        })

        // The highlighted title is a normal menu row. Its Primary color,
        // not a larger font or special positioning, indicates our location.
        // Refresh() inserts this SAME view at index zero on every page.
        val Selected = PhosphorTextView(this).apply {
            text = "Settings"
            gravity = Gravity.CENTER
            textSize = MenuFontSp
            includeFontPadding = false
            setTextColor(Accent)
            setShadowLayer(Dp(3).toFloat(), 0f, Dp(1).toFloat(), Color.BLACK)
            alpha = 0f
            visibility = View.GONE
            isClickable = true
            isFocusable = false
            if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
            contentDescription = "Settings. Close hood"
        }

        fun AddText(Value: String, Size: Float = 17f, ColorValue: Int = Muted) {
            Page.addView(PhosphorTextView(this).apply {
                text = Value
                textSize = Size
                setTextColor(ColorValue)
                gravity = Gravity.CENTER
                setPadding(Dp(8), Dp(5), Dp(8), Dp(5))
            }, LinearLayout.LayoutParams(-1, -2))
        }
        fun AddItem(Name: String, Click: () -> Unit) {
            Page.addView(PhosphorTextView(this).apply {
                text = Name
                textSize = MenuFontSp
                gravity = Gravity.CENTER
                setTextColor(SecondaryAccent)
                includeFontPadding = false
                setShadowLayer(Dp(3).toFloat(), 0f, Dp(1).toFloat(), Color.BLACK)
                isClickable = true
                isFocusable = false
                if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
                setOnClickListener { Click() }
            }, LinearLayout.LayoutParams(-1, Dp(MenuRowDp)))
        }
        fun AddSpacer(Height: Int) { Page.addView(View(this), LinearLayout.LayoutParams(1, Dp(Height))) }
        fun AddAction(Name: String, Click: () -> Unit) { AddItem(Name, Click) }

        // Reparent the ORIGINAL recovery UI. Never duplicate the sensitive
        // reveal/edit/restore logic in a replacement screen.
        RecoveryPanel.setPadding(Dp(1), 0, Dp(1), Dp(20))
        RecoveryPanel.visibility = View.GONE

        var NavigateTo: (String) -> Unit = {}
        var HoodAnimator: ValueAnimator? = null
        fun RootMenu() = Path.isEmpty()
        fun Current() = Path.lastOrNull() ?: "Settings"
        fun DetachRecovery() {
            (RecoveryPanel.parent as? android.view.ViewGroup)?.removeView(RecoveryPanel)
            RecoveryPanel.visibility = View.GONE
        }
        fun AddSuccessIndicator(): TextView {
            val Indicator = PhosphorStatusTextView(this).apply {
                // Center SUCCESS alone. The check is drawn to its right without
                // shifting the word's horizontal alignment.
                text = "Success"
                ShowStatusCheck = true
                translationY = -Dp(10).toFloat()
                textSize = 20f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.parseColor("#667587"))
                visibility = View.INVISIBLE
                alpha = 0f
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            // A dedicated 36dp row means Success never moves another control.
            Page.addView(Indicator, LinearLayout.LayoutParams(-1, Dp(36)))
            return Indicator
        }
        fun FlashSuccess(Indicator: TextView) {
            Indicator.animate().cancel()
            Indicator.text = "Success"
            Indicator.visibility = View.VISIBLE
            Indicator.alpha = 1f
            Indicator.animate().alpha(0f).setStartDelay(1000L).setDuration(280L)
                .withEndAction { Indicator.visibility = View.INVISIBLE }.start()
        }

        // V42: the page is its own live preview. Draft changes never escape
        // Appearance until Save. Leaving via the crystal or Omega discards them.
        fun AddAppearancePage() {
            val Preferences = getSharedPreferences("MowsieNest", MODE_PRIVATE)
            val Identity = findViewById<TextView>(R.id.identityName)
            val StashDoor = findViewById<TextView>(R.id.stashDoorName)
            var DraftName = Identity.text.toString()
            var DraftPrimary = Accent
            var DraftSecondary = SecondaryAccent
            var EditingName = false

            fun SimpleText(TextValue: String, Size: Float, Height: Int): TextView {
                return PhosphorTextView(this).apply {
                    text = TextValue
                    textSize = Size
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(DraftSecondary)
                    layoutParams = LinearLayout.LayoutParams(-1, Dp(Height))
                }
            }
            // One typography scale for every item in the Appearance menu.
            // Name is the action. The identity is a muted value until editing.
            val InactiveInk = Color.parseColor("#667587")
            val NameLabel = SimpleText("Name", MenuFontSp, MenuRowDp).apply {
                isClickable = true
                isFocusable = false
            }
            Page.addView(NameLabel, LinearLayout.LayoutParams(-1, Dp(MenuRowDp)))
            val NameEditor = PhosphorEditText(this).apply {
                setSingleLine(true)
                textSize = 20f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(Dp(4), 0, Dp(4), 0)
                background = null
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                imeOptions = EditorInfo.IME_ACTION_DONE
                filters = arrayOf(InputFilter.LengthFilter(40))
                setText(DraftName)
                setTextColor(InactiveInk)
                isCursorVisible = false
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = true
                if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
            }
            Page.addView(NameEditor, LinearLayout.LayoutParams(-1, Dp(MenuRowDp)))
            fun PaintNameState() {
                NameLabel.setTextColor(if (EditingName) InactiveInk else DraftSecondary)
                NameEditor.setTextColor(if (EditingName) DraftSecondary else InactiveInk)
            }
            fun FinishName() {
                if (!EditingName) return
                DraftName = NameEditor.text.toString()
                EditingName = false
                NameEditor.isCursorVisible = false
                NameEditor.clearFocus()
                NameEditor.isFocusable = false
                NameEditor.isFocusableInTouchMode = false
                PaintNameState()
                HideKeyboard()
            }
            fun StartName() {
                if (EditingName) return
                EditingName = true
                PaintNameState()
                NameEditor.isFocusable = true
                NameEditor.isFocusableInTouchMode = true
                NameEditor.isCursorVisible = true
                NameEditor.requestFocus()
                NameEditor.selectAll()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(NameEditor, InputMethodManager.SHOW_IMPLICIT)
            }
            NameLabel.setOnClickListener { StartName() }
            NameEditor.setOnClickListener { StartName() }
            NameEditor.setOnEditorActionListener { _, Action, Event ->
                if (Action == EditorInfo.IME_ACTION_DONE || Event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    FinishName()
                    true
                } else false
            }

            // The two unlabeled rails are Primary then Secondary. Their
            // accessible descriptions preserve the distinction for TalkBack.
            val ColorsLabel = SimpleText("Colors", MenuFontSp, MenuRowDp)
            Page.addView(ColorsLabel, LinearLayout.LayoutParams(-1, Dp(MenuRowDp)))
            val ColorRails = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            // The two color rails form one compact exception to the 48dp
            // text rhythm. Pull the pair toward Colors; Save and its Success
            // notice move upward together, preserving their exact spacing.
            Page.addView(ColorRails, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = Dp(12)
                rightMargin = Dp(12)
                topMargin = -Dp(10)
            })
            fun AddColorRail(Description: String): AppearanceHueSlider {
                val Slider = AppearanceHueSlider(this).apply {
                    contentDescription = Description
                }
                ColorRails.addView(Slider, LinearLayout.LayoutParams(-1, Dp(40)))
                return Slider
            }
            val PrimarySlider = AddColorRail("Primary color")
            val SecondarySlider = AddColorRail("Secondary color")
            val SaveText = SimpleText("Save", MenuFontSp, MenuRowDp).apply {
                isClickable = true
                isFocusable = false
            }
            Page.addView(SaveText, LinearLayout.LayoutParams(-1, Dp(MenuRowDp)))
            val Success = AddSuccessIndicator()

            fun HueOf(ColorValue: Int): Float {
                val HSV = FloatArray(3)
                Color.colorToHSV(ColorValue, HSV)
                return HSV[0]
            }
            fun ColorAtHue(HueValue: Float): Int = Color.HSVToColor(
                floatArrayOf(HueValue, 0.63f, 0.97f)
            )
            PrimarySlider.Hue = HueOf(DraftPrimary)
            SecondarySlider.Hue = HueOf(DraftSecondary)
            fun Changed(): Boolean = DraftName.trim().isNotEmpty() &&
                (DraftName.trim() != Identity.text.toString() || DraftPrimary != Accent ||
                    DraftSecondary != SecondaryAccent)
            fun PaintPreview() {
                // Primary previews on the title; Secondary previews on menu text
                // and both slider knobs. Uncommitted colors stay on this page.
                Selected.setTextColor(DraftPrimary)
                PaintNameState()
                ColorsLabel.setTextColor(DraftSecondary)
                PrimarySlider.ThumbColor = DraftSecondary
                SecondarySlider.ThumbColor = DraftSecondary
                SaveText.isEnabled = Changed()
                SaveText.setTextColor(if (SaveText.isEnabled) DraftPrimary else Color.parseColor("#667587"))
                SaveText.alpha = if (SaveText.isEnabled) 1f else 0.66f
            }
            NameEditor.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(S: CharSequence?, Start: Int, Count: Int, After: Int) {}
                override fun onTextChanged(S: CharSequence?, Start: Int, Before: Int, Count: Int) {
                    DraftName = S?.toString().orEmpty()
                    PaintPreview()
                }
                override fun afterTextChanged(S: Editable?) {}
            })
            PrimarySlider.OnHueChanged = { Hue ->
                DraftPrimary = ColorAtHue(Hue)
                PaintPreview()
            }
            SecondarySlider.OnHueChanged = { Hue ->
                DraftSecondary = ColorAtHue(Hue)
                PaintPreview()
            }
            SaveText.setOnClickListener {
                if (!Changed()) return@setOnClickListener
                val NewName = DraftName.trim()
                if (NewName.isEmpty()) return@setOnClickListener
                Accent = DraftPrimary
                SecondaryAccent = DraftSecondary
                Identity.text = NewName
                StashDoor.text = NewName
                Preferences.edit()
                    .putString("StashName", NewName)
                    .putInt("StashColor", Accent)
                    .putInt("SecondaryColor", SecondaryAccent)
                    .remove("TertiaryColor")
                    .apply()
                FinishName()
                ApplySavedAppearance()
                RefreshPublicQr()
                PaintPreview()
                FlashSuccess(Success)
            }
            PaintPreview()
        }

        // One Secret page, with exactly four words. Entries are text, not
        // Android-looking input boxes. Only the currently edited value uses
        // Secondary; committed values turn neutral gray. No cursor is drawn.
        // The Secret is a local unlock gate, never Genesis entropy.
        fun AddSecretPage() {
            val InactiveInk = Color.parseColor("#667587")
            val Fields = mutableListOf<EditText>()
            val Slots = mutableListOf<FrameLayout>()
            val Labels = mutableMapOf<EditText, TextView>()
            val Fading = mutableSetOf<EditText>()
            var Editing: EditText? = null
            var Saving = false
            var MessageRevision = 0
            val Save = PhosphorTextView(this).apply {
                text = "Save"
                textSize = 20f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(InactiveInk)
                isClickable = true
                isFocusable = false
                if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
            }

            fun Valid(): Boolean = Fields.size == 3 &&
                Fields.all { it.text.isNotEmpty() } &&
                Fields[1].text.toString() == Fields[2].text.toString()

            fun PaintSave() {
                val Ready = !Saving && Valid()
                Save.isEnabled = Ready
                Save.text = "Save"
                Save.setTextColor(if (Ready) SecondaryAccent else InactiveInk)
                Save.alpha = if (Ready) 1f else 0.72f
            }

            fun PaintFields() {
                for (Field in Fields) {
                    val Active = Field === Editing && !Saving
                    Field.setTextColor(if (Active) SecondaryAccent else InactiveInk)
                    Field.isCursorVisible = false
                    // The static label has its own layer so it fades away
                    // immediately on TAP, before any character is entered.
                    val Label = Labels[Field] ?: continue
                    if (Active) {
                        if (Label.visibility == View.VISIBLE && Fading.add(Field)) {
                            Label.animate().cancel()
                            Label.animate().alpha(0f).setDuration(115L).withEndAction {
                                Fading.remove(Field)
                                Label.visibility = View.INVISIBLE
                            }.start()
                        }
                    } else {
                        Label.animate().cancel()
                        Fading.remove(Field)
                        Label.alpha = 1f
                        Label.visibility = if (Field.text.isEmpty()) View.VISIBLE else View.INVISIBLE
                        Label.setTextColor(SecondaryAccent)
                    }
                }
            }

            fun StopEditing(HideIme: Boolean = true) {
                val Field = Editing ?: return
                Editing = null
                Field.isCursorVisible = false
                Field.clearFocus()
                Field.isFocusableInTouchMode = false
                Field.isFocusable = false
                PaintFields()
                if (HideIme) HideKeyboard()
            }

            fun BeginEditing(Field: EditText) {
                if (Saving) return
                if (Editing !== Field) StopEditing(HideIme = false)
                Editing = Field
                Field.isFocusable = true
                Field.isFocusableInTouchMode = true
                Field.isCursorVisible = false // Never show Android's flashing caret.
                Field.requestFocus()
                Field.setSelection(Field.length())
                PaintFields()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(Field, InputMethodManager.SHOW_IMPLICIT)
            }

            for (Name in listOf("Old", "New", "Confirm")) {
                val Field = PhosphorEditText(this).apply {
                    hint = "" // A separate fading overlay labels this field.
                    contentDescription = "$Name Secret"
                    setTextColor(InactiveInk)
                    textSize = 20f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(Dp(5), 0, Dp(5), 0)
                    background = null
                    // Visible Secret entry is intentional. Disable suggestions
                    // and autocorrection so entered characters aren't rewritten.
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                    filters = arrayOf(InputFilter.LengthFilter(128))
                    setSingleLine(true)
                    isCursorVisible = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isClickable = true
                    if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
                }
                val Slot = FrameLayout(this)
                Slot.addView(Field, FrameLayout.LayoutParams(-1, -1))
                val Label = PhosphorTextView(this).apply {
                    text = Name
                    textSize = 20f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(SecondaryAccent)
                    isClickable = true
                    isFocusable = false
                    if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
                }
                Slot.addView(Label, FrameLayout.LayoutParams(-1, -1))
                Slots.add(Slot)
                Fields.add(Field)
                Labels[Field] = Label
                Page.addView(Slot, LinearLayout.LayoutParams(-1, Dp(MenuRowDp)))
                Field.setOnClickListener { BeginEditing(Field) }
                Label.setOnClickListener { BeginEditing(Field) }
                Field.setOnEditorActionListener { _, Action, Event ->
                    if (Action == EditorInfo.IME_ACTION_DONE || Event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                        StopEditing()
                        true
                    } else false
                }
                Field.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(S: CharSequence?, Start: Int, Count: Int, After: Int) {}
                    override fun onTextChanged(S: CharSequence?, Start: Int, Before: Int, Count: Int) {
                        MessageRevision++
                        PaintFields()
                        if (!Saving) PaintSave()
                    }
                    override fun afterTextChanged(S: Editable?) {}
                })
            }
            Page.addView(Save, LinearLayout.LayoutParams(-1, Dp(MenuRowDp)))
            val Success = AddSuccessIndicator()
            PaintFields()
            PaintSave()

            // A tap on any empty part of the screen commits the current entry
            // to neutral gray. A tap on a different Secret word transfers the
            // keyboard without closing and reopening it. Neither tap is eaten.
            SecretOutsideTap = { X, Y ->
                val Active = Editing
                if (Active != null) {
                    val Bounds = Rect()
                    val InsideActive = Active.getGlobalVisibleRect(Bounds) &&
                        Bounds.contains(X.toInt(), Y.toInt())
                    if (!InsideActive) {
                        val SwitchingField = Fields.any { Field ->
                            if (Field === Active) false else {
                                val OtherBounds = Rect()
                                Field.getGlobalVisibleRect(OtherBounds) &&
                                    OtherBounds.contains(X.toInt(), Y.toInt())
                            }
                        }
                        StopEditing(HideIme = !SwitchingField)
                    }
                }
            }
            ClearSecretPage = {
                StopEditing()
                Fields.forEach { Field ->
                    Field.text.clear()
                    Field.isEnabled = true
                }
                Editing = null
                MessageRevision++
                SecretOutsideTap = null
            }

            Save.setOnClickListener {
                if (Saving || !Valid() || !Authenticated) return@setOnClickListener
                StopEditing()
                Saving = true
                val Old = Fields[0].text.toString().toCharArray()
                val New = Fields[1].text.toString().toCharArray()
                Fields.forEach { it.isEnabled = false }
                PaintFields()
                PaintSave()
                Thread {
                    var Changed = false
                    try { Store.ChangeSecret(Old, New); Changed = true }
                    catch (_: Exception) { /* incorrect old Secret or storage failure */ }
                    finally { Old.fill('\u0000'); New.fill('\u0000') }
                    runOnUiThread {
                        // Completion must not resurrect a page navigated away from.
                        if (Path.lastOrNull() != "Secret" || !HoodOpen || !Authenticated ||
                            Slots.any { it.parent !== Page }) return@runOnUiThread
                        Saving = false
                        Fields.forEach { it.isEnabled = true }
                        if (Changed) {
                            // Reset immediately to Old / New / Confirm / dim Save.
                            Fields.forEach { it.text.clear() }
                            HideRecovery() // The new Secret is now required for reveal.
                            PaintFields()
                            PaintSave()
                            FlashSuccess(Success)
                        } else {
                            // Keep New and Confirm for correction; clear the
                            // incorrect Old and explain why Save didn't commit.
                            Fields[0].text.clear()
                            PaintFields()
                            PaintSave()
                            Save.isEnabled = false
                            Save.text = "Incorrect Secret"
                            Save.setTextColor(InactiveInk)
                            Save.alpha = 1f
                            val Revision = ++MessageRevision
                            Save.postDelayed({
                                if (Revision == MessageRevision && Save.parent === Page) PaintSave()
                            }, 1300L)
                        }
                    }
                }.start()
            }
        }

        fun Refresh(AnimateTitle: Boolean = true) {
            ClearSecretPage?.invoke()
            ClearSecretPage = null
            SecretOutsideTap = null
            HideKeyboard()
            if (Path.lastOrNull() != "Recovery") { HideRecovery(); DetachRecovery() }
            Page.removeAllViews()
            val Name = Current()
            // Single anchor for title + options. Appearance sliders and Secret
            // entries fill the same 48dp slots as ordinary menu words.
            Page.setPadding(Dp(22), Dp(MenuTopDp), Dp(22), Dp(84))
            Selected.animate().cancel()
            Selected.text = Name
            Selected.setTextColor(Accent)
            Selected.contentDescription = if (RootMenu())
                "Settings. Close hood" else "$Name. Back one menu"
            Page.addView(Selected, LinearLayout.LayoutParams(-1, Dp(MenuRowDp)))
            if (RootMenu()) {
                listOf("Stash", "Cache", "Genesis", "Version").forEach { Entry ->
                    AddItem(Entry) { NavigateTo(Entry) }
                }
            } else when (Name) {
                "Stash" -> {
                    listOf("Appearance", "Secret", "Recovery").forEach { Entry ->
                        AddItem(Entry) { NavigateTo(Entry) }
                    }
                }
                "Appearance" -> AddAppearancePage()
                "Recovery" -> {
                    DetachRecovery()
                    RecoveryPanel.visibility = View.VISIBLE
                    // The twelve-word module sits under the title as one
                    // continuous component, with enough space for editing.
                    Page.addView(RecoveryPanel, LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = Dp(12)
                    })
                }
                "Secret" -> AddSecretPage()
                "Cache" -> {
                    listOf("Fields", "Support").forEach { Entry -> AddItem(Entry) { NavigateTo(Entry) } }
                }
                "Fields" -> { AddSpacer(18); AddText("Selected fields will appear here when synchronization is implemented.") }
                "Support" -> { AddSpacer(18); AddText("Field participation controls will appear here.") }
                "Genesis" -> {
                    AddSpacer(20)
                    AddText("Create and configure a field Genesis.")
                    AddSpacer(10)
                    AddText("Field creation is not enabled in this interface preview.", 14f)
                }
                "Version" -> {
                    AddSpacer(18)
                    AddText("Mowsie V1.0", 22f, Ink)
                    AddText("Powered by Oblivious Compute", 16f)
                    AddText("Apache License 2.0", 15f)
                }
            }
            Scroll.scrollTo(0, 0)
            Page.animate().cancel()
            Page.alpha = 0f
            Page.animate().alpha(1f).setDuration(220L).start()
            if (AnimateTitle) {
                Selected.alpha = 0f
                Selected.translationY = Dp(25).toFloat()
                Selected.animate().translationY(0f).alpha(1f).setDuration(260L)
                    .setInterpolator(DecelerateInterpolator()).start()
            } else {
                Selected.translationY = 0f
                Selected.alpha = 1f
            }
        }
        // The open stop is just below the QR. The closed stop is computed from
        // the top Ω hood's ACTUAL tooth, not from any guessed screen offset.
        var LowerClosed = true
        var LowerTravel = 0f
        var LowerAnimator: ValueAnimator? = null
        var LowerReady = false
        val QrWindow = findViewById<FrameLayout>(R.id.qrWindow)
        val QrPattern = findViewById<MowsieQrView>(R.id.qrPattern)
        val QrPlaceholder = findViewById<TextView>(R.id.qrPlaceholder)
        val StashDoorName = findViewById<TextView>(R.id.stashDoorName)
        val FieldName = findViewById<TextView>(R.id.fieldName)
        val BalanceText = findViewById<TextView>(R.id.balanceText)

        // The wheel lives in the same Oblivion gap as the QR. No extra panel.
        val FieldWheel = FieldWheelView(this).apply {
            visibility = View.GONE
            OnFieldChanged = { Field ->
                FieldName.text = Field.Name
                BalanceText.text = Field.Balance
            }
        }
        ActiveFieldWheel = FieldWheel
        QrWindow.addView(FieldWheel, FrameLayout.LayoutParams(-1, -1))

        // V23: place the Airlock behind the ENTIRE lower blast door, not on top
        // of the UI. The recess masks its shoulder and panel side edges.
        // At rest, only a matching embossed Omega crown appears in the recess.
        val Airlock = AirlockHatchView(this).apply {
            Mode = "SYNC"
            PrimaryInk = Accent
            SecondaryInk = SecondaryAccent
            isClickable = true
            isLongClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
            if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
            contentDescription = "Omega airlock hatch"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        ActiveAirlock = Airlock
        // V52: a longer sheet slides behind Door 1 without exposing its
        // bottom edge at the V51 full-open travel. The Ω anchor, profile,
        // motion and all upper-edge/shadow geometry remain unchanged.
        Root.addView(Airlock, 2,
            FrameLayout.LayoutParams(-1, Dp(470), Gravity.TOP))
        // Draw OVER the raised Airlock but UNDER the blast door. This is what
        // lets the outer blast door cast a curved shadow onto the third sheet.
        val AirlockDepth = AirlockDepthShadowsView(this, Airlock, BottomDoor, Navigator)
        Root.addView(AirlockDepth, 3, FrameLayout.LayoutParams(-1, -1))
        var ShowingFields = false
        var FieldTransitioning = false
        var AirlockOpen = false
        var AirlockMoving = false
        var AirlockPosition = 0f // 0 crown, 1 menu, -1 fully concealed
        var AirlockAnimator: ValueAnimator? = null
        var RotationRestoreHood = false
        var RotationRestoreAirlock = false
        var RestoreHoodAfterRotation: (() -> Unit)? = null

        fun PositionAirlock() {
            if (!LowerReady || Root.height <= 0 || BottomDoor.height <= 0) return
            // Same coordinate space for both Root children. No screen-position
            // guesses, and no frame-by-frame layout changes during the motion.
            val Top = BottomDoor.top - Root.paddingTop - Dp(48)
            val Params = Airlock.layoutParams as FrameLayout.LayoutParams
            if (Params.topMargin != Top) {
                Params.topMargin = Top
                Airlock.layoutParams = Params
            }
            // V51: let Door 3 travel much farther upward so the embossed Ω
            // centers over the QR chamber when fully opened.
            val Up = Dp(205) * AirlockPosition.coerceAtLeast(0f)
            val Down = Dp(100) * (-AirlockPosition).coerceAtLeast(0f)
            Airlock.translationY = BottomDoor.translationY - Up + Down
            // V52: restore menu lettering beneath the Ω, appearing only
            // as the Airlock opens. At rest the crown remains engraving-only.
            Airlock.MenuReveal = ((AirlockPosition - 0.32f) / 0.42f).coerceIn(0f, 1f)
            Airlock.clearFocus()
            AirlockDepth.postInvalidateOnAnimation()
        }

        fun MoveAirlock(Target: Float, Duration: Long, OnEnd: (() -> Unit)? = null) {
            if (AirlockMoving) return
            AirlockAnimator?.cancel()
            val From = AirlockPosition
            if (kotlin.math.abs(From - Target) < 0.005f) {
                AirlockOpen = Target > 0.5f
                OnEnd?.invoke()
                return
            }
            AirlockMoving = true
            val Animator = ValueAnimator.ofFloat(From, Target).apply {
                duration = Duration
                interpolator = DecelerateInterpolator()
                addUpdateListener { Frame ->
                    AirlockPosition = Frame.animatedValue as Float
                    PositionAirlock()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    private var Cancelled = false
                    override fun onAnimationCancel(Animation: android.animation.Animator) {
                        Cancelled = true
                        AirlockMoving = false
                    }
                    override fun onAnimationEnd(Animation: android.animation.Animator) {
                        if (!Cancelled) {
                            AirlockPosition = Target
                            AirlockOpen = Target > 0.5f
                            AirlockMoving = false
                            PositionAirlock()
                            OnEnd?.invoke()
                        }
                    }
                })
                start()
            }
            AirlockAnimator = Animator
        }

        fun AnimateAirlock(Open: Boolean, OnEnd: (() -> Unit)? = null) {
            // Expose = quick advancement. Retract = measured concealment.
            MoveAirlock(if (Open) 1f else 0f,
                if (Open) 400L else 465L, OnEnd)
        }

        // Prototype-only preview of the other two contextual menus.
        // Long-press Omega to watch the SAME hatch descend out of sight and
        // emerge again with the next menu. No actual sync, load, or send yet.
        Airlock.setOnClickListener {
            if (!HoodOpen && !FieldTransitioning && !LowerClosed && !AirlockMoving)
                AnimateAirlock(!AirlockOpen)
        }
        Airlock.setOnLongClickListener {
            if (HoodOpen || FieldTransitioning || LowerClosed || AirlockMoving) {
                false
            } else {
                val Next = when (Airlock.Mode) {
                    "SYNC" -> "LOAD"
                    "LOAD" -> "SEND"
                    else -> "SYNC"
                }
                MoveAirlock(-1f, 465L) {
                    Airlock.Mode = Next
                    MoveAirlock(1f, 400L)
                }
                true
            }
        }

        fun SetWindowFields(Visible: Boolean) {
            ShowingFields = Visible
            FieldWheel.visibility = if (Visible) View.VISIBLE else View.GONE
            if (Visible) {
                QrPattern.visibility = View.GONE
                QrPlaceholder.visibility = View.GONE
            } else {
                QrPattern.visibility = View.VISIBLE
                QrPlaceholder.visibility = View.GONE
            }
        }

        fun UpdateDoorLighting() {
            val UpperLocation = IntArray(2)
            val LowerLocation = IntArray(2)
            Navigator.getLocationOnScreen(UpperLocation)
            BottomDoor.getLocationOnScreen(LowerLocation)
            val Density = resources.displayMetrics.density
            val UpperTip = UpperLocation[1] + Navigator.height - 3f * Density
            val LowerSeat = LowerLocation[1] + 44f * Density
            val Gap = LowerSeat - UpperTip
            val Mate = (1f - Gap / (13f * Density)).coerceIn(0f, 1f)
            UpperSurface.Mate = Mate
            LowerSurface.Mate = Mate
            DoorShadows.postInvalidateOnAnimation()
            PositionAirlock()
        }

        fun MeasureLowerDoor() {
            if (Root.height <= 0 || QrWindow.height <= 0) return
            val RootPos = IntArray(2)
            val QrPos = IntArray(2)
            val HoodPos = IntArray(2)
            Root.getLocationOnScreen(RootPos)
            QrWindow.getLocationOnScreen(QrPos)
            Navigator.getLocationOnScreen(HoodPos)

            // FrameLayout lays children out INSIDE Root's inset padding.
            // Preserve V33's 32dp open-stop clearance. V49 raises the whole
            // QR window by 5dp, and getLocationOnScreen includes translationY.
            // Compensate that 5dp before lowering Door 1's OPEN stop another
            // 4dp. The closed stop, mating contour and sealed silhouette stay.
            val QrLiftCompensation = Dp(5)
            val DoorOpenLowering = Dp(4)
            val OpenTop = (QrPos[1] + QrWindow.height + Dp(8) + Dp(32) +
                QrLiftCompensation + DoorOpenLowering -
                RootPos[1] - Root.paddingTop).toFloat()
            val UpperTip = (HoodPos[1] + Dp(110) - Dp(3) -
                RootPos[1] - Root.paddingTop).toFloat()
            val ClosedTop = UpperTip - Dp(44)
            LowerTravel = (OpenTop - ClosedTop).coerceAtLeast(0f)

            // Oversized lower face extends OFF screen below the visible edge.
            // Raising the door cannot expose a strip of Oblivion at the bottom.
            val FullHeight = (Root.height - Root.paddingTop - Root.paddingBottom -
                ClosedTop + Dp(40)).toInt().coerceAtLeast(Dp(200))
            val Params = BottomDoor.layoutParams as FrameLayout.LayoutParams
            Params.topMargin = OpenTop.toInt()
            Params.height = FullHeight
            BottomDoor.layoutParams = Params
            BottomDoor.translationY = if (LowerClosed) -LowerTravel else 0f
            BottomDoor.visibility = View.VISIBLE
            LowerReady = true
            UpdateDoorLighting()
            Root.post { PositionAirlock() }
        }

        fun AnimateLower(CloseDoor: Boolean, OnEnd: (() -> Unit)? = null) {
            if (!LowerReady) return
            LowerAnimator?.cancel()
            LowerClosed = CloseDoor
            val Target = if (CloseDoor) -LowerTravel else 0f
            if (kotlin.math.abs(BottomDoor.translationY - Target) < 1f) {
                BottomDoor.translationY = Target
                UpdateDoorLighting()
                OnEnd?.invoke()
                return
            }
            val Animation = ValueAnimator.ofFloat(BottomDoor.translationY, Target).apply {
                duration = if (CloseDoor) 465L else 850L
                interpolator = DecelerateInterpolator()
                addUpdateListener { Frame ->
                    BottomDoor.translationY = Frame.animatedValue as Float
                    UpdateDoorLighting()
                }
                if (OnEnd != null) {
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        private var Cancelled = false
                        override fun onAnimationCancel(Animation: android.animation.Animator) {
                            Cancelled = true
                        }
                        override fun onAnimationEnd(Animation: android.animation.Animator) {
                            if (!Cancelled && LowerClosed == CloseDoor) OnEnd()
                        }
                    })
                }
                start()
            }
            LowerAnimator = Animation
        }
        // Metronome remains the direct open/close control for the lower door.
        StashDoorName.setOnClickListener {
            if (HoodOpen || FieldTransitioning) return@setOnClickListener
            if (AirlockOpen) AnimateAirlock(false)
            if (ShowingFields && !LowerClosed) {
                AnimateLower(true) { SetWindowFields(false) }
            } else {
                AnimateLower(!LowerClosed)
            }
        }
        // The field name performs the deliberate close -> change contents -> reopen.
        FieldName.setOnClickListener {
            if (HoodOpen || FieldTransitioning) return@setOnClickListener
            if (AirlockOpen) AnimateAirlock(false)
            FieldTransitioning = true
            if (ShowingFields && !LowerClosed) {
                AnimateLower(true) {
                    SetWindowFields(false)
                    FieldTransitioning = false
                }
            } else {
                AnimateLower(true) {
                    SetWindowFields(true)
                    AnimateLower(false) { FieldTransitioning = false }
                }
            }
        }
        FieldWheel.OnCenterTapped = {
            if (!FieldTransitioning && !HoodOpen) FieldName.performClick()
        }
        Root.post { MeasureLowerDoor() }
        ReflowDoorsAfterRotation = { Root.post { MeasureLowerDoor() } }
        SealDoorsForRotation = {
            // Seal sensitive edit fields when the orientation shutter closes.
            ClearSecretPage?.invoke()
            ClearSecretPage = null
            RotationRestoreHood = HoodOpen
            RotationRestoreAirlock = AirlockOpen
            LowerAnimator?.cancel()
            LowerClosed = true
            BottomDoor.translationY = -LowerTravel
            AirlockAnimator?.cancel()
            AirlockMoving = false
            AirlockOpen = false
            AirlockPosition = 0f
            HoodAnimator?.cancel()
            HoodOpen = false
            Selected.animate().cancel()
            Selected.visibility = View.GONE
            Scroll.visibility = View.GONE
            Navigator.layoutParams = Navigator.layoutParams.apply { height = Dp(110) }
            PositionAirlock()
            UpdateDoorLighting()
            // Preserve the menu path, field wheel, and identity. This is not a lock.
        }
        // The scene begins with its blast doors sealed. Only after a successful
        // Secret unlock does the lower slab descend to reveal the QR chamber.
        OpenDoorsOnUnlock = {
            // Present the sealed silhouette for half a second after unlock,
            // then let the heavy lower blast door reveal the QR chamber.
            // Recheck state in case the app relocks during the pause.
            BottomDoor.postDelayed({
                if (Authenticated && Onscreen && LowerReady && LowerClosed && !HoodOpen &&
                    resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                    AnimateLower(false) {
                        if (RotationRestoreHood) {
                            RotationRestoreHood = false
                            RotationRestoreAirlock = false
                            RestoreHoodAfterRotation?.invoke()
                        } else if (RotationRestoreAirlock) {
                            RotationRestoreAirlock = false
                            AnimateAirlock(true)
                        }
                    }
                }
            }, 500L)
        }

        fun OpenHeight(): Int {
            // Measure both panels in the same on-screen coordinate space.
            // The window insets shift Navigator downward from Root's origin,
            // so measuring relative to Root was the source of V16's offset.
            val HoodLocation = IntArray(2)
            val DoorLocation = IntArray(2)
            Navigator.getLocationOnScreen(HoodLocation)
            BottomDoor.getLocationOnScreen(DoorLocation)
            val DoorTop = DoorLocation[1] - HoodLocation[1]

            // Hood base = Hood height - 44dp, hood tooth tip = height - 3dp.
            // Lower recess begins 3dp below its top, ending 44dp below it.
            // Therefore height = DoorTop + 47dp lands each curve precisely
            // on its mirrored counterpart with no hard-coded screen offset.
            return (DoorTop + Dp(47)).coerceAtLeast(Dp(110))
        }
        fun AnimateHood(ToHeight: Int, Duration: Long, OnEnd: (() -> Unit)? = null) {
            HoodAnimator?.cancel()
            val From = Navigator.layoutParams.height
            HoodAnimator = ValueAnimator.ofInt(From, ToHeight).apply {
                duration = Duration
                interpolator = DecelerateInterpolator()
                addUpdateListener { Frame ->
                    val Params = Navigator.layoutParams
                    Params.height = Frame.animatedValue as Int
                    Navigator.layoutParams = Params
                    UpdateDoorLighting()
                }
                if (OnEnd != null) {
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(Animation: android.animation.Animator) {
                            if (Navigator.layoutParams.height == ToHeight) OnEnd()
                        }
                    })
                }
                start()
            }
        }
        fun Open() {
            if (AirlockOpen) AnimateAirlock(false)
            if (LowerClosed) {
                AnimateLower(false) { Open() }
                return
            }
            HoodOpen = true
            Navigator.visibility = View.VISIBLE
            Scroll.visibility = View.VISIBLE
            Selected.visibility = View.VISIBLE
            Selected.alpha = 0f
            Selected.translationY = 0f
            Navigator.layoutParams = Navigator.layoutParams.apply { height = Dp(110) }
            Refresh(false)
            Selected.alpha = 0f
            Selected.animate().alpha(1f).setStartDelay(115L).setDuration(240L).start()
            Page.alpha = 0f
            Page.animate().alpha(1f).setStartDelay(135L).setDuration(260L).start()
            AnimateHood(OpenHeight(), 480L)
        }
        RestoreHoodAfterRotation = { Open() }
        fun Close() {
            ClearSecretPage?.invoke()
            ClearSecretPage = null
            AnimateAirlock(false)
            HideRecovery()
            DetachRecovery()
            Path.clear()
            HoodOpen = false
            Selected.animate().cancel()
            Selected.animate().alpha(0f).setDuration(180L).start()
            Page.animate().cancel()
            Page.animate().alpha(0f).setDuration(150L).start()
            AnimateHood(Dp(110), 465L) {
                if (!HoodOpen) {
                    Scroll.visibility = View.GONE
                    Selected.visibility = View.GONE
                }
            }
        }
        fun BackOne() {
            if (Path.isEmpty()) { Close(); return }
            if (Path.last() == "Recovery") DetachRecovery()
            Path.removeAt(Path.lastIndex)
            Refresh()
        }
        fun MoveToRoot() {
            if (Path.isNotEmpty()) {
                DetachRecovery()
                Path.clear()
                Refresh()
            } else Close()
        }
        NavigateTo = { Name -> Path.add(Name); Refresh() }
        CloseHoodOnLock = {
            ClearSecretPage?.invoke()
            ClearSecretPage = null
            LowerAnimator?.cancel()
            LowerClosed = true
            BottomDoor.translationY = -LowerTravel
            SetWindowFields(false)
            FieldTransitioning = false
            AirlockAnimator?.cancel()
            AirlockMoving = false
            AirlockOpen = false
            AirlockPosition = 0f
            PositionAirlock()
            UpdateDoorLighting()
            HoodAnimator?.cancel()
            Path.clear()
            HoodOpen = false
            HideRecovery()
            DetachRecovery()
            Navigator.visibility = View.VISIBLE
            Scroll.visibility = View.GONE
            Navigator.layoutParams = Navigator.layoutParams.apply { height = Dp(110) }
            Selected.animate().cancel()
            Selected.alpha = 0f
            Selected.visibility = View.GONE
            Root.requestFocus()
        }
        Selected.setOnClickListener { if (HoodOpen) BackOne() else Open() }
        GemTouch.setOnClickListener { if (!HoodOpen) Open() else BackOne() }
        OmegaHandle.setOnClickListener { if (HoodOpen) Close() else Open() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (HoodOpen) {
                    if (RootMenu()) Close() else BackOne()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }


    private fun EnsureLandscapeSealOverlay() {
        if (LandscapeSealOverlay != null) return
        val Overlay = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(getColor(R.color.blast_door))
            isClickable = true
            isFocusable = true
            contentDescription = "Mowsie sealed in landscape"
            clipChildren = false
            clipToPadding = false
        }
        Overlay.addView(LandscapeSealedDoorsView(this), FrameLayout.LayoutParams(-1, -1))

        // One original-size illuminated crystal, floating above the single Ω.
        // Its Ω is drawn by the sealed-door surface, so we cannot get two glyphs.
        val CrystalFloat = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        // Match the portrait control-screen crystal relationship more closely.
        // Use the same roomy float box and a gentler upward offset so the gem
        // sits in front of the Ω rather than drifting too high above it.
        Overlay.addView(CrystalFloat,
            FrameLayout.LayoutParams(Dp(138), Dp(98), Gravity.CENTER).apply {
                topMargin = -Dp(24)
            })
        CrystalFloat.addView(DreamGlowView(this).apply { Strength = 0.36f },
            FrameLayout.LayoutParams(Dp(116), Dp(101), Gravity.CENTER))
        CrystalFloat.addView(ImageView(this).apply {
            setImageResource(R.drawable.lightcrystal)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(Dp(26), Dp(62), Gravity.CENTER))
        var BobAnimator: ValueAnimator? = null
        Overlay.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(View: View) {
                BobAnimator?.cancel()
                BobAnimator = ValueAnimator.ofFloat(-Dp(3).toFloat(), Dp(3).toFloat()).apply {
                    duration = 3600L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    addUpdateListener { CrystalFloat.translationY = it.animatedValue as Float }
                    start()
                }
            }
            override fun onViewDetachedFromWindow(View: View) {
                BobAnimator?.cancel()
                BobAnimator = null
            }
        })
        Root.addView(Overlay, FrameLayout.LayoutParams(-1, -1))
        LandscapeSealOverlay = Overlay
    }

    private fun UpdateLandscapeSeal(Orientation: Int) {
        val Overlay = LandscapeSealOverlay ?: return
        if (Orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (!LandscapeSealVisible) {
                // Save the existing workspace behind a fully opaque shutter.
                // Unlike V35, do NOT invoke the security-lock reset here.
                if (Authenticated) SealDoorsForRotation?.invoke()
                LandscapeSealVisible = true
            }
            // V35 added this surface early, before creating doors and lock UI.
            // They subsequently rendered OVER it and leaked two Omegas/crystals.
            // Keep the sealed landscape machine above every workspace layer.
            Overlay.bringToFront()
            Overlay.visibility = View.VISIBLE
        } else if (LandscapeSealVisible) {
            Overlay.visibility = View.GONE
            LandscapeSealVisible = false
            if (Authenticated) {
                // Recalculate portrait stops before starting the existing
                // half-second pause and 850ms opening sequence.
                ReflowDoorsAfterRotation?.invoke()
                OpenDoorsOnUnlock?.invoke()
            }
        }
    }

    override fun onConfigurationChanged(NewConfig: Configuration) {
        super.onConfigurationChanged(NewConfig)
        // Synchronous concealment: no frame of stretched live QR/door content.
        UpdateLandscapeSeal(NewConfig.orientation)
        if (NewConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Root.post { ReflowDoorsAfterRotation?.invoke() }
        }
    }

    private fun RefreshPublicQr() {
        val Key = ActivePublicKeyHex
        val Name = findViewById<TextView>(R.id.identityName).text.toString()
        val Payload = if (Key == null) {
            "MOWSIE CONTACT V1\nUnlock to show public key"
        } else {
            // This is a REAL deterministic Ed25519 public key, but not yet a Salt address.
            "MOWSIE PUBLIC KEY V1\n" +
                "Algorithm: Ed25519\n" +
                "Derivation: m/0'/0'\n" +
                "Stash: $Name\n" +
                "Public Key: $Key"
        }
        try { findViewById<MowsieQrView>(R.id.qrPattern).SetPayload(Payload) }
        catch (_: WriterException) {
            Toast.makeText(this, "Unable to generate public key QR", Toast.LENGTH_LONG).show()
        }
    }

    /** Copy an image, not a string. The PNG contains only the encoded QR and its
     * quiet zone, with the current visual color, never the on-screen Copied text. */
    private fun CopyPublicQrImage(Qr: MowsieQrView): Boolean {
        val Image = Qr.MakeClipboardBitmap() ?: return false
        return try {
            val Folder = File(cacheDir, "qrcopies").apply { mkdirs() }
            val Filename = "MowsieQr${System.currentTimeMillis()}.png"
            val FileOut = File(Folder, Filename)
            FileOut.outputStream().use { Stream ->
                if (!Image.compress(Bitmap.CompressFormat.PNG, 100, Stream)) {
                    throw java.io.IOException("PNG encoding failed")
                }
            }
            val Uri = FileProvider.getUriForFile(this, "$packageName.qrfiles", FileOut)
            val Clip = ClipData.newUri(contentResolver, "Mowsie public QR", Uri)
            val Clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            Clipboard.setPrimaryClip(Clip)
            Qr.OnCopySucceeded?.invoke()
            true
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to copy QR image", Toast.LENGTH_SHORT).show()
            false
        } finally {
            Image.recycle()
        }
    }

    private fun BuildWordGrid() {
        for (Index in 0 until 12) {
            val Word = PhosphorEditText(this).apply {
                textSize = 13f
                background = null
                setPadding(0, 0, 0, 0)
                includeFontPadding = false
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_ACTION_NEXT
                filters = arrayOf(InputFilter.LengthFilter(150))
                setTextColor(Color.parseColor("#AABBCD"))
                isCursorVisible = false
                gravity = Gravity.CENTER
                isFocusable = false
                isFocusableInTouchMode = false
            }
            val Suggestion = PhosphorTextView(this).apply {
                textSize = 12f
                includeFontPadding = false
                setTextColor(Color.parseColor("#778FA7"))
                gravity = Gravity.CENTER_VERTICAL
                visibility = View.GONE
            }
            val Cell = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                val WordArea = FrameLayout(this@MainActivity)
                WordArea.addView(Word, FrameLayout.LayoutParams(-1, -1))
                WordArea.addView(Suggestion, FrameLayout.LayoutParams(-2, -1, Gravity.START))
                addView(WordArea, LinearLayout.LayoutParams(0, -1, 1f))
            }
            val Params = GridLayout.LayoutParams(
                GridLayout.spec(Index / 3), GridLayout.spec(Index % 3, 1f)
            ).apply {
                width = 0
                height = Dp(45)
                leftMargin = Dp(2)
                rightMargin = Dp(2)
            }
            WordGrid.addView(Cell, Params)
            WordViews.add(Word)
            SuggestionViews.add(Suggestion)

            val Listener = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (ChangingWords || RecoveryModeNow != RecoveryMode.EDITING) return
                    val Text = s?.toString().orEmpty()
                    if (Text.any { it.isWhitespace() } || Text.length > 12) {
                        if (ApplyPastedWords(Text)) return
                    }
                    val Titled = TitleCase(Text)
                    if (Titled != Text) {
                        ChangingWords = true
                        Word.setText(Titled)
                        Word.setSelection(Word.length())
                        ChangingWords = false
                    }
                    OnWordTyped(Index)
                }
            }
            Word.addTextChangedListener(Listener)
            Word.setOnEditorActionListener { _, ActionId, Event ->
                if (RecoveryModeNow == RecoveryMode.EDITING &&
                    (ActionId == EditorInfo.IME_ACTION_NEXT || ActionId == EditorInfo.IME_ACTION_DONE ||
                        Event?.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    CompleteWord(Index)
                    true
                } else false
            }
            Word.setOnFocusChangeListener { _, HasFocus ->
                if (RecoveryModeNow == RecoveryMode.EDITING && HasFocus) HighlightWord(Index)
            }
            Cell.setOnClickListener {
                when (RecoveryModeNow) {
                    RecoveryMode.REVEALED -> CopyRecovery()
                    RecoveryMode.EDITING -> FocusWord(Index)
                    RecoveryMode.HIDDEN -> Unit
                }
            }
            Word.setOnClickListener {
                if (RecoveryModeNow == RecoveryMode.REVEALED) CopyRecovery()
            }
            // Holding ANY part of the twelve-slot restore grid pastes all 12.
            val Paste = View.OnLongClickListener { PasteRecoveryFromClipboard() }
            Cell.setOnLongClickListener(Paste)
            Word.setOnLongClickListener(Paste)
            Suggestion.setOnLongClickListener(Paste)
            Cell.isLongClickable = true
            Word.isLongClickable = true
        }
        WordGrid.setOnClickListener {
            if (RecoveryModeNow == RecoveryMode.REVEALED) CopyRecovery()
        }
        WordGrid.setOnLongClickListener { PasteRecoveryFromClipboard() }
        WordGrid.isLongClickable = true
    }

    private fun ShowRecoveryNotice(Message: String, Duration: Long = 1800L) {
        val Revision = ++RecoveryNoticeRevision
        RecoveryHint.animate().cancel()
        // Keep the STATUS WORD precisely centered under Reveal / Restore / Save.
        // Draw the check outside its right edge so Copied and Pasted, just like
        // Success, are not shifted sideways by the glyph. Success keeps its
        // separate 10dp lift; Copy and Paste retain their prior vertical spot.
        val CheckedWord = when (Message) {
            "✓ Success" -> "Success"
            "✓ Copied" -> "Copied"
            "✓ Pasted" -> "Pasted"
            else -> null
        }
        (RecoveryHint as? PhosphorStatusTextView)?.ShowStatusCheck = CheckedWord != null
        RecoveryHint.text = CheckedWord ?: Message
        RecoveryHint.translationY = if (CheckedWord == "Success") -Dp(10).toFloat() else 0f
        RecoveryHint.alpha = 1f
        RecoveryHint.visibility = if (Message.isBlank()) View.INVISIBLE else View.VISIBLE
        if (Message.isBlank() || Duration <= 0L) return
        RecoveryHint.postDelayed({
            if (RecoveryNoticeRevision != Revision) return@postDelayed
            // The active Secret prompt remains in the indicator position.
            // An incorrect Secret briefly overrides it, then returns it.
            if (RecoveryModeNow == RecoveryMode.HIDDEN &&
                RecoverySecret.visibility == View.VISIBLE) {
                if (Message != "Enter your Secret") ShowRecoveryNotice("Enter your Secret", 0L)
                return@postDelayed
            }
            RecoveryHint.animate().alpha(0f).setDuration(260L).withEndAction {
                if (RecoveryNoticeRevision == Revision) RecoveryHint.visibility = View.INVISIBLE
            }.start()
        }, Duration)
    }

    private fun StopWordPulse() {
        WordPulse?.cancel()
        WordPulse = null
        for (Word in WordViews) Word.alpha = 1f
    }

    private fun PulseFocusedEllipsis(Index: Int) {
        StopWordPulse()
        if (RecoveryModeNow != RecoveryMode.EDITING ||
            Index !in WordViews.indices || WordViews[Index].text.isNotEmpty()) return
        val Word = WordViews[Index]
        WordPulse = ObjectAnimator.ofFloat(Word, View.ALPHA, 1f, 0.25f, 1f).apply {
            duration = 1150L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

    private fun BeginRecoverySecret() {
        if (RecoveryModeNow != RecoveryMode.HIDDEN || RecoverySecretBusy) return
        RecoverySecret.text.clear()
        RevealButton.visibility = View.GONE
        RecoverySecret.visibility = View.VISIBLE
        ShowRecoveryNotice("Enter your Secret", 0L)
        RecoverySecret.requestFocus()
        RecoverySecret.post {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(RecoverySecret, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun SubmitRecoverySecret() {
        if (RecoveryModeNow != RecoveryMode.HIDDEN || RecoverySecretBusy ||
            RecoverySecret.visibility != View.VISIBLE) return
        val Secret = RecoverySecret.text.toString().toCharArray()
        RecoverySecret.text.clear()
        if (Secret.isEmpty()) {
            Secret.fill('\u0000')
            ShowRecoveryNotice("Enter your Secret")
            return
        }
        RecoverySecretBusy = true
        val Attempt = ++RecoveryAttemptRevision
        RevealButton.isEnabled = false
        RevealButton.alpha = 0.40f
        Thread {
            var Words: List<String>? = null
            try {
                val Entropy = Store.Unlock(Secret)
                try { Words = GenesisWords.FromEntropy(Entropy) }
                finally { Entropy.fill(0) }
            } catch (_: Exception) { /* wrong Secret or Genesis unavailable */ }
            finally { Secret.fill('\u0000') }
            runOnUiThread {
                if (Attempt != RecoveryAttemptRevision) return@runOnUiThread
                RecoverySecretBusy = false
                if (Words != null && Authenticated && HoodOpen &&
                    RecoveryModeNow == RecoveryMode.HIDDEN && RecoverySecret.visibility == View.VISIBLE) {
                    ShowRevealedWords(Words!!)
                } else if (RecoveryModeNow == RecoveryMode.HIDDEN) {
                    UpdateRecoveryControls()
                    if (RecoverySecret.visibility == View.VISIBLE) {
                        ShowRecoveryNotice("Incorrect Secret", 1100L)
                        RecoverySecret.requestFocus()
                    }
                }
            }
        }.start()
    }

    private fun PasteRecoveryFromClipboard(): Boolean {
        if (RecoveryModeNow != RecoveryMode.EDITING) return false
        val Clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val Clip = Clipboard.primaryClip ?: run {
            ShowRecoveryNotice("Clipboard is empty")
            return true
        }
        if (Clip.itemCount < 1) return true
        val Text = Clip.getItemAt(0).coerceToText(this).toString()
        if (!ApplyPastedWords(Text)) return true
        ShowRecoveryNotice("✓ Pasted", 1250L)
        return true
    }

    private fun UpdateRecoveryControls() {
        RevealButton.text = when (RecoveryModeNow) {
            RecoveryMode.HIDDEN -> "Reveal"
            RecoveryMode.REVEALED -> "Restore"
            RecoveryMode.EDITING -> "Save"
        }
        val EnteringSecret = RecoveryModeNow == RecoveryMode.HIDDEN &&
            RecoverySecret.visibility == View.VISIBLE
        RevealButton.visibility = if (EnteringSecret) View.GONE else View.VISIBLE
        if (RecoveryModeNow != RecoveryMode.EDITING) RevealButton.isEnabled = !RecoverySecretBusy
        RevealButton.alpha = if (RevealButton.isEnabled) 1f else 0.38f
    }

    private fun HideRecovery() {
        HideKeyboard()
        StopWordPulse()
        RecoveryModeNow = RecoveryMode.HIDDEN
        RecoverySecretBusy = false
        RecoveryAttemptRevision++
        RecoverySecret.clearFocus()
        RecoverySecret.text.clear()
        RecoverySecret.visibility = View.GONE
        RecoveryNoticeRevision++
        RecoveryHint.animate().cancel()
        RecoveryHint.visibility = View.INVISIBLE
        RevealedWords = emptyList()
        ChangingWords = true
        for (Index in WordViews.indices) {
            WordViews[Index].hint = ""
            WordViews[Index].setText("···")
            WordViews[Index].gravity = Gravity.CENTER
            WordViews[Index].setTextColor(Color.parseColor("#9FB2C8"))
            WordViews[Index].isFocusable = false
            WordViews[Index].isFocusableInTouchMode = false
            WordViews[Index].isCursorVisible = false
        }
        ChangingWords = false
        UpdateRecoveryControls()
    }

    private fun ShowRevealedWords(Words: List<String>) {
        HideKeyboard()
        StopWordPulse()
        RecoverySecret.clearFocus()
        RecoverySecret.text.clear()
        RecoverySecret.visibility = View.GONE
        RecoveryNoticeRevision++
        RecoveryHint.animate().cancel()
        RecoveryHint.visibility = View.INVISIBLE
        RevealedWords = Words.toList()
        RecoveryModeNow = RecoveryMode.REVEALED
        ChangingWords = true
        for (Index in WordViews.indices) {
            val Word = WordViews[Index]
            Word.isFocusable = false
            Word.isFocusableInTouchMode = false
            Word.isCursorVisible = false
            Word.hint = ""
            Word.setText(GenesisWords.Display(Words[Index]))
            Word.gravity = Gravity.CENTER
            Word.setTextColor(SecondaryAccent)
        }
        ChangingWords = false
        UpdateRecoveryControls()
    }

    private fun StartRestore() {
        if (RecoveryModeNow != RecoveryMode.REVEALED) return
        if (!Bip39Dictionary.IsReady()) {
            RevealButton.isEnabled = false
            RevealButton.alpha = 0.38f
            ShowRecoveryNotice("Preparing recovery dictionary…", 2500L)
            Thread {
                var Loaded = true
                try { Bip39Dictionary.Load(applicationContext) } catch (_: Exception) { Loaded = false }
                runOnUiThread {
                    RevealButton.isEnabled = true
                    if (Loaded && Authenticated && HoodOpen && RecoveryModeNow == RecoveryMode.REVEALED) {
                        EnterRestore()
                    } else if (!Loaded) ShowRecoveryNotice("Unable to load recovery dictionary.", 2800L)
                }
            }.start()
        } else EnterRestore()
    }

    private fun EnterRestore() {
        RecoveryModeNow = RecoveryMode.EDITING
        ChangingWords = true
        for (Index in WordViews.indices) {
            WordViews[Index].setText("")
            WordViews[Index].hint = "···"
            WordViews[Index].gravity = Gravity.CENTER
            WordViews[Index].setHintTextColor(Color.parseColor("#52667C"))
            WordViews[Index].isFocusable = true
            WordViews[Index].isFocusableInTouchMode = true
            WordViews[Index].isCursorVisible = false
            WordViews[Index].setTextColor(Color.parseColor("#AABBCD"))
        }
        ChangingWords = false
        RecoveryNoticeRevision++
        RecoveryHint.visibility = View.INVISIBLE
        UpdateRecoveryControls()
        FocusWord(0)
        UpdateSaveState()
    }

    private fun FocusWord(Index: Int) {
        if (RecoveryModeNow != RecoveryMode.EDITING) return
        val Target = WordViews[Index]
        Target.requestFocus()
        Target.setSelection(Target.length())
        HighlightWord(Index)
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(Target, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun HighlightWord(Index: Int) {
        for (I in WordViews.indices) {
            val Selected = I == Index
            WordViews[I].setTextColor(if (Selected || WordViews[I].text.isNotEmpty())
                SecondaryAccent else Color.parseColor("#8193A7"))
            WordViews[I].setHintTextColor(if (Selected) SecondaryAccent else Color.parseColor("#52667C"))
        }
        PulseFocusedEllipsis(Index)
        ShowSuggestion(Index)
    }

    private fun ShowSuggestion(Index: Int) {
        val Raw = WordViews[Index].text.toString().lowercase(Locale.ROOT)
        val Suggest = Bip39Dictionary.Completion(applicationContext, Raw)
        for (Item in SuggestionViews) Item.visibility = View.GONE
        if (Raw.length >= 4 && Suggest != null && Suggest.length > Raw.length) {
            // Completion is shown below the action. Never offset a word in the grid.
            ShowRecoveryNotice("↵ ${GenesisWords.Display(Suggest)}", 2200L)
        } else if (Raw.isNotEmpty() && !Bip39Dictionary.IsWord(applicationContext, Raw) && Suggest == null) {
            ShowRecoveryNotice("Not in the BIP39 dictionary", 2200L)
        } else {
            ShowRecoveryNotice("", 0L)
        }
    }

    private fun OnWordTyped(Index: Int) {
        if (RecoveryModeNow != RecoveryMode.EDITING) return
        val Raw = WordViews[Index].text.toString().lowercase(Locale.ROOT)
        if (Bip39Dictionary.IsWord(applicationContext, Raw) && !Bip39Dictionary.HasLonger(applicationContext, Raw)) {
            Advance(Index, Raw)
        } else {
            HighlightWord(Index)
            UpdateSaveState()
        }
    }

    private fun CompleteWord(Index: Int) {
        val Raw = WordViews[Index].text.toString().lowercase(Locale.ROOT)
        val Complete = Bip39Dictionary.Completion(applicationContext, Raw)
        if (Complete != null && Bip39Dictionary.IsWord(applicationContext, Complete)) {
            Advance(Index, Complete)
        } else ShowRecoveryNotice("Finish a valid BIP39 word.")
    }

    private fun Advance(Index: Int, Completed: String) {
        StopWordPulse()
        ChangingWords = true
        WordViews[Index].setText(GenesisWords.Display(Completed))
        WordViews[Index].setSelection(WordViews[Index].length())
        ChangingWords = false
        WordViews[Index].animate().alpha(0.45f).setDuration(100L).withEndAction {
            WordViews[Index].animate().alpha(1f).setDuration(160L).start()
        }.start()
        if (Index < 11) FocusWord(Index + 1)
        else HideKeyboard()
        UpdateSaveState()
    }

    private fun ApplyPastedWords(Text: String): Boolean {
        val Parsed = Bip39Dictionary.ParsePasted(applicationContext, Text)
        if (Parsed == null || Parsed.size != 12) {
            ShowRecoveryNotice("Paste twelve valid recovery words.", 2300L)
            return false
        }
        StopWordPulse()
        ChangingWords = true
        for (Index in 0 until 12) WordViews[Index].setText(GenesisWords.Display(Parsed[Index]))
        for (Word in WordViews) Word.setTextColor(SecondaryAccent)
        ChangingWords = false
        HideKeyboard()
        WordViews.last().clearFocus()
        ShowRecoveryNotice("Checking checksum…", 1500L)
        UpdateSaveState()
        return true
    }

    private fun CandidateEntropy(): ByteArray? {
        if (RecoveryModeNow != RecoveryMode.EDITING) return null
        val Input = WordViews.map { it.text.toString().lowercase(Locale.ROOT) }
        if (Input.size != 12 || Input.any { !Bip39Dictionary.IsWord(applicationContext, it) }) return null
        return try { GenesisWords.ToEntropy(Input) } catch (_: Exception) { null }
    }

    private fun UpdateSaveState() {
        if (RecoveryModeNow != RecoveryMode.EDITING) return
        val Ready = CandidateEntropy()
        RevealButton.isEnabled = Ready != null
        RevealButton.alpha = if (Ready != null) 1f else 0.38f
        if (Ready != null) {
            Ready.fill(0)
            // Save becoming fully bright is the signal. No extra label needed.
        } else if (WordViews.all { Bip39Dictionary.IsWord(applicationContext, it.text.toString().lowercase(Locale.ROOT)) }) {
            ShowRecoveryNotice("Checksum doesn't match.", 2600L)
        }
    }

    private fun SaveRestore() {
        val NewEntropy = CandidateEntropy() ?: return
        val OldEntropy = try { GenesisWords.ToEntropy(RevealedWords) } catch (_: Exception) { null }
        if (OldEntropy != null && OldEntropy.contentEquals(NewEntropy)) {
            OldEntropy.fill(0)
            NewEntropy.fill(0)
            ShowRevealedWords(RevealedWords)
            return
        }
        OldEntropy?.fill(0)
        val SecretField = SecretInput("Secret")
        val Container = DialogContainer(SecretField)
        AlertDialog.Builder(this)
            .setTitle("Replace Genesis?")
            .setMessage("This changes the recovery root and future addresses. Make sure you have a backup of the CURRENT twelve words first. Enter your Secret to confirm.")
            .setView(Container)
            .setNegativeButton("Cancel") { _, _ -> NewEntropy.fill(0) }
            .setPositiveButton("Save", null)
            .create().also { Dialog ->
                Dialog.setOnShowListener {
                    Dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val Secret = SecretField.text.toString().toCharArray()
                        SecretField.text.clear()
                        if (Secret.isEmpty()) { Secret.fill('\u0000'); SecretField.error = "Enter your Secret"; return@setOnClickListener }
                        Dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                        Dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                        Thread {
                            var Error: String? = null
                            var Words: List<String>? = null
                            var PublicKey: String? = null
                            try {
                                // Derive BEFORE changing the stored Genesis, so a derivation
                                // failure cannot replace a working identity.
                                PublicKey = MowsieIdentity.Derive(NewEntropy).PublicKeyHex
                                Store.ReplaceEntropy(NewEntropy, Secret)
                                Words = GenesisWords.FromEntropy(NewEntropy)
                            } catch (_: Exception) { Error = "Wrong Secret or unable to save. Your previous Genesis remains available." }
                            finally { Secret.fill('\u0000') }
                            runOnUiThread {
                                if (Error == null && Words != null && PublicKey != null) {
                                    ActivePublicKeyHex = PublicKey
                                    RefreshPublicQr()
                                    Dialog.dismiss()
                                    if (Authenticated && HoodOpen) {
                                        ShowRevealedWords(Words!!)
                                        ShowRecoveryNotice("✓ Success", 1250L)
                                    }
                                } else {
                                    Dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                    Dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                                    Toast.makeText(this, Error ?: "Unable to save", Toast.LENGTH_LONG).show()
                                }
                            }
                        }.start()
                    }
                }
                // No background dismissal during an in-flight write.
                Dialog.setCancelable(false)
                Dialog.setOnDismissListener { NewEntropy.fill(0) }
                Dialog.show()
            }
    }

    private fun CopyRecovery() {
        if (RecoveryModeNow != RecoveryMode.REVEALED || RevealedWords.size != 12) return
        val Clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val Clip = ClipData.newPlainText("Mowsie Recovery", RevealedWords.joinToString(" "))
        Clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        Clipboard.setPrimaryClip(Clip)
        ShowRecoveryNotice("✓ Copied", 1100L)
    }

    private fun AskSecret(Title: String, Work: (CharArray) -> List<String>, Done: (List<String>) -> Unit) {
        val SecretField = SecretInput("Secret")
        val Container = DialogContainer(SecretField)
        AlertDialog.Builder(this).setTitle(Title).setView(Container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reveal", null).create().also { Dialog ->
                Dialog.setOnShowListener {
                    Dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val Secret = SecretField.text.toString().toCharArray()
                        SecretField.text.clear()
                        if (Secret.isEmpty()) {
                            Secret.fill('\u0000')
                            SecretField.error = "Enter your Secret"
                            return@setOnClickListener
                        }
                        Dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                        Thread {
                            var Result: List<String>? = null
                            try { Result = Work(Secret) } catch (_: Exception) {}
                            finally { Secret.fill('\u0000') }
                            runOnUiThread {
                                if (Result != null) {
                                    Dialog.dismiss()
                                    Done(Result!!)
                                } else {
                                    Dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                    Toast.makeText(this, "Incorrect Secret or Genesis unavailable", Toast.LENGTH_LONG).show()
                                }
                            }
                        }.start()
                    }
                }
                Dialog.show()
            }
    }

    private fun SecretInput(Label: String): EditText = PhosphorEditText(this).apply {
        hint = Label
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setSingleLine(true)
    }

    private fun DialogContainer(Field: EditText): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Dp(24), 0, Dp(24), 0)
        addView(Field)
    }

    /**
     * Front door only. The Ω is stationary, a gently bobbing crystal wakes
     * Secret/Confirm, and an invisible EditText retains native IME behavior.
     * No changes to Genesis, QR, Recovery Hood, or cryptographic operations.
     */
    private fun ShowLockOverlay() {
        if (Root.findViewWithTag<View>("MowsieLock") != null) return
        HideRecovery()
        val NewNest = !Store.Exists()
        val Overlay = FrameLayout(this).apply {
            tag = "MowsieLock"
            setBackgroundColor(getColor(R.color.blast_door))
            isClickable = true
            isFocusableInTouchMode = true
        }

        // Position the whole scene ONCE against the unobstructed screen.
        // Gravity.TOP avoids the recentering that moved the Ω with the IME in V6.
        val Scene = FrameLayout(this)
        Overlay.addView(Scene, FrameLayout.LayoutParams(-1, Dp(405), Gravity.TOP))

        // Embossed relief, not a bright accent-colored logo. The same vector
        // is rendered three times with slight offsets for the bevel and shadow.
        fun AddOmega(Tint: Int, Alpha: Float, X: Int, Y: Int) {
            val Layer = ImageView(this).apply {
                setImageResource(R.drawable.mowsieomega)
                imageTintList = ColorStateList.valueOf(Tint)
                alpha = Alpha
                translationX = Dp(X).toFloat()
                translationY = Dp(Y).toFloat()
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            Scene.addView(Layer, FrameLayout.LayoutParams(Dp(318), Dp(318), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }
        AddOmega(Color.parseColor("#080E18"), 0.90f, 3, 4) // Lower-right relief shadow
        AddOmega(Color.parseColor("#324458"), 0.55f, -2, -3) // Upper-left bevel
        // Opaque face and background share one exact color. Only the emboss edges show.
        AddOmega(getColor(R.color.blast_door), 1f, 0, 0)

        // The crystal floats mostly inside the Ω arch, its crown just grazing
        // the top relief. The bob never moves the Secret or typing text.
        val CrystalRegion = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Crystal. Tap to enter Secret"
        }
        Scene.addView(CrystalRegion, FrameLayout.LayoutParams(Dp(244), Dp(240), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = Dp(34)
        })
        // The aura may extend beyond the crystal's touch region, without moving
        // the crystal or increasing the size of its hit target.
        Scene.clipChildren = false
        CrystalRegion.clipChildren = false
        val CrystalFloat = FrameLayout(this).apply { clipChildren = false }
        CrystalRegion.addView(CrystalFloat, FrameLayout.LayoutParams(-1, -1))
        val CrystalGlow = DreamGlowView(this).apply { Strength = 0.015f }
        CrystalFloat.addView(CrystalGlow, FrameLayout.LayoutParams(Dp(244), Dp(240), Gravity.CENTER))
        // Independent vertical pink failure aura, behind the crystal and separate
        // from the normal purple-blue illumination. No glow is baked into PNGs.
        val ErrorGlow = CrystalErrorGlowView(this)
        CrystalFloat.addView(ErrorGlow, FrameLayout.LayoutParams(Dp(292), Dp(296), Gravity.CENTER))
        val DarkCrystal = ImageView(this).apply {
            setImageResource(R.drawable.darkcrystal)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val LightCrystal = ImageView(this).apply {
            setImageResource(R.drawable.lightcrystal)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val ShookCrystal = ImageView(this).apply {
            setImageResource(R.drawable.shookcrystal)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val CrystalVeil = CrystalErrorVeilView(this)
        val CrystalParams = FrameLayout.LayoutParams(Dp(88), Dp(210), Gravity.CENTER)
        CrystalFloat.addView(DarkCrystal, FrameLayout.LayoutParams(CrystalParams))
        CrystalFloat.addView(LightCrystal, FrameLayout.LayoutParams(CrystalParams))
        // Error artwork temporarily covers either crystal. The translucent
        // pink veil sits ABOVE its facets, but BELOW the Secret/Confirm text.
        CrystalFloat.addView(ShookCrystal, FrameLayout.LayoutParams(CrystalParams))
        CrystalFloat.addView(CrystalVeil, FrameLayout.LayoutParams(CrystalParams))

        // ±5dp on the crystal only, slowly floating in front of the Ω.
        // Use attach/detach to avoid animators running after leaving this screen.
        var BobAnimator: ValueAnimator? = null
        var FailureAnimator: ValueAnimator? = null
        var ErrorFadeAnimator: ValueAnimator? = null
        CrystalFloat.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(View: View) {
                BobAnimator = ValueAnimator.ofFloat(-Dp(5).toFloat(), Dp(5).toFloat()).apply {
                    duration = 3600L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    addUpdateListener { CrystalFloat.translationY = it.animatedValue as Float }
                    start()
                }
            }
            override fun onViewDetachedFromWindow(View: View) {
                BobAnimator?.cancel()
                BobAnimator = null
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
        })

        // The Secret/Confirm label appears ONLY after the first crystal tap.
        // Secret sits just above the Ω feet, with typed characters just below.
        val Caption = PhosphorTextView(this).apply {
            text = "Secret"
            textSize = 31f
            gravity = Gravity.CENTER
            setTextColor(Accent)
            setShadowLayer(Dp(5).toFloat(), Dp(1).toFloat(), Dp(3).toFloat(), Color.BLACK)
            includeFontPadding = false
            alpha = 0f
            visibility = View.INVISIBLE
            isClickable = true
            contentDescription = "Secret. Tap to type"
        }
        Scene.addView(Caption, FrameLayout.LayoutParams(Dp(210), Dp(58), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = Dp(180)
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
            transformationMethod = PasswordTransformationMethod.getInstance()
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            isCursorVisible = false
            isFocusableInTouchMode = true
            showSoftInputOnFocus = true
            visibility = View.INVISIBLE // First interaction happens at the crystal.
            alpha = 0f
        }
        Scene.addView(Input, FrameLayout.LayoutParams(-1, Dp(62), Gravity.TOP).apply {
            topMargin = Dp(269)
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
            topMargin = Dp(336)
        })

        val Footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(PhosphorTextView(this@MainActivity).apply {
                text = "Mowsie V1.0"
                textSize = 11f
                letterSpacing = 0.09f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#9AAFC7"))
            }, LinearLayout.LayoutParams(-1, Dp(23)))
            addView(PhosphorTextView(this@MainActivity).apply {
                text = "Powered by Oblivious Compute"
                textSize = 10f
                letterSpacing = 0.05f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#7187A1"))
            }, LinearLayout.LayoutParams(-1, Dp(22)))
        }
        Overlay.addView(Footer, FrameLayout.LayoutParams(-1, Dp(55), Gravity.BOTTOM).apply {
            bottomMargin = Dp(7)
        })
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
            Caption.animate().alpha(Target).setDuration(Duration).start()
            Input.animate().alpha(Target).setDuration(Duration).withEndAction {
                if (!EditingVisible) Input.visibility = View.INVISIBLE
            }.start()
        }

        var StableHeight = 0
        fun AnchorScene() {
            if (Overlay.height <= 0 || Overlay.width <= 0) return
            // Record the largest full-screen height; never shrink the anchor
            // during an IME transition even if layout precedes inset dispatch.
            if (!KeyboardVisible) StableHeight = maxOf(StableHeight, Overlay.height)
            if (StableHeight <= 0) return
            val Preferred = (StableHeight * 0.15f).toInt()
            val InputClearance = StableHeight - Dp(280) - Dp(382)
            val Top = min(Preferred, InputClearance.coerceAtLeast(Dp(12)))
            val Layout = Scene.layoutParams as FrameLayout.LayoutParams
            if (Layout.topMargin != Top) {
                Layout.topMargin = Top
                Scene.layoutParams = Layout
            }
            // Width-only scaling for narrow devices, no keyboard-dependent size.
            val Scale = min(1f, (Overlay.width - Dp(16)).toFloat() / Dp(325).toFloat())
            Scene.pivotX = Scene.width / 2f
            Scene.pivotY = 0f
            Scene.scaleX = Scale
            Scene.scaleY = Scale
        }
        Overlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> AnchorScene() }
        ViewCompat.setOnApplyWindowInsetsListener(Overlay) { _, Insets ->
            KeyboardVisible = Insets.isVisible(WindowInsetsCompat.Type.ime())
            Footer.visibility = if (KeyboardVisible) View.GONE else View.VISIBLE
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
            Input.transformationMethod = if (Lit) null else PasswordTransformationMethod.getInstance()
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
                // The caption fades in only when Android actually exposes the IME.
                Caption.visibility = View.VISIBLE
                Caption.alpha = 0f
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
            Caption.text = "Secret"
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
                Caption.text = "Confirm"
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
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                            .hideSoftInputFromWindow(Input.windowToken, 0)
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

    override fun onStart() { super.onStart(); Onscreen = true }
    override fun onStop() {
        Onscreen = false
        ClearFrontDoor?.invoke()
        super.onStop()
        if (Authenticated && !isChangingConfigurations) {
            Authenticated = false
            CloseHoodOnLock?.invoke()
            ShowLockOverlay()
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                UpdateLandscapeSeal(Configuration.ORIENTATION_LANDSCAPE)
            }
        } else HideRecovery()
    }

    private fun TitleCase(Raw: String): String =
        Raw.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

    private fun HideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(Root.windowToken, 0)
        Root.requestFocus()
    }

    private fun Dp(Value: Int): Int = (Value * resources.displayMetrics.density + 0.5f).toInt()
    private fun Dp(Value: Float): Float = Value * resources.displayMetrics.density
}

/**
 * Crisp ZXing QR modules, rendered independently of the Stash's two colors.
 * Tap opens the existing viewfinder; long-press then drag adjusts HSL color;
 * releasing ANY long-press attempts to copy the QR as a shareable PNG.
 * The BitMatrix and encoded public identity never change during color adjustment.
 */
class MowsieQrView @JvmOverloads constructor(
    Context: Context,
    Attributes: AttributeSet? = null,
    Style: Int = 0
) : View(Context, Attributes, Style) {
    private var CurrentMatrix: BitMatrix? = null
    // QR styling is separate from Primary/Secondary and never changes the
    // encoded public identity. Restore the last chosen appearance on startup.
    private val QrAppearance = Context.getSharedPreferences(
        "MowsieQrAppearance", android.content.Context.MODE_PRIVATE)
    private val Saturation = 0.88f
    private var Hue = QrAppearance.getFloat("Hue", 270f)
    private var Lightness = QrAppearance.getFloat("Lightness", 1f).coerceIn(0f, 1f)
    private var StartingWhite = QrAppearance.getBoolean("StartingWhite", true)
    private var ModuleColor: Int = if (StartingWhite) Color.WHITE else
        ColorUtils.HSLToColor(floatArrayOf(Hue, Saturation, Lightness))
    var OnCopyRequest: (() -> Boolean)? = null
    var OnCopySucceeded: (() -> Unit)? = null

    private val ModulePaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }
    private var Pressed = false
    private var Holding = false
    private var StartX = 0f
    private var StartY = 0f
    private var FingerX = 0f
    private var FingerY = 0f
    private var LastX = 0f
    private var LastY = 0f
    private val Slop = ViewConfiguration.get(Context).scaledTouchSlop
    private val HoldRunnable = Runnable {
        if (Pressed) {
            Holding = true
            LastX = FingerX
            LastY = FingerY
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    @Throws(WriterException::class)
    fun SetPayload(Payload: String) {
        val Hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4
        )
        CurrentMatrix = QRCodeWriter().encode(Payload, BarcodeFormat.QR_CODE, 0, 0, Hints)
        invalidate()
    }

    private fun DragColor(Dx: Float, Dy: Float) {
        if (kotlin.math.abs(Dx) < 0.01f && kotlin.math.abs(Dy) < 0.01f) return
        // White has no hue. First horizontal adjustment reveals a saturated
        // color, while vertical motion still spans pure white down to black.
        if (StartingWhite && kotlin.math.abs(Dx) > 0.01f) {
            Lightness = 0.62f
            StartingWhite = false
        }
        if (kotlin.math.abs(Dy) > 0.01f) StartingWhite = false
        Hue = ((Hue + Dx * 360f / width.coerceAtLeast(1)) % 360f + 360f) % 360f
        Lightness = (Lightness - Dy * 1.40f / height.coerceAtLeast(1)).coerceIn(0f, 1f)
        ModuleColor = ColorUtils.HSLToColor(floatArrayOf(Hue, Saturation, Lightness))
        invalidate()
    }

    override fun onTouchEvent(Event: MotionEvent): Boolean {
        when (Event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                Pressed = true
                Holding = false
                StartX = Event.x
                StartY = Event.y
                FingerX = Event.x
                FingerY = Event.y
                LastX = Event.x
                LastY = Event.y
                removeCallbacks(HoldRunnable)
                postDelayed(HoldRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!Pressed) return false
                FingerX = Event.x
                FingerY = Event.y
                if (Holding) {
                    DragColor(FingerX - LastX, FingerY - LastY)
                    LastX = FingerX
                    LastY = FingerY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!Pressed) return false
                removeCallbacks(HoldRunnable)
                val WasHolding = Holding
                Pressed = false
                Holding = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (WasHolding) {
                    // Save only on release, never continuously while the
                    // finger moves. The very next app launch uses this color.
                    QrAppearance.edit()
                        .putFloat("Hue", Hue)
                        .putFloat("Lightness", Lightness)
                        .putBoolean("StartingWhite", StartingWhite)
                        .apply()
                    performLongClick()  // always copy on release, even after dragging
                } else if (kotlin.math.abs(Event.x - StartX) <= Slop &&
                    kotlin.math.abs(Event.y - StartY) <= Slop) {
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(HoldRunnable)
                Pressed = false
                Holding = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(Event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun performLongClick(): Boolean {
        return OnCopyRequest?.invoke() ?: super.performLongClick()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(HoldRunnable)
        Pressed = false
        Holding = false
        super.onDetachedFromWindow()
    }

    /** High-resolution PNG with a dark background and the matrix's quiet zone.
     * Draw directly from the BitMatrix so no text or focus overlay is exported. */
    fun MakeClipboardBitmap(): Bitmap? {
        val Matrix = CurrentMatrix ?: return null
        val Cell = (960 / Matrix.width).coerceAtLeast(1)
        val Padding = Cell * 2
        val Side = Matrix.width * Cell
        val Size = Side + Padding * 2
        val BitmapOut = Bitmap.createBitmap(Size, Size, Bitmap.Config.ARGB_8888)
        val CanvasOut = Canvas(BitmapOut)
        CanvasOut.drawColor(Color.parseColor("#0C1521"))
        ModulePaint.color = ModuleColor
        for (Y in 0 until Matrix.height) {
            for (X in 0 until Matrix.width) {
                if (Matrix.get(X, Y)) {
                    val Left = Padding + X * Cell
                    val Top = Padding + Y * Cell
                    CanvasOut.drawRect(Left.toFloat(), Top.toFloat(),
                        (Left + Cell).toFloat(), (Top + Cell).toFloat(), ModulePaint)
                }
            }
        }
        return BitmapOut
    }

    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        val Matrix = CurrentMatrix ?: return
        val AvailableWidth = width - paddingLeft - paddingRight
        val AvailableHeight = height - paddingTop - paddingBottom
        val Cell = min(AvailableWidth, AvailableHeight) / Matrix.width
        if (Cell < 1) return
        val Side = Cell * Matrix.width
        val Left = paddingLeft + (AvailableWidth - Side) / 2
        val Top = paddingTop + (AvailableHeight - Side) / 2
        ModulePaint.color = ModuleColor
        for (Y in 0 until Matrix.height) {
            for (X in 0 until Matrix.width) {
                if (Matrix.get(X, Y)) {
                    val PixelX = Left + X * Cell
                    val PixelY = Top + Y * Cell
                    Canvas.drawRect(PixelX.toFloat(), PixelY.toFloat(),
                        (PixelX + Cell).toFloat(), (PixelY + Cell).toFloat(), ModulePaint)
                }
            }
        }
    }
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


