package com.mowsie.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.animation.ValueAnimator
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

/** Mowsie V21: independently animated solid doors, dark seam, field wheel. */
class MainActivity : AppCompatActivity() {
    private enum class RecoveryMode { HIDDEN, REVEALED, EDITING }

    private var HoodOpen = false
    private var Authenticated = false
    private var Onscreen = true
    private var ChangingWords = false
    private var RecoveryModeNow = RecoveryMode.HIDDEN
    private var RevealedWords: List<String> = emptyList()
    private val WordViews = mutableListOf<EditText>()
    private val NumberViews = mutableListOf<TextView>()
    private val SuggestionViews = mutableListOf<TextView>()

    private lateinit var Store: GenesisStore
    private lateinit var Root: FrameLayout
    private lateinit var RecoveryHint: TextView
    private lateinit var RevealButton: Button
    private lateinit var CancelButton: Button
    private lateinit var WordGrid: GridLayout
    private var Accent = Color.parseColor("#73A9E4")
    private var ActivePublicKeyHex: String? = null
    private var ClearFrontDoor: (() -> Unit)? = null
    private var CloseHoodOnLock: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        Store = GenesisStore(this)
        Root = findViewById(R.id.main)
        RecoveryHint = findViewById(R.id.recoveryHint)
        RevealButton = findViewById(R.id.revealButton)
        WordGrid = findViewById(R.id.wordGrid)

        val Preferences = getSharedPreferences("MowsieNest", MODE_PRIVATE)
        Accent = Preferences.getInt("StashColor", Accent)
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
        CancelButton = Button(this).apply {
            text = "Cancel"
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#324962"))
            setTextColor(Color.WHITE)
            visibility = View.GONE
        }
        val HoodParent = RevealButton.parent as LinearLayout
        HoodParent.addView(CancelButton, HoodParent.indexOfChild(RevealButton),
            LinearLayout.LayoutParams(-1, Dp(48)).apply { topMargin = Dp(12) })
        CancelButton.setOnClickListener { ShowRevealedWords(RevealedWords) }

        SetupHoodNavigation(LogoToggle, Hood, BottomDoor)
        RevealButton.setOnClickListener {
            if (!Authenticated) return@setOnClickListener
            when (RecoveryModeNow) {
                RecoveryMode.HIDDEN -> AskSecretForReveal()
                RecoveryMode.REVEALED -> StartRestore()
                RecoveryMode.EDITING -> SaveRestore()
            }
        }
        Hood.findViewById<TextView>(R.id.settingsButton).setOnClickListener { ShowSettings() }

