package com.mowsie.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.animation.ValueAnimator
import android.animation.ObjectAnimator
import android.os.PersistableBundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.util.Locale


        // The page is its own live preview. Draft changes never escape
        // Appearance until Save. Leaving via the crystal or Omega discards them.
internal fun ControlPanel.AddAppearancePage(
    Page: LinearLayout, Selected: PhosphorTextView, MenuFontSp: Float,
    MenuRowDp: Int
) {
    val Preferences = getSharedPreferences("MowsieNest", Context.MODE_PRIVATE)
    val Identity = findViewById<TextView>(R.id.identityName)
    val StashDoor = findViewById<TextView>(R.id.stashDoorName)
    val InactiveInk = Color.parseColor("#667587")
    var SavedName = Identity.text.toString()
    var DraftName = SavedName
    var DraftPrimary = Accent
    var DraftSecondary = SecondaryAccent
    var EditingName = false
    var UpdatingName = false

    // Just the current name, in gray. Tap it to clear the visible text;
    // characters entered afterwards use the secondary appearance color.
    val NameEditor = PhosphorEditText(this).apply {
        setSingleLine(true)
        textSize = MenuFontSp
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(Dp(4), 0, Dp(4), 0)
        background = null
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        imeOptions = EditorInfo.IME_ACTION_DONE
        filters = arrayOf(InputFilter.LengthFilter(40))
        setText(SavedName)
        setTextColor(InactiveInk)
        isCursorVisible = false
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = true
        contentDescription = "Identity name. Tap to replace"
        if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
    }
    Page.addView(NameEditor, LinearLayout.LayoutParams(-1, Dp(MenuRowDp)))

    // Both color controls are self-explanatory visually. Keep accessible
    // descriptions so the two unnamed sliders remain distinguishable.
    val ColorRails = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }
    Page.addView(ColorRails, LinearLayout.LayoutParams(-1, -2).apply {
        leftMargin = Dp(12)
        rightMargin = Dp(12)
        topMargin = Dp(8)
        bottomMargin = Dp(8)
    })
    fun AddColorRail(Description: String): AppearanceHueSlider {
        val Slider = AppearanceHueSlider(this).apply {
            contentDescription = Description
        }
        ColorRails.addView(Slider, LinearLayout.LayoutParams(-1, Dp(45)))
        return Slider
    }
    val PrimarySlider = AddColorRail("Primary color")
    val SecondarySlider = AddColorRail("Secondary color")
    val SaveText = PhosphorTextView(this).apply {
        text = "Save"
        textSize = MenuFontSp
        gravity = Gravity.CENTER
        includeFontPadding = false
        isClickable = true
        isFocusable = false
        if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
    }
    Page.addView(SaveText, LinearLayout.LayoutParams(-1, Dp(MenuRowDp)))

    fun HueOf(Value: Int): Float {
        val HSV = FloatArray(3)
        Color.colorToHSV(Value, HSV)
        return HSV[0]
    }
    fun ColorAtHue(HueValue: Float): Int = Color.HSVToColor(
        floatArrayOf(HueValue, 0.63f, 0.97f)
    )
    PrimarySlider.Hue = HueOf(DraftPrimary)
    SecondarySlider.Hue = HueOf(DraftSecondary)

    fun Changed(): Boolean = DraftName.trim().isNotEmpty() &&
        (DraftName.trim() != SavedName || DraftPrimary != Accent || DraftSecondary != SecondaryAccent)

    fun PaintPreview() {
        Selected.setTextColor(DraftPrimary)
        NameEditor.setTextColor(if (EditingName || DraftName != SavedName) DraftSecondary else InactiveInk)
        PrimarySlider.ThumbColor = DraftSecondary
        SecondarySlider.ThumbColor = DraftSecondary
        SaveText.isEnabled = Changed()
        SaveText.setTextColor(if (SaveText.isEnabled) DraftPrimary else InactiveInk)
        SaveText.alpha = if (SaveText.isEnabled) 1f else 0.66f
    }
    fun FinishName() {
        if (!EditingName) return
        EditingName = false
        DraftName = NameEditor.text.toString().trim().ifEmpty { SavedName }
        UpdatingName = true
        NameEditor.setText(DraftName)
        UpdatingName = false
        NameEditor.isCursorVisible = false
        NameEditor.clearFocus()
        NameEditor.isFocusable = false
        NameEditor.isFocusableInTouchMode = false
        HideKeyboard()
        PaintPreview()
    }
    fun StartName() {
        if (EditingName) return
        EditingName = true
        UpdatingName = true
        NameEditor.setText("")
        UpdatingName = false
        DraftName = ""
        NameEditor.isFocusable = true
        NameEditor.isFocusableInTouchMode = true
        NameEditor.isCursorVisible = true
        NameEditor.requestFocus()
        PaintPreview()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(NameEditor, InputMethodManager.SHOW_IMPLICIT)
    }
    NameEditor.setOnClickListener { StartName() }
    NameEditor.setOnEditorActionListener { _, Action, Event ->
        if (Action == EditorInfo.IME_ACTION_DONE || Event?.keyCode == KeyEvent.KEYCODE_ENTER) {
            FinishName()
            true
        } else false
    }
    NameEditor.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(S: CharSequence?, Start: Int, Count: Int, After: Int) {}
        override fun onTextChanged(S: CharSequence?, Start: Int, Before: Int, Count: Int) {
            if (UpdatingName) return
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
        if (EditingName) FinishName()
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
        SavedName = NewName
        ApplySavedAppearance()
        ActiveCacheView?.ShowIdentity(NewName)
        RefreshPublicQr()
        PaintPreview()
    }
    PaintPreview()
}


        // One Secret page, with exactly four words. Entries are text, not
        // Android-looking input boxes. Only the currently edited value uses
        // Secondary; committed values turn neutral gray. No cursor is drawn.
        // The Secret is a local unlock gate, never Genesis entropy.
