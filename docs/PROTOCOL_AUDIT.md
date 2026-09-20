# OMNITRIX — Repository & Protocol Audit (Phase 1)

Date: 2026-09-19
Target device: Noise ColorFit Caliber, device identifier **2881**, firmware **R204.5.8**
Audited commit: `70260e3` (existing experimental implementation)

---

## 1. Confirmed Android behavior (verified against platform API contracts)

| Behavior | Status |
|---|---|
| Manifest splits runtime permissions correctly per API level (BLUETOOTH/ADMIN ≤ 30, BLUETOOTH_SCAN/CONNECT ≥ 31) | ✅ pattern correct, but **incomplete** (see §7) |
| `BLUETOOTH_SCAN` declared with `neverForLocation` — valid on Android 12+ | ✅ |
| CameraX + ML Kit barcode scanning is a sound QR pipeline | ✅ architecture OK |
| Old code requested permissions once at startup without any per-flow handling and crashed/`SecurityException` if denied (`startScan`, `getRemoteDevice`, `device.name`) | confirmed broken |
| `bluetoothLeScanner` can be `null` while Bluetooth is off — old code crashed in `onCreate` with BT off | confirmed broken |
| On Android 6–11, BLE scan results require a runtime **location** permission — old code never declared or requested it, so scanning on ≤ Android 11 silently found nothing | confirmed broken |
| Old GATT writes were fire-and-forget on the main thread with `postDelayed` timing, never checking `writeCharacteristic()` return values | confirmed broken |

## 2. Confirmed BLE behavior

