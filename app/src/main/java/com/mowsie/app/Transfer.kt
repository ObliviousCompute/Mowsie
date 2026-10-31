package com.mowsie.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import kotlin.math.floor
import java.util.Locale

/** Purely visual transfer composition. Never mutates a balance, field or store. */
internal class TransferPage(private val Host: ControlPanel) : FrameLayout(Host) {
    private val Grey = Color.parseColor("#A1ADB8")
    private val Dim = Color.parseColor("#75899B")
    private var Recipient: ScanIdentity? = null
    private var SelectedField: PulpData.FieldEntry = PulpData.Fields[2]
    private var Available = 0.0
    private var Amount = 0
    private var Stage = 0 // 0 compose, 1 message, 2 persistent Sent review
    private fun Dp(Size: Int) = Host.Dp(Size)
    private fun Line(Content: String, Size: Float, Ink: Int, Bold: Boolean = false) =
        PhosphorTextView(Host).apply {
            text = Content
            textSize = Size
            gravity = Gravity.CENTER
            setTextColor(Ink)
            includeFontPadding = false
            if (Bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    private val Header = LinearLayout(Host).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private val Sender = Line("", 21f, Host.Accent)
    private val Field = Line("", 21f, Grey)
    private val Balance = Line("", 17f, Grey)
    private val Slider = SeekBar(Host).apply {
        max = 100
        progress = 0
        setSplitTrack(false)
        progressTintList = ColorStateList.valueOf(Color.parseColor("#9CA7B3"))
        progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#344454"))
        thumbTintList = ColorStateList.valueOf(Color.parseColor("#CFD4DC"))
        contentDescription = "Mock Salt allocation"
        setPadding(Dp(8), Dp(8), Dp(8), Dp(8))
    }
    private val RecipientGroup = LinearLayout(Host).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private val RecipientAmount = Line("0 Salt", 17f, Grey)
    private val RecipientName = Line("", 21f, Grey)
    private val RecipientKey = Line("", 13f, Dim)
    private val Action = Line("Confirm", 21f, Host.Accent).apply {
        isClickable = true
        isFocusable = true
        contentDescription = "Confirm mock transfer amount"
    }
    private val Message = MowsieMemo(Host).apply {
        hint = "Message..."
        setHintTextColor(Dim)
        setTextColor(Host.Accent)
        CursorInk = Host.SecondaryAccent
        textSize = 17f
        gravity = Gravity.TOP or Gravity.START
        background = null
        setPadding(0, 0, 0, 0)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setSingleLine(false)
        maxLines = 7
        isVerticalScrollBarEnabled = false
        imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
    }
    // A frozen message with value attached, not a generic success screen.
    // These views are read-only and preserve the actual proposed recipient,
    // amount and optional memo from the immediately preceding composer.
    private val Receipt = LinearLayout(Host).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(Dp(16), Dp(4), Dp(16), Dp(4))
        visibility = View.GONE
        isClickable = true
        contentDescription = "Mock Sent receipt. Tap to return to Cache."
    }
    private val ReceiptMemo = Line("", 17f, Host.Accent)
    private val ReceiptAmount = Line("", 17f, Grey)
    private val ReceiptName = Line("", 21f, Grey)
    private val ReceiptKey = Line("", 13f, Dim)
    private val Sent = PhosphorStatusTextView(Host).apply {
        text = "Sent"
        ShowStatusCheck = true
        textSize = 21f
        gravity = Gravity.CENTER
        setTextColor(Host.Accent)
        includeFontPadding = false
    }
    var OnSuccessTapped: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        Header.addView(Sender, LinearLayout.LayoutParams(-1, Dp(29)))
        Header.addView(Field, LinearLayout.LayoutParams(-1, Dp(29)))
        Header.addView(Balance, LinearLayout.LayoutParams(-1, Dp(26)))
        addView(Header, LayoutParams(-1, Dp(90), Gravity.TOP).apply {
            topMargin = Dp(4)
        })
        addView(Slider, LayoutParams(-1, Dp(58), Gravity.CENTER_VERTICAL).apply {
            leftMargin = Dp(18)
            rightMargin = Dp(18)
            topMargin = -Dp(23)
        })
        RecipientGroup.addView(RecipientAmount, LinearLayout.LayoutParams(-1, Dp(27)))
        RecipientGroup.addView(RecipientName, LinearLayout.LayoutParams(-1, Dp(29)))
        RecipientGroup.addView(RecipientKey, LinearLayout.LayoutParams(-1, Dp(22)))
        addView(RecipientGroup, LayoutParams(-1, Dp(80), Gravity.BOTTOM).apply {
            bottomMargin = Dp(47)
        })
        addView(Action, LayoutParams(-1, Dp(42), Gravity.BOTTOM).apply {
            bottomMargin = Dp(2)
        })
        addView(Message, LayoutParams(-1, Dp(158), Gravity.TOP).apply {
            topMargin = Dp(11)
            leftMargin = Dp(22)
            rightMargin = Dp(22)
        })
        Receipt.addView(ReceiptMemo, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = Dp(19)
        })
        Receipt.addView(ReceiptAmount, LinearLayout.LayoutParams(-1, Dp(27)))
        Receipt.addView(ReceiptName, LinearLayout.LayoutParams(-1, Dp(29)))
        Receipt.addView(ReceiptKey, LinearLayout.LayoutParams(-1, Dp(22)))
        Receipt.addView(Sent, LinearLayout.LayoutParams(-1, Dp(48)).apply {
            topMargin = Dp(19)
        })
        // Put the entire completed message a little below center. No card,
        // receipt border, or header that introduces a different visual style.
        addView(Receipt, LayoutParams(-1, -2, Gravity.CENTER).apply {
            topMargin = Dp(25)
            leftMargin = Dp(12)
            rightMargin = Dp(12)
        })
        Slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(Bar: SeekBar, Progress: Int, FromUser: Boolean) {
                if (Stage != 0) return
                Amount = Progress
                RefreshAmount()
            }
            override fun onStartTrackingTouch(Bar: SeekBar) {}
            override fun onStopTrackingTouch(Bar: SeekBar) {}
        })
        Action.setOnClickListener {
            when (Stage) {
                0 -> if (Amount > 0) ShowMessage()
                1 -> ShowSuccess()
            }
        }
        Receipt.setOnClickListener { OnSuccessTapped?.invoke() }
        ShowCompose()
    }

    fun Begin(Identity: ScanIdentity, Name: String, FieldData: PulpData.FieldEntry) {
        Recipient = Identity
        SelectedField = FieldData
        Available = FieldData.Balance.substringBefore(' ').replace(",", "").toDoubleOrNull() ?: 0.0
        Sender.text = Name
        Sender.setTextColor(Host.Accent)
        Field.text = FieldData.Name
        val Upper = floor(Available.coerceAtLeast(0.0)).toInt().coerceAtMost(999999)
        Slider.max = Upper
        Slider.progress = 0
        Amount = 0
        RecipientName.text = Identity.Name
        RecipientKey.text = Identity.ShortKey
        Message.setText("")
        Message.setTextColor(Host.Accent)
        Message.CursorInk = Host.SecondaryAccent
        ShowCompose()
    }

    private fun Format(Value: Double): String =
        String.format(Locale.US, "%,.2f", Value.coerceAtLeast(0.0))

    private fun RefreshAmount() {
        Balance.text = "${Format(Available - Amount)} ${SelectedField.Currency}"
        RecipientAmount.text = "${Format(Amount.toDouble())} ${SelectedField.Currency}"
        Action.alpha = if (Amount > 0) 1f else .36f
        Action.isEnabled = Amount > 0
    }

    private fun ShowCompose() {
        Stage = 0
        Header.visibility = View.VISIBLE
        Slider.visibility = View.VISIBLE
        RecipientGroup.visibility = View.VISIBLE
        Action.visibility = View.VISIBLE
        Action.text = "Confirm"
        Message.visibility = View.GONE
        Receipt.visibility = View.GONE
        alpha = 1f
        RefreshAmount()
    }

    private fun ShowMessage() {
        Stage = 1
        Header.visibility = View.GONE
        Slider.visibility = View.GONE
        RecipientGroup.visibility = View.VISIBLE
        Action.visibility = View.VISIBLE
        Action.text = "Send"
        Action.isEnabled = true
        Action.alpha = 1f
        Message.visibility = View.VISIBLE
        // "Message..." is the sole prompt. Don't take focus until tapped.
        Host.Root.requestFocus()
    }

    private fun ShowSuccess() {
        Stage = 2
        val Keyboard = Host.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        Keyboard.hideSoftInputFromWindow(Message.windowToken, 0)
        Message.clearFocus()
        ReceiptMemo.text = Message.text?.toString()?.trim().orEmpty()
        ReceiptMemo.visibility = if (ReceiptMemo.text.isEmpty()) View.GONE else View.VISIBLE
        ReceiptMemo.setTextColor(Host.Accent)
        ReceiptAmount.text = RecipientAmount.text
        ReceiptName.text = RecipientName.text
        ReceiptKey.text = RecipientKey.text
        Sent.setTextColor(Host.Accent)
        animate().cancel()
        animate().alpha(0f).setDuration(190L).withEndAction {
            Header.visibility = View.GONE
            Slider.visibility = View.GONE
            RecipientGroup.visibility = View.GONE
            Action.visibility = View.GONE
            Message.visibility = View.GONE
            Receipt.visibility = View.VISIBLE
            animate().alpha(1f).setDuration(260L).start()
        }.start()
        // Visual mock only. No transaction, balance change or transmission.
    }

    fun Leave() {
        val Keyboard = Host.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        Keyboard.hideSoftInputFromWindow(Message.windowToken, 0)
        Message.clearFocus()
        animate().cancel()
        alpha = 1f
        Message.setText("")
        Receipt.visibility = View.GONE
        Recipient = null
        Stage = 0
    }
}