internal fun ControlPanel.AddSecretPage(
    Page: LinearLayout, MenuRowDp: Int, AddSuccessIndicator: () -> TextView,
    FlashSuccess: (TextView) -> Unit, IsSecretCurrent: () -> Boolean
) {
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
                        if (!IsSecretCurrent() || !Authenticated ||
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


internal fun ControlPanel.BuildWordGrid() {
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
                val WordArea = FrameLayout(this@BuildWordGrid)
                WordArea.addView(Word, FrameLayout.LayoutParams(-1, -1))
                WordArea.addView(Suggestion, FrameLayout.LayoutParams(-2, -1, Gravity.START))
                addView(WordArea, LinearLayout.LayoutParams(0, -1, 1f))
            }
            val Params = GridLayout.LayoutParams(
                GridLayout.spec(Index / 3), GridLayout.spec(Index % 3, 1f)
            ).apply {
                width = 0
                height = Dp(40)
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
                    if (ChangingWords || RecoveryModeNow != ControlPanel.RecoveryMode.EDITING) return
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
                if (RecoveryModeNow == ControlPanel.RecoveryMode.EDITING &&
                    (ActionId == EditorInfo.IME_ACTION_NEXT || ActionId == EditorInfo.IME_ACTION_DONE ||
                        Event?.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    CompleteWord(Index)
                    true
                } else false
            }
            Word.setOnFocusChangeListener { _, HasFocus ->
                if (RecoveryModeNow == ControlPanel.RecoveryMode.EDITING && HasFocus) HighlightWord(Index)
            }
            Cell.setOnClickListener {
                when (RecoveryModeNow) {
                    ControlPanel.RecoveryMode.REVEALED -> CopyRecovery()
                    ControlPanel.RecoveryMode.EDITING -> FocusWord(Index)
                    ControlPanel.RecoveryMode.HIDDEN -> Unit
                }
            }
            Word.setOnClickListener {
                if (RecoveryModeNow == ControlPanel.RecoveryMode.REVEALED) CopyRecovery()
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
            if (RecoveryModeNow == ControlPanel.RecoveryMode.REVEALED) CopyRecovery()
        }
        WordGrid.setOnLongClickListener { PasteRecoveryFromClipboard() }
        WordGrid.isLongClickable = true
    }

internal fun ControlPanel.ShowRecoveryNotice(Message: String, Duration: Long = 1800L) {
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
            if (RecoveryModeNow == ControlPanel.RecoveryMode.HIDDEN &&
                RecoverySecret.visibility == View.VISIBLE) {
                if (Message != "Enter your Secret") ShowRecoveryNotice("Enter your Secret", 0L)
                return@postDelayed
            }
            RecoveryHint.animate().alpha(0f).setDuration(260L).withEndAction {
                if (RecoveryNoticeRevision == Revision) RecoveryHint.visibility = View.INVISIBLE
            }.start()
        }, Duration)
    }

internal fun ControlPanel.StopWordPulse() {
        WordPulse?.cancel()
        WordPulse = null
        for (Word in WordViews) Word.alpha = 1f
    }

