package com.mowsie.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Read-only Cache chamber. Header and historical rows scroll as one surface.
 * Entries are synthetic presentation data, never used as real balances,
 * authenticated transfer history, or Soulcrum evidence.
 */
internal class CacheActivityView(Context: Context) : ScrollView(Context) {
    private val Ink = Color.parseColor("#9299A4")
    private val Muted = Color.parseColor("#697987")
    private val Bright = Color.parseColor("#B7BEC8")
    var PrimaryInk: Int = Color.parseColor("#73A9E4")
        set(Value) { field = Value; Identity.setTextColor(Value) }
    private val Column = LinearLayout(Context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        // Match the wheel's overscan to let text scroll beneath both tabs.
        setPadding(Dp(16), Dp(8 + 85), Dp(16), Dp(18 + 85))
    }
    private val History = LinearLayout(Context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private fun Dp(Value: Int) = (Value * resources.displayMetrics.density + .5f).toInt()
    private fun Line(Text: String, Size: Float, ColorInk: Int = Ink, Margin: Int = 1): TextView =
        PhosphorTextView(context).apply {
            text = Text
            textSize = Size
            gravity = Gravity.CENTER
            setTextColor(ColorInk)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = Dp(Margin)
                bottomMargin = Dp(Margin)
            }
        }

    private val Identity = Line("Metatron", 21f, PrimaryInk, 5)
    private val Field = Line("Bob's Donuts", 21f, Bright, 4)
    private val Amount = Line("10,485.75 Salt", 17f, Ink, 6)
    var OnIdentityTapped: (() -> Unit)? = null
    private var PendingHistory: Runnable? = null

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        clipToPadding = false
        addView(Column, LayoutParams(-1, -2))
        Column.addView(Identity)
        Column.addView(Field)
        Column.addView(Amount)
        // These labels are informational, not a highlight wheel.
        // Header is informational only; no hidden QR navigation.
        Column.addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#293746"))
        }, LinearLayout.LayoutParams(-1, Dp(1)).apply {
            topMargin = Dp(14)
            bottomMargin = Dp(8)
        })
        Column.addView(History, LinearLayout.LayoutParams(-1, -2))
        ShowField("Bob's Donuts", "10,485.75 Salt")
        contentDescription = "Cache activity. Scroll to explore the selected Field."
    }

    fun ShowIdentity(Name: String) { Identity.text = Name }

    fun ShowField(Name: String, Balance: String) {
        Field.text = Name
        Amount.text = Balance
        val Selected = PulpData.Fields.firstOrNull { it.Name == Name } ?: PulpData.Fields[2]
        PendingHistory?.let { removeCallbacks(it) }
        val Update = Runnable { PopulateHistory(Selected) }
        PendingHistory = Update
        // Rebuilding forty rows during every intermediate wheel tick would
        // cause dropped frames. Update once the user's scroll settles.
        if (History.childCount == 0) Update.run() else postDelayed(Update, 160L)
    }

    private fun PopulateHistory(Selected: PulpData.FieldEntry) {
        PendingHistory = null
        History.removeAllViews()
        PulpData.Records(Selected).forEach { Record ->
            val Row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Dp(5), Dp(8), Dp(5), Dp(8))
            }
            val Upper = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val Status = Line(Record.Status, 14.5f, Bright, 0).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            val Time = Line(Record.Time, 11.5f, Muted, 0).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            Upper.addView(Status, LinearLayout.LayoutParams(0, -2, 1f))
            Upper.addView(Time, LinearLayout.LayoutParams(-2, -2))
            Row.addView(Upper, LinearLayout.LayoutParams(-1, -2))
            val Detail = Line("${Record.Peer}   ·   ${Record.Value}", 13f, Ink, 2).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                maxLines = 2
            }
            Row.addView(Detail)
            History.addView(Row, LinearLayout.LayoutParams(-1, -2))
            History.addView(View(context).apply {
                setBackgroundColor(Color.parseColor("#1C2B39"))
            }, LinearLayout.LayoutParams(-1, Dp(1)))
        }
        scrollTo(0, 0)
    }
}
