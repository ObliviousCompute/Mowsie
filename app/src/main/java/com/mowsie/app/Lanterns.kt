package com.mowsie.app

import android.content.Context

/** Fictional Lantern names; no connection or preference is established. */
internal fun MakeLanternsPage(Context: Context): PreviewWheel = PreviewWheel(Context).apply {
    ActionLabel = "Ignite"
    Entries = listOf(ActionLabel) + PulpData.Lanterns
    SelectIndex(0)
}

internal fun MakeIgnitePage(Context: Context): android.view.View =
    android.widget.FrameLayout(Context).apply {
        addView(PhosphorTextView(Context).apply {
            text = "Ignite\n\nYour Lantern begins here."
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#8B929B"))
        }, android.widget.FrameLayout.LayoutParams(-1, -1))
    }
