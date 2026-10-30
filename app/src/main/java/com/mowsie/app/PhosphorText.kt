package com.mowsie.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.view.View
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView

/**
 * Static projected-phosphor lettering, tinted by the CURRENT text color.
 * No full-screen shader, running ticker, textures, or perpetual redraws.
 * Repeating shaders are shared by all views of the same color at a given DPI.
 * This class is never used for Ω graphics, crystal artwork, or QR modules.
 */
internal object PhosphorInk {
    private val Textures = mutableMapOf<Long, Shader>()

    fun Apply(Brush: Paint, Ink: Int, Density: Float) {
        val Key = ((Ink.toLong() and 0xFFFFFFFFL) shl 12) or
            ((Density * 256f).toLong() and 0xFFFL)
        val Pattern = Textures.getOrPut(Key) {
            val Period = 2.7f * Density
            // An almost imperceptible phosphor modulation INSIDE the glyph.
            // Color is not clamped, so primary, secondary and gray all work.
            val Dim = Color.argb(Color.alpha(Ink),
                (Color.red(Ink) * .89f).toInt(),
                (Color.green(Ink) * .89f).toInt(),
                (Color.blue(Ink) * .89f).toInt())
            LinearGradient(0f, 0f, 0f, Period,
                intArrayOf(Ink, Ink, Dim, Dim, Ink),
                floatArrayOf(0f, .43f, .57f, .78f, 1f), Shader.TileMode.REPEAT)
        }
        if (Brush.shader !== Pattern) Brush.shader = Pattern
        // Faint glow only, not a bloom or a fuzzy replacement for sharp text.
        Brush.setShadowLayer(1.25f * Density, 0f, 0f,
            Color.argb(34, Color.red(Ink), Color.green(Ink), Color.blue(Ink)))
    }
}

open class PhosphorTextView @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null,
    Style: Int = android.R.attr.textViewStyle
) : AppCompatTextView(Context, Attributes, Style) {
    override fun onDraw(Canvas: Canvas) {
        PhosphorInk.Apply(paint, currentTextColor, resources.displayMetrics.density)
        super.onDraw(Canvas)
    }
}

/**
 * A single centered status word with a neutral check painted just to its right.
 * Centering the word itself keeps Success, Copied and Pasted aligned directly
 * beneath their corresponding action without a check shifting the word.
 */
class PhosphorStatusTextView @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null,
    Style: Int = android.R.attr.textViewStyle
) : PhosphorTextView(Context, Attributes, Style) {
    var ShowStatusCheck: Boolean = false
        set(Value) { field = Value; invalidate() }

    override fun onDraw(Canvas: Canvas) {
        super.onDraw(Canvas)
        val Word = text.toString()
        if (!ShowStatusCheck || Word !in setOf("Success", "Copied", "Pasted")) return
        val Brush = paint
        val Spacing = resources.displayMetrics.density * 7f
        // The text itself is centered by TextView's Gravity.CENTER. Add the
        // check beyond the measured word without changing the layout.
        val X = width / 2f + Brush.measureText(Word) / 2f + Spacing
        val Baseline = height / 2f - (Brush.fontMetrics.ascent + Brush.fontMetrics.descent) / 2f
        Canvas.drawText("✓", X, Baseline, Brush)
    }
}

class PhosphorEditText @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null,
    Style: Int = android.R.attr.editTextStyle
) : AppCompatEditText(Context, Attributes, Style) {
    override fun onDraw(Canvas: Canvas) {
        val Ink = if (text.isNullOrEmpty() && !hint.isNullOrEmpty()) currentHintTextColor
            else currentTextColor
        PhosphorInk.Apply(paint, Ink, resources.displayMetrics.density)
        super.onDraw(Canvas)
    }
}

class PhosphorButton @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null,
    Style: Int = android.R.attr.buttonStyle
) : AppCompatButton(Context, Attributes, Style) {
    override fun onDraw(Canvas: Canvas) {
        PhosphorInk.Apply(paint, currentTextColor, resources.displayMetrics.density)
        super.onDraw(Canvas)
    }
}
