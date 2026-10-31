package com.mowsie.app

import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback


internal fun ControlPanel.InstallLock(LogoToggle: FrameLayout, RecoveryPanel: LinearLayout, BottomDoor: FrameLayout) {

        // Keep the original 110dp header slot, with a stationary upper shell.
        // Settings belongs to the chamber. No dropdown hood motion remains.
        LogoToggle.visibility = View.INVISIBLE
        val Navigator = FrameLayout(this).apply {
            visibility = View.VISIBLE
            clipChildren = false
            clipToPadding = false
            isClickable = false
            contentDescription = "Stationary upper orb tab"
        }
        Root.addView(Navigator, FrameLayout.LayoutParams(-1, Dp(110), Gravity.TOP))
        val UpperSurface = HoodSheetView(this)
        Navigator.addView(UpperSurface, FrameLayout.LayoutParams(-1, -1))

        // Lift the entire lower door, INCLUDING its text, out of the ordinary
        // vertical layout. This creates two true independent solid overlays.
        // Ordering: QR backdrop, shared shadows, lower door, upper hood, gem.
        val DoorShadows = DoorShadowsView(this, Navigator, BottomDoor)
        Root.addView(DoorShadows, 1, FrameLayout.LayoutParams(-1, -1))
        (BottomDoor.parent as android.view.ViewGroup).removeView(BottomDoor)
        BottomDoor.visibility = View.INVISIBLE
        Root.addView(BottomDoor, 2, FrameLayout.LayoutParams(-1, -1, Gravity.TOP))
        val LowerSurface = BottomDoor.findViewById<BottomDoorView>(R.id.bottomDoorSurface)

        val Scroll = SettingsScrollView(this).apply {
            visibility = View.GONE
            setFillViewport(true)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        // Settings now lives in the chamber; the upper Ω never descends.
        // Scroll is mounted into QrWindow after the existing field wheel.
        // Settings has one scrollable editor, reparented into the chamber.
        val MenuRowDp = 48
        val MenuFontSp = 20f
        val MenuTopDp = 8
        val Page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(Dp(22), Dp(MenuTopDp), Dp(22), Dp(12))
        }
        Scroll.addView(Page, FrameLayout.LayoutParams(-1, -2))

        // The stationary upper tab now displays one floating orb instead of Ω.
        val Crystal = MountFloatingCrystal(BottomDoor)
        ActiveCrystal = Crystal
        val GemTouch = Crystal.Touch

        // The highlighted title also acts as Back. Settings.Refresh(true)
        // inserts this SAME view at index zero on every page.
        val Selected = PhosphorTextView(this).apply {
            text = "Settings"
            gravity = Gravity.CENTER
            textSize = MenuFontSp
            includeFontPadding = false
            setTextColor(Accent)
            setShadowLayer(Dp(3).toFloat(), 0f, Dp(1).toFloat(), Color.BLACK)
            alpha = 0f
            visibility = View.GONE
            isClickable = true
            isFocusable = false
            if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
            contentDescription = "Settings"
        }

        val Settings = BuildSettings(
            Page, Scroll, Selected, RecoveryPanel, MenuTopDp, MenuRowDp, MenuFontSp)
        // Door1 geometry is measured from the stationary upper Ω and QR.
        var LowerClosed = true
        var LowerTravel = 0f
        var LowerAnimator: ValueAnimator? = null
        var LowerReady = false
        val QrWindow = findViewById<FrameLayout>(R.id.qrWindow)
        val QrPattern = findViewById<MowsieQrView>(R.id.qrPattern)
        val QrPlaceholder = findViewById<TextView>(R.id.qrPlaceholder)
        val StashDoorName = findViewById<TextView>(R.id.stashDoorName)
        val FieldName = findViewById<TextView>(R.id.fieldName)
        val BalanceText = findViewById<TextView>(R.id.balanceText)

        // The wheel lives in the same Oblivion gap as the QR. No extra panel.
        val CacheActivity = CacheActivityView(this).apply {
            PrimaryInk = Accent
            ShowIdentity(getSharedPreferences("MowsieNest", android.content.Context.MODE_PRIVATE)
                .getString("StashName", "Metatron") ?: "Metatron")
        }
        ActiveCacheView = CacheActivity
        val FieldWheel = FieldWheelView(this).apply {
            visibility = View.GONE
            ActionInk = Accent
            OnFieldChanged = { Field ->
                FieldName.text = Field.Name
                BalanceText.text = Field.Balance
                CacheActivity.ShowField(Field.Name, Field.Balance)
            }
        }
        ActiveFieldWheel = FieldWheel
        QrWindow.addView(FieldWheel, FrameLayout.LayoutParams(-1, -1))
        // Reparent the real settings editor into the QR chamber, not a copy.
        QrWindow.addView(Scroll, FrameLayout.LayoutParams(-1, -1))
        // One real public identity QR, permanently mounted on the outer door.
        // Never create a duplicate inside the chamber or an alternate QR page.
        StashDoorName.visibility = View.GONE
        (QrPattern.parent as android.view.ViewGroup).removeView(QrPattern)
        BottomDoor.addView(QrPattern, FrameLayout.LayoutParams(Dp(272), Dp(272),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        QrPattern.visibility = View.GONE // Crystal-controlled, never visible on Threshold.
        val CacheView = CacheActivity
        val CacheActivityPage = CacheActivity
        val Chamber = Chamber(this, QrWindow, CacheActivityPage,
            QrPlaceholder, FieldWheel, Scroll)
        Chamber.Show(ChamberMode.Cache)
        ActivePeersWheel = Chamber.PeersWheel.apply { ActionInk = Accent }
        ActiveLanternsWheel = Chamber.LanternsWheel.apply { ActionInk = Accent }
        // Scroll overscan must extend beyond the upper tab's shoulder, not
        // merely beyond the 320dp QR chamber. The outer vertical host also
        // disables child clipping in activity_main.xml; otherwise its window
        // starts beneath the 110dp hood and cuts text off before the metal.
        // Equal top/bottom overscan keeps all three wheel focal lines centered.
        // Door geometry, selector tabs, touch targets and QR position are fixed.
        val ScrollOverscan = Dp(85)
        listOf<View>(CacheActivity, FieldWheel, Chamber.PeersWheel,
            Chamber.LanternsWheel).forEach { Page ->
            Page.layoutParams = FrameLayout.LayoutParams(-1, -1).apply {
                topMargin = -ScrollOverscan
                bottomMargin = -ScrollOverscan
            }
        }
        ActiveScanView = Chamber.Scanner
        fun SelectChamber(Mode: ChamberMode) {
            if (Mode != ChamberMode.Settings) {
                ClearSecretPage?.invoke()
                ClearSecretPage = null
                SecretOutsideTap = null
                HideRecovery()
                Settings.DetachRecovery()
                if (Chamber.Mode == ChamberMode.Settings) Settings.Clear()
            } else {
                Settings.Refresh(false)
                Selected.visibility = View.VISIBLE
            }
            SettingsActive = Mode == ChamberMode.Settings
            // The exterior QR remains on Door1 regardless of chamber mode.
            Chamber.Show(Mode)
        }


        // Door2 carries one floating Opal toward the fixed receiving Trinity.
        // The upper tab owns the stationary three-orb socket and artwork.
        val Airlock = AirlockHatchView(this).apply {
            Mode = "CHAMBERS"
            PrimaryInk = Accent
            SecondaryInk = SecondaryAccent
            isClickable = true
            isLongClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
            if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
            contentDescription = "Trinity airlock hatch"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        ActiveAirlock = Airlock
        // The long sheet slides behind Door1 without exposing its bottom edge
        // at the open stop. Its Ω anchor and shadow geometry remain fixed.
        // Door2 is one moving assembly: its metal surface and its single hovering
        // orb share the same physical position and 205dp travel.
        val AirlockAssembly = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            isClickable = false
        }
        AirlockAssembly.addView(Airlock, FrameLayout.LayoutParams(-1, -1))
        Root.addView(AirlockAssembly, 2,
            FrameLayout.LayoutParams(-1, Dp(470), Gravity.TOP))
        // Draw the solid hatch below Door1 so its recess still has real
        // occlusion and contact shadows. The orbs themselves are luminous
        // floating objects ABOVE the metal, and must remain visible at the
        // CLOSED stop when the Trinity surrounds the stationary upper orb.
        val AirlockDepth = AirlockDepthShadowsView(this, Airlock, BottomDoor, Navigator)
        Root.addView(AirlockDepth, 3, FrameLayout.LayoutParams(-1, -1))
        // Transparent foreground for the lone moving orb. It tracks Door2,
        // while the three fixed receiving orbs are mounted on Navigator.
        val TrinityLayer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            isClickable = false
        }
        Root.addView(TrinityLayer, FrameLayout.LayoutParams(-1, Dp(470), Gravity.TOP))
        // Fixed upper Trinity, moving lower orb. Orb motion stops when docked.
        val Orbs = Orbs(this, Navigator, TrinityLayer)
        Orbs.SetCrystalIlluminated(Crystal.Illuminated)
        Crystal.OnIlluminationChanged = { Lit -> Orbs.SetCrystalIlluminated(Lit) }
        var FieldTransitioning = false
        var AirlockOpen = false
        var AirlockMoving = false
        var AirlockPosition = 0f // 0 crown, 1 menu, -1 fully concealed
        var AirlockAnimator: ValueAnimator? = null
        var RotationRestoreAirlock = false
        var OrbsWereDocked = false

        fun PositionAirlock() {
            if (!LowerReady || Root.height <= 0 || BottomDoor.height <= 0) return
            // Same coordinate space for both Root children. No screen-position
            // guesses, and no frame-by-frame layout changes during the motion.
            val Top = BottomDoor.top - Root.paddingTop - Dp(48)
            val Params = AirlockAssembly.layoutParams as FrameLayout.LayoutParams
            if (Params.topMargin != Top) {
                Params.topMargin = Top
                AirlockAssembly.layoutParams = Params
                val OrbParams = TrinityLayer.layoutParams as FrameLayout.LayoutParams
                OrbParams.topMargin = Top
                TrinityLayer.layoutParams = OrbParams
            }
            // Preserve the original 205dp selector-sheet travel; the single
            // floating orb rides Door2 in place of its old engraved Ω.
            val Up = Dp(205) * AirlockPosition.coerceAtLeast(0f)
            val Down = Dp(100) * (-AirlockPosition).coerceAtLeast(0f)
            AirlockAssembly.translationY = BottomDoor.translationY - Up + Down
            TrinityLayer.translationY = AirlockAssembly.translationY
            // The fixed socket is 67dp from the upper tab's top. The traveling
            // orb is 52dp from the selector tab's top. Check actual modeled
            // positions, not only Door1's closed flag, so ANY docking triggers
            // the same resonance animation (including from the Ω menu).
            val SocketY = Navigator.top - Root.paddingTop + Dp(67)
            val MovingY = Top + AirlockAssembly.translationY + Dp(52)
            val OrbsDockedNow = kotlin.math.abs(SocketY - MovingY) <= Dp(12)
            val DoorOpenFraction = if (LowerTravel > 0f)
                ((BottomDoor.translationY + LowerTravel) / LowerTravel).coerceIn(0f, 1f)
            else 0f
            Orbs.UpdateMating(OrbsDockedNow, DoorOpenFraction)
            if (OrbsDockedNow && !OrbsWereDocked) Orbs.PulseContact()
            OrbsWereDocked = OrbsDockedNow
            // Keep selector lettering below the lone orb, exposing it only while
            // the sheet is raised. The stationary Trinity always stays above.
            Airlock.MenuReveal = ((AirlockPosition - 0.32f) / 0.42f).coerceIn(0f, 1f)
            Airlock.clearFocus()
            AirlockDepth.postInvalidateOnAnimation()
        }

        fun MoveAirlock(Target: Float, Duration: Long, OnEnd: (() -> Unit)? = null) {
            if (AirlockMoving) return
            AirlockAnimator?.cancel()
            val From = AirlockPosition
            if (kotlin.math.abs(From - Target) < 0.005f) {
                AirlockOpen = Target > 0.5f
                OnEnd?.invoke()
                return
            }
            AirlockMoving = true
            val Animator = ValueAnimator.ofFloat(From, Target).apply {
                duration = Duration
                interpolator = DecelerateInterpolator()
                addUpdateListener { Frame ->
                    AirlockPosition = Frame.animatedValue as Float
                    PositionAirlock()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    private var Cancelled = false
                    override fun onAnimationCancel(Animation: android.animation.Animator) {
                        Cancelled = true
                        AirlockMoving = false
                    }
                    override fun onAnimationEnd(Animation: android.animation.Animator) {
                        if (!Cancelled) {
                            AirlockPosition = Target
                            AirlockOpen = Target > 0.5f
                            AirlockMoving = false
                            PositionAirlock()
                            OnEnd?.invoke()
                        }
                    }
                })
                start()
            }
            AirlockAnimator = Animator
        }

        fun AnimateAirlock(Open: Boolean, OnEnd: (() -> Unit)? = null) {
            // Expose = quick advancement. Retract = measured concealment.
            MoveAirlock(if (Open) 1f else 0f,
                if (Open) 400L else 465L, OnEnd)
        }

        var OmegasJoined = false
        fun UpdateDoorLighting() {
            val UpperLocation = IntArray(2)
            val LowerLocation = IntArray(2)
            Navigator.getLocationOnScreen(UpperLocation)
            BottomDoor.getLocationOnScreen(LowerLocation)
            val Density = resources.displayMetrics.density
            val UpperTip = UpperLocation[1] + Navigator.height - 3f * Density
            val LowerSeat = LowerLocation[1] + 44f * Density
            val Gap = LowerSeat - UpperTip
            val Mate = (1f - Gap / (13f * Density)).coerceIn(0f, 1f)
            UpperSurface.Mate = Mate
            LowerSurface.Mate = Mate
            val JoinedNow = Mate >= 0.94f
            // Door1's existing crystal glow is kept; orb resonance is now
            // triggered by the actual socket/orb alignment in PositionAirlock().
            if (JoinedNow && !OmegasJoined) Crystal.PulseContact()
            OmegasJoined = JoinedNow
            DoorShadows.postInvalidateOnAnimation()
            PositionAirlock()
        }

        fun MeasureLowerDoor() {
            if (Root.height <= 0 || QrWindow.height <= 0) return
            val RootPos = IntArray(2)
            val QrPos = IntArray(2)
            val HoodPos = IntArray(2)
            Root.getLocationOnScreen(RootPos)
            QrWindow.getLocationOnScreen(QrPos)
            Navigator.getLocationOnScreen(HoodPos)

            // FrameLayout lays children out INSIDE Root's inset padding.
            // Respect the chamber's 32dp clearance and the translated QR
            // window when calculating Door1's open stop.
            val QrLiftCompensation = Dp(5)
            val DoorOpenLowering = Dp(4)
            val DoorOpenLift = 0 // Restore the original clean chamber height.
            val OpenTop = (QrPos[1] + QrWindow.height + Dp(8) + Dp(32) +
                QrLiftCompensation + DoorOpenLowering - DoorOpenLift -
                RootPos[1] - Root.paddingTop).toFloat()
            val UpperTip = (HoodPos[1] + Dp(110) - Dp(3) -
                RootPos[1] - Root.paddingTop).toFloat()
            val ClosedTop = UpperTip - Dp(44)
            LowerTravel = (OpenTop - ClosedTop).coerceAtLeast(0f)

            // Oversized lower face extends OFF screen below the visible edge.
            // Raising the door cannot expose a strip of Oblivion at the bottom.
            val FullHeight = (Root.height - Root.paddingTop - Root.paddingBottom -
                ClosedTop + Dp(40)).toInt().coerceAtLeast(Dp(200))
            val Params = BottomDoor.layoutParams as FrameLayout.LayoutParams
            Params.topMargin = OpenTop.toInt()
            Params.height = FullHeight
            BottomDoor.layoutParams = Params
            // target a true screen-width square, not an arbitrary dp cap.
            // Leave 5% breathing room on each side (90% of usable width).
            // Measure the FOOTER'S ACTUAL door-relative top, leaving an 8dp
            // gap, so the QR never overlaps it on smaller displays. Both QR
            // and footer are children of Door1 and move with its translation.
            val QrTop = Dp(321) // Exterior QR anchor within Door1.
            val UsableWidth = (Root.width - Root.paddingLeft - Root.paddingRight)
                .coerceAtLeast(1)
            val WidthTarget = (UsableWidth * 0.90f).toInt()
            val FooterTop = FullHeight - Dp(58) - Dp(46)
            val HeightAvailable = (FooterTop - QrTop - Dp(8)).coerceAtLeast(1)
            val Side = minOf(WidthTarget, HeightAvailable)
            val QrParams = QrPattern.layoutParams as FrameLayout.LayoutParams
            QrParams.width = Side
            QrParams.height = Side
            QrParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            QrParams.topMargin = QrTop
            QrPattern.layoutParams = QrParams
            ActiveQrFeedback?.let { Notice ->
                val LabelParams = Notice.layoutParams as FrameLayout.LayoutParams
                LabelParams.topMargin = QrTop + Side + Dp(2)
                Notice.layoutParams = LabelParams
            }
            BottomDoor.translationY = if (LowerClosed) -LowerTravel else 0f
            BottomDoor.visibility = View.VISIBLE
            LowerReady = true
            UpdateDoorLighting()
            Root.post { PositionAirlock() }
        }

        fun AnimateLower(CloseDoor: Boolean, OnEnd: (() -> Unit)? = null) {
            if (!LowerReady) return
            LowerAnimator?.cancel()
            LowerClosed = CloseDoor
            val Target = if (CloseDoor) -LowerTravel else 0f
            if (kotlin.math.abs(BottomDoor.translationY - Target) < 1f) {
                BottomDoor.translationY = Target
                UpdateDoorLighting()
                OnEnd?.invoke()
                return
            }
            val Animation = ValueAnimator.ofFloat(BottomDoor.translationY, Target).apply {
                duration = if (CloseDoor) 465L else 850L
                interpolator = DecelerateInterpolator()
                addUpdateListener { Frame ->
                    BottomDoor.translationY = Frame.animatedValue as Float
                    UpdateDoorLighting()
                }
                if (OnEnd != null) {
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        private var Cancelled = false
                        override fun onAnimationCancel(Animation: android.animation.Animator) {
                            Cancelled = true
                        }
                        override fun onAnimationEnd(Animation: android.animation.Animator) {
                            if (!Cancelled && LowerClosed == CloseDoor) OnEnd()
                        }
                    })
                }
                start()
            }
            LowerAnimator = Animation
        }
        // Public QR is PRIVATE BY DISPLAY until explicitly requested. No
        // ordinary chamber exchange may flash it as Door1 seals and opens.
        fun HideExteriorQr() {
            ExteriorQrExposed = false
            QrPattern.visibility = View.GONE
            UpdateExteriorFooter()
            ActiveQrFeedback?.let { Notice ->
                Notice.animate().cancel()
                Notice.visibility = View.GONE
            }
        }
        // One Seal → Warp → Reveal operation for every chamber change,
        // including Settings editor navigation. The content is exchanged only
        // while Door1 fully conceals the chamber. Crystal pulses on physical mate.
        fun WarpChange(Change: () -> Unit) {
            if (FieldTransitioning || AirlockMoving || !Authenticated || !LowerReady) return
            HideExteriorQr() // Never expose QR during an automatic seal/reveal.
            FieldTransitioning = true
            fun Exchange() {
                if (LowerClosed) {
                    Change()
                    AnimateLower(false) { FieldTransitioning = false }
                } else {
                    AnimateLower(true) {
                        Change()
                        AnimateLower(false) { FieldTransitioning = false }
                    }
                }
            }
            if (AirlockOpen) AnimateAirlock(false) { Exchange() } else Exchange()
        }
        fun WarpTo(Mode: ChamberMode) = WarpChange { SelectChamber(Mode) }
        // Every recognized Mowsie QR opens a non-authoritative demo composer.
        // Only public identity information travels from scanner to UI.
        Chamber.Scanner.OnRecognized = { Identity ->
            if (Authenticated && Chamber.Mode == ChamberMode.Scan && !FieldTransitioning) {
                val Name = findViewById<TextView>(R.id.identityName).text.toString()
                val SelectedField = PulpData.Fields.firstOrNull {
                    it.Name == FieldWheel.CurrentField.Name
                } ?: PulpData.Fields[2]
                Chamber.Transfer.Begin(Identity, Name, SelectedField)
                SelectChamber(ChamberMode.Transfer)
            }
        }
        Chamber.Transfer.OnSuccessTapped = { WarpTo(ChamberMode.Cache) }
        // Top-of-wheel actions are the only functional rows in this pulp pass.
        // Add goes directly to the camera instrument, without sealing Door1.
        Chamber.PeersWheel.OnAction = {
            if (Authenticated && !LowerClosed && !FieldTransitioning && !AirlockMoving) {
                SelectChamber(ChamberMode.Scan)
            }
        }
        FieldWheel.OnAction = { WarpTo(ChamberMode.Genesis) }
        Chamber.LanternsWheel.OnAction = { WarpTo(ChamberMode.Ignite) }
        Settings.Exchange = { Change -> WarpChange(Change) }
        // The Cache activity is the only Cache chamber view. The QR now
        // lives on the exterior door, so no QR/activity toggle remains.
        Airlock.OnMenuChoice = { Name ->
            ChamberMode.entries.firstOrNull { it.name == Name }?.let {
                Orbs.IlluminateUpper()
                WarpTo(it)
            }
        }
        Airlock.setOnClickListener {
            if (Authenticated && !LowerClosed && !FieldTransitioning && !AirlockMoving) {
                AnimateAirlock(!AirlockOpen)
            }
        }
        Airlock.setOnLongClickListener { false }

        // Crystal always locks or reveals the machine, never swaps menus.
        GemTouch.contentDescription = "Crystal. Close or reveal the control panel"
        GemTouch.setOnLongClickListener {
            if (Authenticated && !FieldTransitioning && !AirlockMoving) {
                Crystal.ToggleIllumination()
            }
            true // A long press must never be interpreted as a door tap.
        }
        GemTouch.setOnClickListener {
            if (!Authenticated || FieldTransitioning || AirlockMoving || !LowerReady) return@setOnClickListener
            // In Scan / transfer a crystal tap exits to Cache instead of
            // needlessly exposing the personal QR over the demo workflow.
            if (Chamber.Mode == ChamberMode.Scan || Chamber.Mode == ChamberMode.Transfer) {
                WarpTo(ChamberMode.Cache)
                return@setOnClickListener
            }
            fun TogglePrivacyDoor() {
                if (LowerClosed) {
                    // The exterior QR stays attached to the door as it moves
                    // DOWN and is hidden only once Door1 is fully open.
                    // No QR can appear during automatic unlock or on Threshold.
                    AnimateLower(false) { HideExteriorQr() }
                } else {
                    // Prepare the real PUBLIC QR while Door1 is already below
                    // the viewport. It rides UP with the slab, emerging from
                    // the bottom edge rather than popping in at the end.
                    // Other chamber exchanges still explicitly hide it.
                    ExteriorQrExposed = true
                    RefreshPublicQr()
                    UpdateExteriorFooter()
                    AnimateLower(true)
                }
            }
            if (AirlockOpen) AnimateAirlock(false) { TogglePrivacyDoor() }
            else TogglePrivacyDoor()
        }
        // The fixed upper Trinity toggles Settings / Cache, using the sealed Warp.
        Orbs.OnUpperTap = {
            WarpTo(when (Chamber.Mode) {
                ChamberMode.Scan, ChamberMode.Transfer, ChamberMode.Settings -> ChamberMode.Cache
                else -> ChamberMode.Settings
            })
        }
        Orbs.OnTrinityTap = {
            if (Authenticated && !LowerClosed && !FieldTransitioning && !AirlockMoving) {
                if (Chamber.Mode != ChamberMode.Cache) {
                    // Single floating orb is the direct Home control from
                    // any wheel or settings chamber. Seal, exchange, reveal.
                    WarpTo(ChamberMode.Cache)
                } else AnimateAirlock(!AirlockOpen)
            }
        }
        Selected.setOnClickListener {
            if (!Settings.Back()) WarpTo(ChamberMode.Cache)
        }

        QrPattern.contentDescription = "Public identity QR on lower door. Hold to copy; hold and drag to adjust color"
        // A Field inscription can still open the Fields chamber directly.
        FieldName.setOnClickListener { WarpTo(ChamberMode.Fields) }
        FieldWheel.OnCenterTapped = { /* FieldWheel handles its own selection. */ }
        Root.post { MeasureLowerDoor() }
        ReflowDoorsAfterRotation = { Root.post { MeasureLowerDoor() } }
        SealDoorsForRotation = {
            HideExteriorQr() // Rotation is not a deliberate QR reveal.
            // Seal sensitive edit fields when the orientation shutter closes.
            ClearSecretPage?.invoke()
            ClearSecretPage = null
            RotationRestoreAirlock = AirlockOpen
            LowerAnimator?.cancel()
            LowerClosed = true
            BottomDoor.translationY = -LowerTravel
            AirlockAnimator?.cancel()
            AirlockMoving = false
            AirlockOpen = false
            AirlockPosition = 0f
            PositionAirlock()
            UpdateDoorLighting()
            // Preserve the menu path, field wheel, and identity. This is not a lock.
        }
        // The scene begins with the lock sealed. Only after a successful
        // Secret unlock does the lower slab descend to reveal the QR chamber.
        OpenDoorsOnUnlock = {
            HideExteriorQr() // No public QR at login or during automatic opening.
            // Present the sealed silhouette for half a second after unlock,
            // then let the heavy lower blast door reveal the QR chamber.
            // Recheck state in case the app relocks during the pause.
            BottomDoor.postDelayed({
                if (Authenticated && Onscreen && LowerReady && LowerClosed &&
                    resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                    AnimateLower(false) {
                        if (RotationRestoreAirlock) {
                            RotationRestoreAirlock = false
                            AnimateAirlock(true)
                        }
                    }
                }
            }, 500L)
        }

        ResetWorkspaceOnLock = {
            HideExteriorQr() // Public QR stays hidden on every future login.
            ClearSecretPage?.invoke()
            ClearSecretPage = null
            LowerAnimator?.cancel()
            LowerClosed = true
            BottomDoor.translationY = -LowerTravel
            Crystal.Illuminate(false) // Return to the dormant Threshold artwork on relock.
            FieldTransitioning = false
            AirlockAnimator?.cancel()
            AirlockMoving = false
            AirlockOpen = false
            AirlockPosition = 0f
            PositionAirlock()
            HideRecovery()
            Settings.Clear()
            SettingsActive = false
            Chamber.Show(ChamberMode.Cache)
            UpdateDoorLighting()
            Root.requestFocus()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    AirlockOpen -> AnimateAirlock(false)
                    Chamber.Mode == ChamberMode.Settings && !Settings.IsRoot() -> Settings.Back()
                    Chamber.Mode != ChamberMode.Cache -> WarpTo(ChamberMode.Cache)
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }



