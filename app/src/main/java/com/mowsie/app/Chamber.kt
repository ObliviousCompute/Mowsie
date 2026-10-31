package com.mowsie.app

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView

/** Cache is the crystal's home; four primary modes are selected on Door2. */
internal enum class ChamberMode { Cache, Scan, Peers, Fields, Lanterns, Genesis, Ignite, Settings, Transfer }

/** Only content belongs here. Lock.kt owns every moving part of the selector. */
internal class Chamber(
    Context: ControlPanel,
    private val Window: FrameLayout,
    private val CacheContent: View,
    private val Placeholder: View,
    private val FieldWheel: FieldWheelView,
    private val SettingsPage: ScrollView
) {
    var Mode: ChamberMode = ChamberMode.Cache
        private set

    val PeersWheel = MakePeersPage(Context)
    val Scanner = ScanPage(Context)
    val Transfer = TransferPage(Context)
    val LanternsWheel = MakeLanternsPage(Context)
    private val Pages = mapOf(
        ChamberMode.Peers to PeersWheel,
        ChamberMode.Lanterns to LanternsWheel,
        ChamberMode.Scan to Scanner,
        ChamberMode.Transfer to Transfer,
        ChamberMode.Genesis to MakeGenesisPage(Context),
        ChamberMode.Ignite to MakeIgnitePage(Context)
    )

    init {
        CacheContent.visibility = View.GONE
        Window.addView(CacheContent, FrameLayout.LayoutParams(-1, -1))
        Pages.values.forEach { Page ->
            Page.visibility = View.GONE
            Window.addView(Page, FrameLayout.LayoutParams(-1, -1))
        }
        Show(ChamberMode.Cache)
    }

    fun Show(Next: ChamberMode) {
        if (Mode == ChamberMode.Scan && Next != ChamberMode.Scan) Scanner.Exit()
        if (Mode == ChamberMode.Transfer && Next != ChamberMode.Transfer) Transfer.Leave()
        Mode = Next
        CacheContent.visibility = if (Next == ChamberMode.Cache) View.VISIBLE else View.GONE
        Placeholder.visibility = View.GONE
        FieldWheel.visibility = if (Next == ChamberMode.Fields) View.VISIBLE else View.GONE
        SettingsPage.visibility = if (Next == ChamberMode.Settings) View.VISIBLE else View.GONE
        Pages.forEach { (Type, Page) ->
            Page.visibility = if (Type == Next) View.VISIBLE else View.GONE
        }
        if (Next == ChamberMode.Scan) Scanner.Enter()
    }
}
