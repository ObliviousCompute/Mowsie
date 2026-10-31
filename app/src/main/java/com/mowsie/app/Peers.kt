package com.mowsie.app

import android.content.Context

/** Fictional contact labels for visual evaluation; no peer discovery occurs. */
internal fun MakePeersPage(Context: Context): PreviewWheel = PreviewWheel(Context).apply {
    ActionLabel = "Add"
    Entries = listOf(ActionLabel) + PulpData.Peers
    SelectIndex(3) // Keep Milton centered after inserting Add as row zero.
}