- BLE scanning (`BluetoothLeScanner.startScan`) works; the old code filtered by name match only ("Caliber"/"Noise"/"2881") and discarded all other devices — anti-diagnostic.
- `connectGatt(..., TRANSPORT_LE)` is correct usage.
- **Every watch-specific UUID / packet in the old code is unverified:**
  - `16186f00/16186f01/16186f02-...` service/characteristic UUIDs — **fabricated**; they don't even use the standard Bluetooth base UUID (`...-00805f9b34fb`; old code used the non-standard `00807f9b34fb`).
  - `PING = 00 00 00 00 01 00`, `ACK_OK`, `ACK_END`, `frame(1,0,8)+varint(opcode)`, protobuf varint helpers, the 8-frame "registration" sequence with `pbBytes(3, pbBytes(3, inner))`, find-watch opcode `0xA1` — **all guessed, never validated against a ColorFit Caliber**.
  - Old code wrote ACK bytes **to the notify characteristic** — invalid GATT usage (notify characteristics usually aren't writable; likely intended when porting from another watch project).
  - Old code auto-ran `initializeSession()` (an 8-frame write burst @120 ms) immediately after enabling notifications — a spammy block of **unknown opcodes sent to the watch without user consent**. This was removed in full.

## 3. Confirmed QR behavior

- ML Kit `BarcodeScanning` + CameraX `ImageAnalysis` raw-value extraction is correct and works.
- Old flow **assumed the QR embeds a MAC address** (`extractMac` regex), then immediately connected and launched the guessed session init — auto-executing unverified behavior. Removed.
- The actual QR content of a ColorFit Caliber 2881 is **unknown** until captured by the diagnostic mode. It may be a MAC, a URL, JSON, a serial, a token, or a blob.

## 4. Unknown watch-specific behavior (must be discovered, never guessed)

- GATT service/characteristic layout of the Caliber 2881 under firmware R204.5.8.
- Whether the Caliber speaks a Moyoung-family protocol (0xFEEA frame magic) — this is a *hypothesis* from public research on Noise/Da Fit watches (see `PROTOCOL_RESEARCH.md`), **not** established for 2881/R204.5.8.
- Which characteristic is notify vs write vs special channels.
- Session/registration/binding handshake used by the official NoiseFit app.
- Time-sync, notification push, find-watch, battery, sensor opcodes.
- Whether the watch accepts raw BLE connections without NoiseFit app binding.
- The QR payload format and its role in pairing.

## 5. Guessed / reverse-engineered behavior in old code (REMOVED & QUARANTINED)

All of it. The only protocol material retained anywhere in the new codebase lives in
`protocol/PacketEncoder.kt` / `PacketDecoder.kt` as a **pure, test-only Moyoung-family codec hypothesis**, clearly labeled UNKNOWN, and **never wired to any BLE write path**. `BleConnection` in the new app has no characteristic-value write API in diagnostic mode at all — the only GATT mutation it can perform is a CCCD subscribe/unsubscribe, and only on explicit user tap.

## 6. Potentially dangerous behavior (old → now)

| Old behavior | Risk | New behavior |
|---|---|---|
| Auto 8-frame session init on connect | unknown persistent effects on watch | gone — diagnostic mode is read/discovery only |
| `findWatch()` sending opcode `0xA1` | guessed opcode | UI shows *"Not yet supported on this firmware."* |
| Writing ACKs to notify characteristic | undefined GATT usage | removed; only CCCD descriptor writes, user-initiated |
| Fire-and-forget writes with sleeps | command spam, queue overflows | strict op queue; no value writes at all |
| QR → auto-connect + session init | auto-executing scanned data | QR is captured, parsed, displayed, stored; no side effects |

## 7b. Addendum — second user audit (2026-09-19 rev 2)

The user re-audited and flagged the pre-rebuild `MainActivity` (`70260e3`) — that file
was already fully replaced before this audit round; this revision closes the remaining
gaps so the checklist is unambiguous:

- Legacy UUIDs/packets are **preserved as UNVERIFIED data** (`protocol/UnverifiedLegacyCatalog.kt`)
  instead of silently deleted; each discovery logs PRESENT/ABSENT for them.
- Log event taxonomy canonicalized (`diagnostics/LogEvent`): SCAN_STARTED, DEVICE_FOUND,
  CONNECTING, CONNECTED, SERVICE_DISCOVERY, SERVICE_FOUND, CHARACTERISTIC_FOUND,
  NOTIFICATION_ENABLED, DISCONNECTED, ERROR, RX_PACKET (raw only).
- `ble/AdvertisementParser` captures raw advertisement bytes (parse + render; malformed tolerated).
- Diagnostics screen gained a DEVICE panel (name/address/RSSI/advertisement facts).
- Package/file layout aligned to the requested architecture (`diagnostics/`,
  `pairing/QrScanner`, `pairing/QrPayloadParser`, `pairing/PairingState`,
  `protocol/ProtocolDecoder`, `protocol/ProtocolEncoder`).
- New unit tests: AdvertisementParserTest, BlePermissionsTest (denial paths),
  PairingStateMachineTest, UnverifiedLegacyCatalogTest, QR UNKNOWN(binary) classification.

## 7. Compile / runtime problems fixed

1. Missing `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` for Android ≤ 11 → added (`maxSdkVersion=30`).
2. Missing runtime permission checks before `startScan`/`stopScan`/`connectGatt`/`device.name` → centralized in `ble/BlePermissions.kt`.
3. NPE when Bluetooth is off (`adapter.bluetoothLeScanner` null) → handled with a "enable Bluetooth" flow.
4. No launcher icon (missing `android:icon`) → added adaptive vector icon.
5. Permissions requested on cold start without rationale → moved to per-flow requests.
6. No Gradle wrapper (task requires `./gradlew assembleDebug`) → wrapper added (Gradle 8.10.2).
7. Monolith `MainActivity` containing protocol logic → architecture split into `ble/`, `protocol/`, `pairing/`, `devices/`, `diag/`, `ui/`.
8. CI only built `assembleDebug`, never ran tests or lint → workflow updated (unit tests + lint + APK artifact).
9. Deprecated `BluetoothAdapter.getDefaultAdapter()` usage; unfiltered scan showing only one brand → rebuilt.
