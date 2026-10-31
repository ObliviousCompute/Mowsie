# Mowsie

Mowsie is a native Android prototype of an identity and Field-based value interface. Its custom interface uses a crystal, Ω shutter, floating orbs, and a scrollable Cache activity chamber.

**Current status:** Interactive demonstration. Field balances, Peers, Lanterns, and activity records are fictional presentation data. The Scan → Confirm → Message → Send → Sent flow is simulated. **No Salt is transferred, no transaction is signed or broadcast, and a Sent result is not proof of delivery or admission.**

## Features

- Local identity generation and 12-word BIP39 recovery, with an Ed25519 public identity derived using a provisional Mowsie path.
- A local Secret gate for casual access control, with Android Keystore-backed encryption of stored identity material. It is not a hardened password vault.
- Public-identity QR display, customizable colors and image copying.
- Live QR recognition through the camera, plus clipboard QR-image recognition.
- Interactive transfer walkthrough with amount slider, optional message, and persistent **Sent ✓** review.
- Scrollable example Fields, Peers, Lanterns, and transaction histories.
- Custom shutter and crystal interface, including the sealed landscape view.

## Open in Android Studio

1. Open the project folder (the directory containing `settings.gradle.kts`).
2. Allow Android Studio to sync Gradle and install the required Android SDK components.
3. Build and install the `app` module on an Android device (Android 7.0 / API 24 or newer).
4. Grant camera permission when using **Scan**. To try the walkthrough, show your own Mowsie QR on another screen and scan it with your phone.

The Gradle wrapper is included. A full Android build is not verified by this archive's cleanup process.

## Source map

| File | Responsibility |
| --- | --- |
| `ControlPanel.kt` | Activity entry point and component coordination |
| `Lock.kt`, `DoorSurfaces.kt` | Physical shutter, tabs, landscape seal and surfaces |
| `Crystal.kt`, `Orbs.kt` | Crystal and orb visuals/animations |
| `Chamber.kt`, `Cache.kt`, `CacheActivity.kt` | Chamber navigation and activity display |
| `Scan.kt`, `ScanProtocol.kt`, `Transfer.kt` | QR recognition and simulated transfer UI |
| `GenesisStore.kt`, `MowsieIdentity.kt`, `GenesisWords.kt` | Local identity storage, derivation and recovery helpers |
| `Settings.kt` | Appearance, Secret and Recovery UI; Stasis is not yet implemented |
| `PulpData.kt` | **Synthetic UI data only**; not authorized balances or network state |

Editable vector source artwork is in `Artwork/`. Android raster resources are under `app/src/main/res/`.

## Development boundaries

The current identity format and derivation path are provisional, not a production currency protocol. A scanned Mowsie-format public key identifies a payload's structure, not cryptographic proof that its claimed owner controls the key. Real evidence admission, transfers, synchronization, and the Soulcrum engine are not included in this UI prototype.

No license file is included in this package. Choose and add a license explicitly before publishing if you want others to have open-source reuse rights.
