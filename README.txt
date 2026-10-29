Mowsie V21 - door polish and field wheel
=======================================

Open the MowsieV21 folder in Android Studio.

Based directly on working V20. All original local identity, Secret, Genesis,
Recovery, and Settings code is retained.

V21 visual changes
- The authentic QR opens larger, with 8dp horizontal margins and a taller
  320dp QR chamber. The lower door's open stop follows the enlarged QR.
- Both raised doors keep the same exact mirrored tooth/recess geometry.
- While the doors approach contact, their metallic edge highlights fade out.
  A dark crack replaces the bright doubled line in the closed position.
- Contour-based shadows still fall onto the darker full-screen backdrop.

V21 field wheel experiment
- Tap the field name on the lower door (initially Bob's Donuts).
- The lower door rises, changes the revealed content to a field wheel, then
  descends again. This is one continuous close/reopen sequence.
- Drag the wheel vertically or tap an off-center item. The centered name is
  the current selection. Only names appear in the wheel; the mock Salt
  balance updates on the lower door as the field changes.
- Fields are Tia's Tacos, Sally's Coffee, Bob's Donuts, In-N-Out Burger,
  City Wok, and Joe's FroYo.
- Tap the selected field name on the lower door again, or tap the centered
  field in the wheel, to raise the door and dismiss the wheel.
- Tap the Stash name (e.g. Metronome) to open/close the lower door as before.
- Tap the crystal for the existing settings hood. Tap the QR to switch to
  the existing viewfinder placeholder, and tap again to return to the QR.

The wheel fields and balances are MOCK VALUES for this UI prototype.
No real synchronization, camera scanning, or Salt transfer is implemented.
The constellation idea and green Synchronize action are deferred.

The ZIP contains the complete Android Studio project and source. This
runtime has no Android SDK, so a complete Android build/device test could
not be performed here.