internal fun ControlPanel.PulseFocusedEllipsis(Index: Int) {
        StopWordPulse()
        if (RecoveryModeNow != ControlPanel.RecoveryMode.EDITING ||
            Index !in WordViews.indices || WordViews[Index].text.isNotEmpty()) return
        val Word = WordViews[Index]
        WordPulse = ObjectAnimator.ofFloat(Word, View.ALPHA, 1f, 0.25f, 1f).apply {
            duration = 1150L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

internal fun ControlPanel.BeginRecoverySecret() {
        if (RecoveryModeNow != ControlPanel.RecoveryMode.HIDDEN || RecoverySecretBusy) return
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

internal fun ControlPanel.SubmitRecoverySecret() {
        if (RecoveryModeNow != ControlPanel.RecoveryMode.HIDDEN || RecoverySecretBusy ||
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
                if (Words != null && Authenticated && SettingsActive &&
                    RecoveryModeNow == ControlPanel.RecoveryMode.HIDDEN && RecoverySecret.visibility == View.VISIBLE) {
                    ShowRevealedWords(Words!!)
                } else if (RecoveryModeNow == ControlPanel.RecoveryMode.HIDDEN) {
                    UpdateRecoveryControls()
                    if (RecoverySecret.visibility == View.VISIBLE) {
                        ShowRecoveryNotice("Incorrect Secret", 1100L)
                        RecoverySecret.requestFocus()
                    }
                }
            }
        }.start()
    }

internal fun ControlPanel.PasteRecoveryFromClipboard(): Boolean {
        if (RecoveryModeNow != ControlPanel.RecoveryMode.EDITING) return false
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

internal fun ControlPanel.UpdateRecoveryControls() {
        RevealButton.text = when (RecoveryModeNow) {
            ControlPanel.RecoveryMode.HIDDEN -> "Reveal"
            ControlPanel.RecoveryMode.REVEALED -> "Restore"
            ControlPanel.RecoveryMode.EDITING -> "Save"
        }
        val EnteringSecret = RecoveryModeNow == ControlPanel.RecoveryMode.HIDDEN &&
            RecoverySecret.visibility == View.VISIBLE
        RevealButton.visibility = if (EnteringSecret) View.GONE else View.VISIBLE
        if (RecoveryModeNow != ControlPanel.RecoveryMode.EDITING) RevealButton.isEnabled = !RecoverySecretBusy
        RevealButton.alpha = if (RevealButton.isEnabled) 1f else 0.38f
    }

internal fun ControlPanel.HideRecovery() {
        HideKeyboard()
        StopWordPulse()
        RecoveryModeNow = ControlPanel.RecoveryMode.HIDDEN
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

internal fun ControlPanel.ShowRevealedWords(Words: List<String>) {
        HideKeyboard()
        StopWordPulse()
        RecoverySecret.clearFocus()
        RecoverySecret.text.clear()
        RecoverySecret.visibility = View.GONE
        RecoveryNoticeRevision++
        RecoveryHint.animate().cancel()
        RecoveryHint.visibility = View.INVISIBLE
        RevealedWords = Words.toList()
        RecoveryModeNow = ControlPanel.RecoveryMode.REVEALED
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

internal fun ControlPanel.StartRestore() {
        if (RecoveryModeNow != ControlPanel.RecoveryMode.REVEALED) return
        if (!Bip39Dictionary.IsReady()) {
            RevealButton.isEnabled = false
            RevealButton.alpha = 0.38f
            ShowRecoveryNotice("Preparing recovery dictionary…", 2500L)
            Thread {
                var Loaded = true
                try { Bip39Dictionary.Load(applicationContext) } catch (_: Exception) { Loaded = false }
                runOnUiThread {
                    RevealButton.isEnabled = true
                    if (Loaded && Authenticated && SettingsActive && RecoveryModeNow == ControlPanel.RecoveryMode.REVEALED) {
                        EnterRestore()
                    } else if (!Loaded) ShowRecoveryNotice("Unable to load recovery dictionary.", 2800L)
                }
            }.start()
        } else EnterRestore()
    }

internal fun ControlPanel.EnterRestore() {
        RecoveryModeNow = ControlPanel.RecoveryMode.EDITING
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

internal fun ControlPanel.FocusWord(Index: Int) {
        if (RecoveryModeNow != ControlPanel.RecoveryMode.EDITING) return
        val Target = WordViews[Index]
        Target.requestFocus()
        Target.setSelection(Target.length())
        HighlightWord(Index)
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(Target, InputMethodManager.SHOW_IMPLICIT)
    }

internal fun ControlPanel.HighlightWord(Index: Int) {
        for (I in WordViews.indices) {
            val Selected = I == Index
            WordViews[I].setTextColor(if (Selected || WordViews[I].text.isNotEmpty())
                SecondaryAccent else Color.parseColor("#8193A7"))
            WordViews[I].setHintTextColor(if (Selected) SecondaryAccent else Color.parseColor("#52667C"))
        }
        PulseFocusedEllipsis(Index)
        ShowSuggestion(Index)
    }

internal fun ControlPanel.ShowSuggestion(Index: Int) {
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

internal fun ControlPanel.OnWordTyped(Index: Int) {
        if (RecoveryModeNow != ControlPanel.RecoveryMode.EDITING) return
        val Raw = WordViews[Index].text.toString().lowercase(Locale.ROOT)
        if (Bip39Dictionary.IsWord(applicationContext, Raw) && !Bip39Dictionary.HasLonger(applicationContext, Raw)) {
            Advance(Index, Raw)
        } else {
            HighlightWord(Index)
            UpdateSaveState()
        }
    }

internal fun ControlPanel.CompleteWord(Index: Int) {
        val Raw = WordViews[Index].text.toString().lowercase(Locale.ROOT)
        val Complete = Bip39Dictionary.Completion(applicationContext, Raw)
        if (Complete != null && Bip39Dictionary.IsWord(applicationContext, Complete)) {
            Advance(Index, Complete)
        } else ShowRecoveryNotice("Finish a valid BIP39 word.")
    }

internal fun ControlPanel.Advance(Index: Int, Completed: String) {
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

internal fun ControlPanel.ApplyPastedWords(Text: String): Boolean {
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

internal fun ControlPanel.CandidateEntropy(): ByteArray? {
        if (RecoveryModeNow != ControlPanel.RecoveryMode.EDITING) return null
        val Input = WordViews.map { it.text.toString().lowercase(Locale.ROOT) }
        if (Input.size != 12 || Input.any { !Bip39Dictionary.IsWord(applicationContext, it) }) return null
        return try { GenesisWords.ToEntropy(Input) } catch (_: Exception) { null }
    }

internal fun ControlPanel.UpdateSaveState() {
        if (RecoveryModeNow != ControlPanel.RecoveryMode.EDITING) return
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

internal fun ControlPanel.SaveRestore() {
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
                                    if (Authenticated && SettingsActive) {
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

internal fun ControlPanel.CopyRecovery() {
        if (RecoveryModeNow != ControlPanel.RecoveryMode.REVEALED || RevealedWords.size != 12) return
        val Clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val Clip = ClipData.newPlainText("Mowsie Recovery", RevealedWords.joinToString(" "))
        Clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        Clipboard.setPrimaryClip(Clip)
        ShowRecoveryNotice("✓ Copied", 1100L)
    }

internal fun ControlPanel.AskSecret(Title: String, Work: (CharArray) -> List<String>, Done: (List<String>) -> Unit) {
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

internal fun ControlPanel.SecretInput(Label: String): EditText = PhosphorEditText(this).apply {
        hint = Label
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setSingleLine(true)
    }

internal fun ControlPanel.DialogContainer(Field: EditText): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Dp(24), 0, Dp(24), 0)
        addView(Field)
    }

    /**
     * Front door only. The Ω is stationary, a gently bobbing crystal wakes
     * Secret/Confirm, and an invisible EditText retains native IME behavior.
     * No changes to Genesis, QR, Recovery Hood, or cryptographic operations.
     */


/** Only the Settings index scrolls. Editing pages keep their controls stationary. */
internal class SettingsScrollView(Context: Context) : ScrollView(Context) {
    var ScrollEnabled: Boolean = true
        set(Value) {
            field = Value
            if (!Value) scrollTo(0, 0)
        }

    override fun onInterceptTouchEvent(Event: android.view.MotionEvent): Boolean =
        ScrollEnabled && super.onInterceptTouchEvent(Event)

    override fun onTouchEvent(Event: android.view.MotionEvent): Boolean =
        ScrollEnabled && super.onTouchEvent(Event)

    override fun onGenericMotionEvent(Event: android.view.MotionEvent): Boolean =
        ScrollEnabled && super.onGenericMotionEvent(Event)
}

/** Settings content is independent of the moving upper/lower door surfaces. */
internal class SettingsNavigator(
    val Refresh: (Boolean) -> Unit,
    val Navigate: (String) -> Unit,
    val Clear: () -> Unit,
    val Back: () -> Boolean,
    val IsRoot: () -> Boolean,
    val DetachRecovery: () -> Unit
) {
    /** Provided by Lock after installation. Internal editor navigation uses the same seal. */
    var Exchange: ((() -> Unit) -> Unit)? = null
}

internal fun ControlPanel.BuildSettings(
    Page: LinearLayout, Scroll: SettingsScrollView, Selected: PhosphorTextView,
    RecoveryPanel: LinearLayout, MenuTopDp: Int, MenuRowDp: Int,
    MenuFontSp: Float
): SettingsNavigator {
    val Ink = Color.parseColor("#F0F3FA")
    val Muted = Color.parseColor("#9BABBF")
    val Path = mutableListOf<String>()
    var Navigator: SettingsNavigator? = null
    fun ChangeBehindLock(Change: () -> Unit) {
        val Exchange = Navigator?.Exchange
        if (Exchange == null) Change() else Exchange(Change)
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
        RecoveryPanel.setPadding(Dp(1), 0, Dp(1), Dp(6))
        RecoveryPanel.visibility = View.GONE

        var NavigateTo: (String) -> Unit = {}
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

        fun Refresh(AnimateTitle: Boolean = true) {
            ClearSecretPage?.invoke()
            ClearSecretPage = null
            SecretOutsideTap = null
            HideKeyboard()
            if (Path.lastOrNull() != "Recovery") { HideRecovery(); DetachRecovery() }
            Page.removeAllViews()
            val Name = Current()
            // Only the root Settings index scrolls. The three editors use
            // compact fixed slots to fit inside the 320dp QR chamber.
            Scroll.ScrollEnabled = RootMenu()
            val Compact = Name == "Appearance" || Name == "Secret" || Name == "Recovery"
            val RowDp = if (Compact) 44 else MenuRowDp
            // Shift the whole Settings composition, including the title,
            // down as one unit. This applies to root and every subpage; it does
            // not alter relative row spacing or the scroll policy.
            val SettingsDropDp = 20
            Page.setPadding(Dp(22), Dp((if (Compact) 4 else MenuTopDp) + SettingsDropDp),
                Dp(22), Dp(if (Compact) 4 else 12))
            Selected.animate().cancel()
            Selected.text = Name
            Selected.setTextColor(Accent)
            Selected.contentDescription = if (RootMenu())
                "Settings. Choose a configuration" else "$Name. Back one menu"
            Page.addView(Selected, LinearLayout.LayoutParams(-1, Dp(RowDp)))
            if (RootMenu()) {
                listOf("Appearance", "Secret", "Recovery", "Stasis").forEach { Entry ->
                    AddItem(Entry) { NavigateTo(Entry) }
                }
            } else when (Name) {
                "Appearance" -> AddAppearancePage(Page, Selected, MenuFontSp, RowDp)
                "Recovery" -> {
                    DetachRecovery()
                    RecoveryPanel.visibility = View.VISIBLE
                    // The twelve-word module sits under the title as one
                    // continuous component, with enough space for editing.
                    Page.addView(RecoveryPanel, LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = Dp(2)
                    })
                }
                "Secret" -> AddSecretPage(Page, RowDp, ::AddSuccessIndicator, ::FlashSuccess) { Path.lastOrNull() == "Secret" && SettingsActive }
                "Stasis" -> {
                    AddSpacer(18)
                    AddText("Background participation preferences will appear here.")
                    AddText("Android power restrictions will still apply.", 14f)
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

        NavigateTo = { Name -> ChangeBehindLock { Path.add(Name); Refresh() } }
        return SettingsNavigator(
            Refresh = { Animate -> Refresh(Animate) },
            Navigate = { Name -> NavigateTo(Name) },
            Clear = {
                DetachRecovery()
                Path.clear()
                Refresh(false)
            },
            Back = {
                if (Path.isEmpty()) false else {
                    ChangeBehindLock {
                        if (Path.lastOrNull() == "Recovery") DetachRecovery()
                        if (Path.isNotEmpty()) Path.removeAt(Path.lastIndex)
                        Refresh()
                    }
                    true
                }
            },
            IsRoot = { RootMenu() },
            DetachRecovery = { DetachRecovery() }
        ).also { Navigator = it }
}