        fun RefreshQr() { RefreshPublicQr() }
        IdentityName.text = Preferences.getString("StashName", getString(R.string.stash_name))
        StashDoorName.text = IdentityName.text
        FieldName.text = "Bob's Donuts"
        BalanceText.text = "10,485.75 Salt"
        RefreshQr()
        var ShowingViewfinder = false
        fun ToggleQrWindow() {
            ShowingViewfinder = !ShowingViewfinder
            QrPattern.visibility = if (ShowingViewfinder) View.GONE else View.VISIBLE
            QrPlaceholder.visibility = if (ShowingViewfinder) View.VISIBLE else View.GONE
            QrPlaceholder.text = if (ShowingViewfinder) getString(R.string.scan_placeholder) else ""
        }
        QrPattern.setOnClickListener { ToggleQrWindow() }
        QrPlaceholder.setOnClickListener { ToggleQrWindow() }
        IdentityName.setOnClickListener {
            val Input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                filters = arrayOf(InputFilter.LengthFilter(40))
                setSingleLine(true)
                setText(IdentityName.text)
                selectAll()
            }
            val Container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Dp(24), 0, Dp(24), 0)
                addView(Input)
            }
            AlertDialog.Builder(this).setTitle(R.string.name_stash).setView(Container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save) { _, _ ->
                    val NewName = Input.text.toString().trim()
                    if (NewName.isNotEmpty()) {
                        IdentityName.text = NewName
                        StashDoorName.text = NewName
                        Preferences.edit().putString("StashName", NewName).apply()
                        RefreshQr()
                    }
                }.show()
        }
        SendButton.visibility = View.GONE
        HideRecovery()
        ShowLockOverlay()
        ViewCompat.setOnApplyWindowInsetsListener(Root) { View, Insets ->
            val Bars = Insets.getInsets(WindowInsetsCompat.Type.systemBars())
            View.setPadding(Bars.left, Bars.top, Bars.right, Bars.bottom)
            Insets
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
        val Page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(Dp(22), Dp(122), Dp(22), Dp(96))
        }
        Scroll.addView(Page, FrameLayout.LayoutParams(-1, -2))

        // These layers ride the moving tab itself, NOT the stationary crystal.
        val OmegaHandle = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Omega. Return to Settings or close hood"
        }
        Navigator.addView(OmegaHandle,
            FrameLayout.LayoutParams(Dp(190), Dp(92), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        fun OmegaLayer(Tint: String, Alpha: Float, X: Int, Y: Int) {
            val Image = ImageView(this).apply {
                setImageResource(R.drawable.mowsieomega)
                imageTintList = ColorStateList.valueOf(Color.parseColor(Tint))
                alpha = Alpha
                scaleType = ImageView.ScaleType.FIT_CENTER
                translationX = Dp(X).toFloat()
                translationY = Dp(Y).toFloat()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            OmegaHandle.addView(Image,
                FrameLayout.LayoutParams(Dp(108), Dp(88), Gravity.CENTER))
        }
        OmegaLayer("#070C14", 0.95f, 2, 3)
        OmegaLayer("#334252", 0.46f, -1, -2)
        OmegaLayer("#15202E", 0.97f, 0, 0)

        // The crystal is separate from the sliding hood. Its vertical bob
        // continues at the same screen coordinates throughout navigation.
        val GemAnchor = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            isClickable = true
            isFocusable = true
            contentDescription = "Crystal. Open settings hood"
        }
        Root.addView(GemAnchor, FrameLayout.LayoutParams(-1, Dp(110), Gravity.TOP))
        val GemFloat = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        GemAnchor.addView(GemFloat,
            FrameLayout.LayoutParams(Dp(138), Dp(98), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        GemFloat.addView(DreamGlowView(this).apply { Strength = 0.36f },
            FrameLayout.LayoutParams(Dp(116), Dp(101), Gravity.CENTER))
        GemFloat.addView(ImageView(this).apply {
            setImageResource(R.drawable.lightcrystal)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, FrameLayout.LayoutParams(Dp(26), Dp(62), Gravity.CENTER))
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

        // The word sits over the bottom quarter of the stationary crystal,
        // but is only visible while the hood is open.
        val Selected = TextView(this).apply {
            text = "Settings"
            gravity = Gravity.CENTER
            textSize = 22f
            includeFontPadding = false
            setTextColor(Ink)
            setShadowLayer(Dp(4).toFloat(), Dp(1).toFloat(), Dp(2).toFloat(), Color.BLACK)
            alpha = 0f
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            contentDescription = "Settings. Close hood"
        }
        GemAnchor.addView(Selected,
            FrameLayout.LayoutParams(Dp(190), Dp(43), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = Dp(64)
            })

        fun AddText(Value: String, Size: Float = 17f, ColorValue: Int = Muted) {
            Page.addView(TextView(this).apply {
                text = Value
                textSize = Size
                setTextColor(ColorValue)
                gravity = Gravity.CENTER
                setPadding(Dp(8), Dp(5), Dp(8), Dp(5))
            }, LinearLayout.LayoutParams(-1, -2))
        }
        fun AddItem(Name: String, Click: () -> Unit) {
            Page.addView(TextView(this).apply {
                text = Name
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(Ink)
                includeFontPadding = false
                setShadowLayer(Dp(3).toFloat(), 0f, Dp(1).toFloat(), Color.BLACK)
                isClickable = true
                isFocusable = true
                setOnClickListener { Click() }
            }, LinearLayout.LayoutParams(-1, Dp(57)))
        }
        fun AddSpacer(Height: Int) { Page.addView(View(this), LinearLayout.LayoutParams(1, Dp(Height))) }
        fun AddAction(Name: String, Click: () -> Unit) { AddItem(Name, Click) }

        // Reparent the ORIGINAL recovery UI. Never duplicate the sensitive
        // reveal/edit/restore logic in a replacement screen.
        RecoveryPanel.setPadding(Dp(1), Dp(8), Dp(1), Dp(20))
        RecoveryPanel.visibility = View.GONE

        var NavigateTo: (String) -> Unit = {}
        var HoodAnimator: ValueAnimator? = null
        fun RootMenu() = Path.isEmpty()
        fun Current() = Path.lastOrNull() ?: "Settings"
        fun DetachRecovery() {
            (RecoveryPanel.parent as? android.view.ViewGroup)?.removeView(RecoveryPanel)
            RecoveryPanel.visibility = View.GONE
        }
        fun Refresh(AnimateTitle: Boolean = true) {
            HideKeyboard()
            if (Path.lastOrNull() != "Recovery") { HideRecovery(); DetachRecovery() }
            Page.removeAllViews()
            val Name = Current()
            Selected.animate().cancel()
            Selected.text = Name
            Selected.contentDescription = if (RootMenu())
                "Settings. Close hood" else "$Name. Back one menu"
            if (RootMenu()) {
                listOf("Stash", "Cache", "Genesis", "Version").forEach { Entry ->
                    AddItem(Entry) { NavigateTo(Entry) }
                }
            } else when (Name) {
                "Stash" -> {
                    listOf("Name", "Appearance", "Recovery", "Secret").forEach { Entry ->
                        AddItem(Entry) { NavigateTo(Entry) }
                    }
                }
                "Name" -> {
                    AddSpacer(15)
                    AddText(findViewById<TextView>(R.id.identityName).text.toString(), 22f, Ink)
                    AddAction("Rename Stash") { findViewById<TextView>(R.id.identityName).performClick() }
                }
                "Appearance" -> { AddSpacer(18); AddText("Appearance and accent colors will live here.") }
                "Recovery" -> {
                    DetachRecovery()
                    RecoveryPanel.visibility = View.VISIBLE
                    Page.addView(RecoveryPanel, LinearLayout.LayoutParams(-1, -2))
                }
                "Secret" -> { AddAction("Change Secret") { ShowChangeSecret() } }
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
        var LowerClosed = false
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
        QrWindow.addView(FieldWheel, FrameLayout.LayoutParams(-1, -1))
        var ShowingFields = false
        var FieldTransitioning = false
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
            val OpenTop = (QrPos[1] + QrWindow.height + Dp(8) -
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
                duration = if (CloseDoor) 465L else 390L
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
            if (ShowingFields && !LowerClosed) {
                AnimateLower(true) { SetWindowFields(false) }
            } else {
                AnimateLower(!LowerClosed)
            }
        }
        // The field name performs the deliberate close -> change contents -> reopen.
        FieldName.setOnClickListener {
            if (HoodOpen || FieldTransitioning) return@setOnClickListener
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
            AnimateHood(OpenHeight(), 465L)
        }
        fun Close() {
            HideRecovery()
            DetachRecovery()
            Path.clear()
            HoodOpen = false
            Selected.animate().cancel()
            Selected.animate().alpha(0f).setDuration(180L).start()
            Page.animate().cancel()
            Page.animate().alpha(0f).setDuration(150L).start()
            AnimateHood(Dp(110), 390L) {
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
            LowerAnimator?.cancel()
            LowerClosed = false
            BottomDoor.translationY = 0f
            SetWindowFields(false)
            FieldTransitioning = false
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
        }
        Selected.setOnClickListener { if (HoodOpen) BackOne() else Open() }
        GemAnchor.setOnClickListener { if (!HoodOpen) Open() else BackOne() }
        OmegaHandle.setOnClickListener { if (HoodOpen) MoveToRoot() else Open() }
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
                "Public Key: $Key\n" +
                "Color: #%06X".format(Accent and 0xFFFFFF)
        }
        try { findViewById<MowsieQrView>(R.id.qrPattern).SetPayload(Payload) }
        catch (_: WriterException) {
            Toast.makeText(this, "Unable to generate public key QR", Toast.LENGTH_LONG).show()
        }
    }

    private fun BuildWordGrid() {
        for (Index in 0 until 12) {
            val Number = TextView(this).apply {
                text = "${Index + 1}."
                textSize = 11f
                setTextColor(Color.parseColor("#8499B2"))
                includeFontPadding = false
                gravity = Gravity.CENTER_VERTICAL
            }
            val Word = EditText(this).apply {
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
                isFocusable = false
                isFocusableInTouchMode = false
            }
            val Suggestion = TextView(this).apply {
                textSize = 12f
                includeFontPadding = false
                setTextColor(Color.parseColor("#778FA7"))
                gravity = Gravity.CENTER_VERTICAL
                visibility = View.GONE
            }
            val Cell = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Number, LinearLayout.LayoutParams(-2, -1).apply { rightMargin = Dp(3) })
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
            NumberViews.add(Number)
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
            Cell.setOnClickListener { if (RecoveryModeNow == RecoveryMode.REVEALED) CopyRecovery() else if (RecoveryModeNow == RecoveryMode.EDITING) FocusWord(Index) }
            Number.setOnClickListener { if (RecoveryModeNow == RecoveryMode.REVEALED) CopyRecovery() else if (RecoveryModeNow == RecoveryMode.EDITING) FocusWord(Index) }
            Word.setOnClickListener { if (RecoveryModeNow == RecoveryMode.REVEALED) CopyRecovery() }
        }
        WordGrid.setOnClickListener { if (RecoveryModeNow == RecoveryMode.REVEALED) CopyRecovery() }
    }

    private fun UpdateRecoveryControls() {
        RevealButton.text = when (RecoveryModeNow) {
            RecoveryMode.HIDDEN -> "Reveal"
            RecoveryMode.REVEALED -> "Restore"
            RecoveryMode.EDITING -> "Save"
        }
        CancelButton.visibility = if (RecoveryModeNow == RecoveryMode.EDITING) View.VISIBLE else View.GONE
        if (RecoveryModeNow != RecoveryMode.EDITING) RevealButton.isEnabled = true
    }

    private fun HideRecovery() {
        HideKeyboard()
        RecoveryModeNow = RecoveryMode.HIDDEN
        RevealedWords = emptyList()
        ChangingWords = true
        for (Index in WordViews.indices) {
            WordViews[Index].setText("•••••")
            WordViews[Index].setTextColor(Color.parseColor("#9FB2C8"))
            WordViews[Index].isFocusable = false
            WordViews[Index].isFocusableInTouchMode = false
            WordViews[Index].isCursorVisible = false
            NumberViews[Index].setTextColor(Color.parseColor("#8499B2"))
        }
        ChangingWords = false
        RecoveryHint.text = "Reveal requires your Secret."
        UpdateRecoveryControls()
    }

    private fun ShowRevealedWords(Words: List<String>) {
        HideKeyboard()
        RevealedWords = Words.toList()
        RecoveryModeNow = RecoveryMode.REVEALED
        ChangingWords = true
        for (Index in WordViews.indices) {
            val Word = WordViews[Index]
            Word.isFocusable = false
            Word.isFocusableInTouchMode = false
            Word.isCursorVisible = false
            Word.setText(GenesisWords.Display(Words[Index]))
            Word.setTextColor(Color.parseColor("#ADBCCD"))
            NumberViews[Index].setTextColor(Color.parseColor("#879AB3"))
        }
        ChangingWords = false
        RecoveryHint.text = "Tap any word to copy all twelve."
        UpdateRecoveryControls()
    }

    private fun AskSecretForReveal() {
        AskSecret("Reveal Recovery", { Secret ->
            val Entropy = Store.Unlock(Secret)
            try { GenesisWords.FromEntropy(Entropy) }
            finally { Entropy.fill(0) }
        }, { Words ->
            if (Authenticated && HoodOpen) ShowRevealedWords(Words)
        })
    }

    private fun StartRestore() {
        if (RecoveryModeNow != RecoveryMode.REVEALED) return
        if (!Bip39Dictionary.IsReady()) {
            RevealButton.isEnabled = false
            RecoveryHint.text = "Preparing the BIP39 dictionary…"
            Thread {
                var Loaded = true
                try { Bip39Dictionary.Load(applicationContext) } catch (_: Exception) { Loaded = false }
                runOnUiThread {
                    RevealButton.isEnabled = true
                    if (Loaded && Authenticated && HoodOpen && RecoveryModeNow == RecoveryMode.REVEALED) {
                        EnterRestore()
                    } else if (!Loaded) RecoveryHint.text = "Unable to load BIP39 dictionary."
                }
            }.start()
        } else EnterRestore()
    }

    private fun EnterRestore() {
        RecoveryModeNow = RecoveryMode.EDITING
        ChangingWords = true
        for (Index in WordViews.indices) {
            WordViews[Index].setText("")
            WordViews[Index].isFocusable = true
            WordViews[Index].isFocusableInTouchMode = true
            WordViews[Index].isCursorVisible = true
            WordViews[Index].setTextColor(Color.parseColor("#AABBCD"))
            NumberViews[Index].setTextColor(Color.parseColor("#8499B2"))
        }
        ChangingWords = false
        RecoveryHint.text = "Type a word, press Enter for a suggestion, or paste all twelve."
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
            NumberViews[I].setTextColor(if (Selected) Accent else Color.parseColor("#8499B2"))
            WordViews[I].setTextColor(if (Selected) Accent else Color.parseColor("#AABBCD"))
        }
        ShowSuggestion(Index)
    }

    private fun ShowSuggestion(Index: Int) {
        val Raw = WordViews[Index].text.toString().lowercase(Locale.ROOT)
        val Suggest = Bip39Dictionary.Completion(applicationContext, Raw)
        for (Item in SuggestionViews) Item.visibility = View.GONE
        if (Raw.length >= 4 && Suggest != null && Suggest.length > Raw.length) {
            val Tail = Suggest.substring(Raw.length)
            val Field = WordViews[Index]
            val Ghost = SuggestionViews[Index]
            Ghost.text = Tail
            Field.post {
                if (RecoveryModeNow != RecoveryMode.EDITING || !Field.hasFocus()) return@post
                val Width = Field.paint.measureText(Field.text.toString())
                val Extra = Ghost.paint.measureText(Tail)
                if (Width + Extra + Dp(3) <= Field.width) {
                    Ghost.translationX = Width + Dp(1)
                    Ghost.visibility = View.VISIBLE
                }
            }
            RecoveryHint.text = "↵ ${GenesisWords.Display(Suggest)}"
        } else if (Raw.isNotEmpty() && !Bip39Dictionary.IsWord(applicationContext, Raw) && Suggest == null) {
            RecoveryHint.text = "Not in the BIP39 dictionary"
        } else {
            RecoveryHint.text = "Enter accepts a suggestion. Full words advance automatically."
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
        } else RecoveryHint.text = "Finish a valid BIP39 word."
    }

    private fun Advance(Index: Int, Completed: String) {
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
            RecoveryHint.text = "Paste 12 BIP39 words. If joined, the split must be unambiguous."
            return false
        }
        ChangingWords = true
        for (Index in 0 until 12) WordViews[Index].setText(GenesisWords.Display(Parsed[Index]))
        ChangingWords = false
        HideKeyboard()
        WordViews.last().clearFocus()
        for (Number in NumberViews) Number.setTextColor(Color.parseColor("#8499B2"))
        RecoveryHint.text = "12 words entered. Checking checksum…"
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
        if (Ready != null) {
            Ready.fill(0)
            RecoveryHint.text = "Valid BIP39 checksum. Ready to Save."
        } else if (WordViews.all { Bip39Dictionary.IsWord(applicationContext, it.text.toString().lowercase(Locale.ROOT)) }) {
            RecoveryHint.text = "All words recognized, but the checksum doesn't match."
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
                                    if (Authenticated && HoodOpen) ShowRevealedWords(Words!!)
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
        RecoveryHint.text = "Copied ✓"
    }

    private fun ShowSettings() {
        AlertDialog.Builder(this).setTitle("Mowsie Settings")
            .setItems(arrayOf("Change Secret", "More settings coming later")) { _, Which ->
                if (Which == 0) ShowChangeSecret() else
                    Toast.makeText(this, "Settings coming later", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun ShowChangeSecret() {
        val OldField = SecretInput("Current Secret")
        val NewField = SecretInput("New Secret")
        val ConfirmField = SecretInput("Confirm New Secret")
        val Container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Dp(24), 0, Dp(24), 0)
            addView(OldField)
            addView(NewField)
            addView(ConfirmField)
        }
        AlertDialog.Builder(this).setTitle("Change Secret").setView(Container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null).create().also { Dialog ->
                Dialog.setOnShowListener {
                    Dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val Old = OldField.text.toString().toCharArray()
                        val New = NewField.text.toString().toCharArray()
                        val Confirm = ConfirmField.text.toString().toCharArray()
                        if (Old.isEmpty() || New.isEmpty() || !New.contentEquals(Confirm)) {
                            Old.fill('\u0000')
                            New.fill('\u0000')
                            Confirm.fill('\u0000')
                            Toast.makeText(this, "Enter a Secret and match its confirmation.", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        OldField.text.clear()
                        NewField.text.clear()
                        ConfirmField.text.clear()
                        Confirm.fill('\u0000')
                        Dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                        Dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                        Thread {
                            var Worked = false
                            try { Store.ChangeSecret(Old, New); Worked = true }
                            catch (_: Exception) { /* old Secret incorrect, or unable to save */ }
                            finally { Old.fill('\u0000'); New.fill('\u0000') }
                            runOnUiThread {
                                if (Worked) {
                                    Dialog.dismiss()
                                    HideRecovery() // Require the newly chosen Secret for Reveal.
                                    Toast.makeText(this, "Secret updated. Genesis unchanged.", Toast.LENGTH_LONG).show()
                                } else {
                                    Dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                    Dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                                    Toast.makeText(this, "Wrong Secret or unable to update", Toast.LENGTH_LONG).show()
                                }
                            }
                        }.start()
                    }
                }
                Dialog.setCancelable(false)
                Dialog.show()
            }
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

    private fun SecretInput(Label: String): EditText = EditText(this).apply {
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
            setBackgroundColor(Color.parseColor("#101A28"))
            isClickable = true
            isFocusableInTouchMode = true
        }

        // Position the whole scene ONCE against the unobstructed screen.
        // Gravity.TOP avoids the recentering that moved the Ω with the IME in V6.
        val Scene = FrameLayout(this)
        Overlay.addView(Scene, FrameLayout.LayoutParams(-1, Dp(405), Gravity.TOP))

        // Embossed relief, not a bright accent-colored logo. The same vector
        // is rendered three times with slight offsets for the bevel and shadow.
        fun AddOmega(ColorHex: String, Alpha: Float, X: Int, Y: Int) {
            val Layer = ImageView(this).apply {
                setImageResource(R.drawable.mowsieomega)
                imageTintList = ColorStateList.valueOf(Color.parseColor(ColorHex))
                alpha = Alpha
                translationX = Dp(X).toFloat()
                translationY = Dp(Y).toFloat()
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            Scene.addView(Layer, FrameLayout.LayoutParams(Dp(318), Dp(318), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }
        AddOmega("#080E18", 0.90f, 3, 4)     // Lower-right relief shadow
        AddOmega("#324458", 0.55f, -2, -3)   // Faint upper-left bevel
        AddOmega("#111C2A", 0.96f, 0, 0)     // Face only slightly raised above #101A28

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
        val Caption = TextView(this).apply {
            text = "Secret"
            textSize = 31f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#F2F4FC"))
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
        val Input = EditText(this).apply {
            background = null
            hint = ""
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FAF8FE"))
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
        val Feedback = TextView(this).apply {
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
            addView(TextView(this@MainActivity).apply {
                text = "Mowsie V1.0"
                textSize = 11f
                letterSpacing = 0.09f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#9AAFC7"))
            }, LinearLayout.LayoutParams(-1, Dp(23)))
            addView(TextView(this@MainActivity).apply {
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
            Input.setSelection(Input.text.length)
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
            Input.text.clear()
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
            Input.text.clear()
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
 * An actual ZXing QR symbol, rendered as crisp square modules with no bitmap or PNG.
 * The parent FrameLayout remains transparent. For compatibility testing only,
 * a tap toggles between inverted floating colors and standard dark-on-light colors.
 */
class MowsieQrView @JvmOverloads constructor(
    Context: Context,
    Attributes: AttributeSet? = null,
    Style: Int = 0
) : View(Context, Attributes, Style) {

    private var CurrentMatrix: BitMatrix? = null
    private var StandardContrast = false

    private val ModulePaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }
    private val BackgroundPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
        color = Color.parseColor("#F5F8FC")
    }

    @Throws(WriterException::class)
    fun SetPayload(Payload: String) {
        val Hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4
        )
        CurrentMatrix = QRCodeWriter().encode(
            Payload, BarcodeFormat.QR_CODE, 0, 0, Hints
        )
        invalidate()
    }

    fun ToggleContrast(): Boolean {
        StandardContrast = !StandardContrast
        invalidate()
        return StandardContrast
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

        if (StandardContrast) {
            // The rectangle exists only in the compatibility test mode.
            // It contains the required 4-module light quiet zone.
            Canvas.drawRect(
                Left.toFloat(), Top.toFloat(),
                (Left + Side).toFloat(), (Top + Side).toFloat(),
                BackgroundPaint
            )
            ModulePaint.color = Color.parseColor("#101A28")
        } else {
            // White modules float over Mowsie's native inky-blue background.
            // This is an inverted QR and some scanners will not recognize it.
            ModulePaint.color = Color.WHITE
        }

        for (Y in 0 until Matrix.height) {
            for (X in 0 until Matrix.width) {
                if (Matrix.get(X, Y)) {
                    val PixelX = Left + X * Cell
                    val PixelY = Top + Y * Cell
                    Canvas.drawRect(
                        PixelX.toFloat(), PixelY.toFloat(),
                        (PixelX + Cell).toFloat(), (PixelY + Cell).toFloat(),
                        ModulePaint
                    )
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


