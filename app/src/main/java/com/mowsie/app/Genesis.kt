package com.mowsie.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/** Field-creation placeholder; no protocol state is written. */
internal fun MakeGenesisPage(Context: Context): View = FrameLayout(Context).apply {
    addView(PhosphorTextView(Context).apply {
        text = "Genesis\n\nYour new Field begins here."
        textSize = 18f
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#8B929B"))
    }, FrameLayout.LayoutParams(-1, -1))
}