/** Invisible typing surface with a reliably secondary-colored caret on API 24+.
 * The platform draws text and handles selection/IME; only the cursor is custom.
 * No border, decoration, helper label, or visible text box is added. */
internal class MowsieMemo(Context: Context) : PhosphorEditText(Context) {
    var CursorInk: Int = Color.parseColor("#B3D0BF")
        set(Value) { field = Value; invalidate() }
    private val CursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var Lit = false
    private val Blink = object : Runnable {
        override fun run() {
            if (!hasFocus() || !isAttachedToWindow) return
            Lit = !Lit
            invalidate()
            postDelayed(this, 530L)
        }
    }

    init { isCursorVisible = false }

    override fun onFocusChanged(Focused: Boolean, Direction: Int, PreviouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(Focused, Direction, PreviouslyFocusedRect)
        removeCallbacks(Blink)
        Lit = Focused
        invalidate()
        if (Focused) postDelayed(Blink, 530L)
    }

    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        if (!Lit || !hasFocus()) return
        val Lines = layout ?: return
        val Offset = selectionStart
        if (Offset < 0 || selectionEnd != Offset) return
        val LineIndex = Lines.getLineForOffset(Offset.coerceIn(0, text?.length ?: 0))
        val Left = totalPaddingLeft + Lines.getPrimaryHorizontal(Offset) - scrollX
        val Top = totalPaddingTop + Lines.getLineTop(LineIndex) - scrollY
        val Bottom = totalPaddingTop + Lines.getLineBottom(LineIndex) - scrollY
        CursorPaint.color = CursorInk
        Canvas.drawRoundRect(Left, Top.toFloat(), Left + resources.displayMetrics.density * 1.8f,
            Bottom.toFloat(), 1f, 1f, CursorPaint)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(Blink)
        super.onDetachedFromWindow()
    }
}
