package com.mowsie.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import androidx.activity.enableEdgeToEdge
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

/** Mowsie V4: fast local Secret gate, real BIP39 Restore, and existing QR/Hood. */
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
        val OmegaLogo = findViewById<ImageView>(R.id.omegaLogo)
        val MowsieHeader = findViewById<ImageView>(R.id.mowsieHeader)
        val LogoGlow = findViewById<View>(R.id.logoGlow)
        val LogoToggle = findViewById<FrameLayout>(R.id.logoToggle)
        val Hood = findViewById<LinearLayout>(R.id.hoodPanel)
        val HoodTab = findViewById<TextView>(R.id.hoodTab)

        OmegaLogo.imageTintList = ColorStateList.valueOf(Accent)
        MowsieHeader.imageTintList = ColorStateList.valueOf(Color.WHITE)
        LogoGlow.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.argb(90, Color.red(Accent), Color.green(Accent), Color.blue(Accent)), Color.TRANSPARENT)
        ).apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = 112f * resources.displayMetrics.density
        }
        HoodTab.background = GradientDrawable().apply {
            setColor(Color.parseColor("#2A405A"))
            cornerRadius = Dp(11).toFloat()
        }
        HoodTab.elevation = Dp(3).toFloat()
        Hood.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#223750"), Color.parseColor("#142235"))
        ).apply {
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, Dp(18).toFloat(), Dp(18).toFloat(), Dp(18).toFloat(), Dp(18).toFloat())
        }
        Hood.elevation = Dp(9).toFloat()

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

        LogoToggle.setOnClickListener {
            Hood.animate().cancel()
            if (!HoodOpen) {
                HoodOpen = true
                Hood.visibility = View.VISIBLE
                Hood.translationY = -Hood.height.toFloat()
                HoodTab.text = "⌃"
                Hood.animate().translationY(0f).setDuration(340L)
                    .setInterpolator(DecelerateInterpolator()).start()
            } else {
                HoodOpen = false
                HideRecovery()
                HoodTab.text = "⌄"
                Hood.animate().translationY(-Hood.height.toFloat()).setDuration(280L)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction { if (!HoodOpen) Hood.visibility = View.INVISIBLE }
                    .start()
            }
        }
        RevealButton.setOnClickListener {
            if (!Authenticated) return@setOnClickListener
            when (RecoveryModeNow) {
                RecoveryMode.HIDDEN -> AskSecretForReveal()
                RecoveryMode.REVEALED -> StartRestore()
                RecoveryMode.EDITING -> SaveRestore()
            }
        }
        findViewById<TextView>(R.id.settingsButton).setOnClickListener { ShowSettings() }

        fun RefreshQr() { RefreshPublicQr() }
        IdentityName.text = Preferences.getString("StashName", getString(R.string.stash_name))
        RefreshQr()
        QrPattern.setOnClickListener {
            val Standard = QrPattern.ToggleContrast()
            Toast.makeText(this, if (Standard) "Standard QR contrast" else "Floating QR contrast", Toast.LENGTH_SHORT).show()
        }
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
                        Preferences.edit().putString("StashName", NewName).apply()
                        RefreshQr()
                    }
                }.show()
        }
        var Sending = false
        SendButton.setOnClickListener {
            Sending = !Sending
            QrPattern.visibility = if (Sending) View.GONE else View.VISIBLE
            QrPlaceholder.visibility = if (Sending) View.VISIBLE else View.GONE
            if (Sending) QrPlaceholder.setText(R.string.scan_placeholder)
            SendButton.setText(if (Sending) R.string.receive else R.string.send)
        }
        HideRecovery()
        ShowLockOverlay()
        ViewCompat.setOnApplyWindowInsetsListener(Root) { View, Insets ->
            val Bars = Insets.getInsets(WindowInsetsCompat.Type.systemBars())
            View.setPadding(Bars.left, Bars.top, Bars.right, Bars.bottom)
            Insets
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

    private fun ShowLockOverlay() {
        if (Root.findViewWithTag<View>("MowsieLock") != null) return
        HideRecovery()
        val NewNest = !Store.Exists()
        val Overlay = FrameLayout(this).apply {
            tag = "MowsieLock"
            setBackgroundColor(Color.parseColor("#101A28"))
            isClickable = true
            isFocusable = true
        }
        val Scroll = ScrollView(this).apply { setFillViewport(true) }
        val Content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(Dp(30), Dp(20), Dp(30), Dp(25))
        }
        val Title = TextView(this).apply {
            text = "Mowsie"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val Caption = TextView(this).apply {
            text = "Secret"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Accent)
            setPadding(0, Dp(12), 0, Dp(18))
        }
        val Input = SecretInput("Secret").apply { imeOptions = EditorInfo.IME_ACTION_DONE }
        val Feedback = TextView(this).apply {
            setTextColor(Color.parseColor("#BDADAC"))
            gravity = Gravity.CENTER
            textSize = 13f
            setPadding(0, Dp(12), 0, Dp(8))
            text = if (NewNest) "" else ""
        }
        val Enter = Button(this).apply {
            text = "Enter"
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#496F97"))
            setTextColor(Color.WHITE)
        }
        Content.addView(Title)
        Content.addView(Caption)
        Content.addView(Input)
        Content.addView(Feedback)
        Content.addView(Enter)
        Scroll.addView(Content)
        Overlay.addView(Scroll)
        Root.addView(Overlay, FrameLayout.LayoutParams(-1, -1))

        var Confirming = false
        var FirstSecret: CharArray? = null
        fun ResetToSecret() {
            FirstSecret?.fill('\u0000')
            FirstSecret = null
            Confirming = false
            Input.text.clear()
            Input.hint = "Secret"
            Caption.text = "Secret"
        }
        fun Submit() {
            if (!Enter.isEnabled) return
            val Secret = Input.text.toString().toCharArray() // Do NOT trim: spaces are valid.
            Input.text.clear()
            if (Secret.isEmpty()) {
                Feedback.text = "Enter at least one character."
                Secret.fill('\u0000')
                return
            }
            if (NewNest && !Confirming) {
                FirstSecret = Secret
                Confirming = true
                Caption.text = "Confirm Secret"
                Input.hint = "Confirm Secret"
                Feedback.text = ""
                return
            }
            val SelectedSecret: CharArray
            if (NewNest) {
                val Initial = FirstSecret
                if (Initial == null || !Initial.contentEquals(Secret)) {
                    Secret.fill('\u0000')
                    ResetToSecret()
                    Feedback.text = "Secrets didn't match. Start again."
                    return
                }
                Secret.fill('\u0000')
                SelectedSecret = Initial
                FirstSecret = null
            } else SelectedSecret = Secret
            Enter.isEnabled = false
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
                    Error = if (NewNest) "Genesis creation failed. Your recovery data was not silently replaced." else
                        "Wrong Secret or device key unavailable. Genesis wasn't replaced."
                } finally { SelectedSecret.fill('\u0000') }
                runOnUiThread {
                    Enter.isEnabled = true
                    if (Error == null && PublicKey != null && Onscreen) {
                        ActivePublicKeyHex = PublicKey
                        RefreshPublicQr()
                        Authenticated = true
                        Root.removeView(Overlay)
                        if (NewNest) Toast.makeText(this,
                            "Genesis created. Back up your twelve words in Recovery.", Toast.LENGTH_LONG).show()
                    } else {
                        ResetToSecret()
                        Feedback.text = Error ?: "Enter your Secret."
                    }
                }
            }.start()
        }
        Enter.setOnClickListener { Submit() }
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
        super.onStop()
        if (Authenticated && !isChangingConfigurations) {
            Authenticated = false
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