/**
 * the landscape pose is a static, sealed copy of portrait's actual
 * physical artwork. Neither the portrait door nor its animations are moved.
 * Only the crystal bob continues; rotating upright uses the existing reveal.
 */
internal fun ControlPanel.EnsureLandscapeSealOverlay() {
        if (LandscapeSealOverlay != null) return
        val Overlay = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(getColor(R.color.blast_door))
            isClickable = true
            isFocusable = true
            contentDescription = "Mowsie sealed in landscape"
            clipChildren = false
            clipToPadding = false
        }
        val Surface = LandscapeSealedDoorsView(this)
        Overlay.addView(Surface, FrameLayout.LayoutParams(-1, -1))

        // Reuse portrait's exact drawable, three embossed layers, anchor size,
        // GemFloat dimensions, 88x210dp crystal and gentle hover amplitude.
        // Portrait uses 90% of the 318dp Omega; landscape starts there too.
        val OmegaAnchor = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            scaleX = 0.90f
            scaleY = 0.90f
        }
        Overlay.addView(OmegaAnchor,
            FrameLayout.LayoutParams(Dp(318), Dp(355), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        fun OmegaLayer(Tint: Int, Opacity: Float, X: Int, Y: Int) {
            val Layer = ImageView(this).apply {
                setImageResource(R.drawable.mowsieomega)
                imageTintList = ColorStateList.valueOf(Tint)
                alpha = Opacity
                translationX = Dp(X).toFloat()
                translationY = Dp(Y).toFloat()
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            OmegaAnchor.addView(Layer,
                FrameLayout.LayoutParams(Dp(318), Dp(318), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }
        OmegaLayer(Color.parseColor("#080E18"), .90f, 3, 4)
        OmegaLayer(Color.parseColor("#324458"), .55f, -2, -3)
        OmegaLayer(getColor(R.color.blast_door), 1f, 0, 0)

        val GemFloat = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        OmegaAnchor.addView(GemFloat,
            FrameLayout.LayoutParams(Dp(244), Dp(240), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { topMargin = Dp(34) })
        val Glow = DreamGlowView(this).apply { Strength = .36f }
        GemFloat.addView(Glow, FrameLayout.LayoutParams(-1, -1))
        val DarkGem = ImageView(this).apply {
            setImageResource(R.drawable.darkcrystal)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val LightGem = ImageView(this).apply {
            setImageResource(R.drawable.lightcrystal)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
        }
        GemFloat.addView(DarkGem, FrameLayout.LayoutParams(Dp(88), Dp(210), Gravity.CENTER))
        GemFloat.addView(LightGem, FrameLayout.LayoutParams(Dp(88), Dp(210), Gravity.CENTER))
        AttachCrystalBob(GemFloat, Dp(5.5f).toFloat())

        // Original rigid upward Trinity, 160x110dp, and the single docked orb
        // at center Y=67dp. Both use the same art and sizing as portrait.
        val Trinity = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // Full-size Trinity near the upper edge of the landscape seal.
        Overlay.addView(Trinity,
            FrameLayout.LayoutParams(Dp(160), Dp(110), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { topMargin = Dp(-20) })
        val DockedOrb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(Dp(11), Dp(11), Dp(11), Dp(11))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        Overlay.addView(DockedOrb,
            FrameLayout.LayoutParams(Dp(54), Dp(54), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { topMargin = Dp(20) })

        // The landscape Ω and crystal retain their portrait-derived scale.
        // Trinity and docked orb share a reference line above the crease;
        // preserve the existing height-based Trinity visibility rule.
        fun Fit(HeightPx: Int) {
            if (HeightPx <= 0) return
            val H = HeightPx / resources.displayMetrics.density
            val FullScale = minOf(.90f, (H - 104f) / 355f)
            val OriginalFull = FullScale >= .81f
            val Scale = if (OriginalFull) FullScale else
                minOf(.90f, (H - 12f) / 355f).coerceIn(.45f, .90f)
            val OriginalVisibleTop = if (OriginalFull) maxOf(96f, (H - 355f * Scale) / 2f)
                else maxOf(6f, (H - 355f * Scale) / 2f)
            val VisibleTop = OriginalVisibleTop + 60f
            // A scaled Android View retains its unscaled layout rectangle.
            // Offset by half the scale difference to position its VISIBLE edge.
            val AnchorTop = VisibleTop - 355f * (1f - Scale) / 2f
            val Params = OmegaAnchor.layoutParams as FrameLayout.LayoutParams
            val TopPx = Dp(AnchorTop).toInt()
            if (Params.topMargin != TopPx) {
                Params.topMargin = TopPx
                OmegaAnchor.layoutParams = Params
            }
            OmegaAnchor.scaleX = Scale
            OmegaAnchor.scaleY = Scale
            // Trinity artwork sits at y=-20..90 after the 10dp lift.
            // Short landscape screens retain Omega-only mode.
            val ShowCluster = H >= 315f
            Trinity.visibility = if (ShowCluster) View.VISIBLE else View.GONE
            DockedOrb.visibility = if (ShowCluster) View.VISIBLE else View.GONE
            Surface.ShowTrinity = ShowCluster
            Surface.SeamDp = if (ShowCluster) 50f else minOf(36f, OriginalVisibleTop) - 32f
        }
        Overlay.addOnLayoutChangeListener { _, _, _, _, Bottom, _, _, _, _ ->
            Fit(Bottom - Overlay.top)
        }
        // Reuse the portrait's dormant/active artwork when orientation changes.
        LandscapeSealUpdateArtwork = {
            val Lit = ActiveCrystal?.Illuminated == true
            DarkGem.alpha = if (Lit) 0f else 1f
            LightGem.alpha = if (Lit) 1f else 0f
            Glow.Strength = if (Lit) .66f else .36f
            Trinity.setImageResource(if (Lit)
                R.drawable.orbactivetrinityfixed else R.drawable.orbdormanttrinityfixed)
            DockedOrb.setImageResource(if (Lit)
                R.drawable.orbactiveupper else R.drawable.orbdormantupper)
        }
        LandscapeSealUpdateArtwork?.invoke()
        Root.addView(Overlay, FrameLayout.LayoutParams(-1, -1))
        LandscapeSealOverlay = Overlay
    }

internal fun ControlPanel.UpdateLandscapeSeal(Orientation: Int) {
        val Overlay = LandscapeSealOverlay ?: return
        if (Orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LandscapeSealUpdateArtwork?.invoke()
            if (!LandscapeSealVisible) {
                // Hide the workspace without performing a security-lock reset.
                if (Authenticated) SealDoorsForRotation?.invoke()
                LandscapeSealVisible = true
            }
            // Keep the sealed landscape machine above every workspace layer.
            Overlay.bringToFront()
            Overlay.visibility = View.VISIBLE
        } else if (LandscapeSealVisible) {
            Overlay.visibility = View.GONE
            LandscapeSealVisible = false
            if (Authenticated) {
                // Recalculate portrait stops before starting the existing
                // half-second pause and 850ms opening sequence.
                ReflowDoorsAfterRotation?.invoke()
                OpenDoorsOnUnlock?.invoke()
            }
        }
    }
