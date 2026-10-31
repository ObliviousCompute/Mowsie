package com.mowsie.app

import android.content.Context
import android.util.AttributeSet

/** Field selection uses the exact same luminous wheel as Peers and Lanterns. */
internal class FieldWheelView @JvmOverloads constructor(
    Context: Context, Attributes: AttributeSet? = null
) : PreviewWheel(Context, Attributes) {
    data class Field(val Name: String, val Balance: String)
    val Fields = PulpData.Fields.map { Field(it.Name, it.Balance) }
    var OnFieldChanged: ((Field) -> Unit)? = null
    var CurrentField: Field = Fields[2]
        private set

    init {
        ActionLabel = "Genesis"
        Entries = listOf(ActionLabel) + Fields.map { it.Name }
        OnSelected = { WheelIndex ->
            // Position zero is Genesis, not a Field. Scrolling past it
            // must never select or alter the active Field.
            if (WheelIndex > 0) {
                CurrentField = Fields[WheelIndex - 1]
                OnFieldChanged?.invoke(CurrentField)
            }
        }
        // Bob's Donuts remains the initial selected Field.
        SelectField(2)
    }

    fun SelectField(Index: Int) {
        val Actual = Index.coerceIn(0, Fields.lastIndex)
        CurrentField = Fields[Actual]
        SelectIndex(Actual + 1)
    }
}
