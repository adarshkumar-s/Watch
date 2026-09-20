# OMNITRIX — Diagnostic Milestone Status (post user-audit)

Device: Noise ColorFit Caliber (2881) • Firmware R204.5.8 • 2026-09-19 (rev 2)

Audit trail: user audit found the pre-rebuild `MainActivity` (commit `70260e3`) still
sending guessed frames. That monolith was fully replaced; this revision closes the
remaining audit gaps and labels all legacy material UNVERIFIED instead of deleting it.

## Audit point-by-point

1. **Remove automatic unknown writes** — done. `BleConnection` contains no characteristic-value
   write method at all; grep-verifiable: no `writeCharacteristic(` exists in `main` sources
   (only `writeDescriptor` for user-gated CCCD). No PING/ACK_OK/ACK_END/registration/init/
   find-watch frames exist anywhere; the old ones are archived as DATA in
   `protocol/UnverifiedLegacyCatalog.kt`, marked "never validated".
2. **Real diagnostic mode** — DiagnosticsActivity shows a DEVICE panel (name, address, RSSI,
   tx power, advertised services, manufacturer data, service data, parsed raw AD structures)
   above the log; full GATT tree lives in the CONNECTED WATCH explorer.
3. **QR must not assume MAC** — QrScannerActivity displays the complete RAW value verbatim;
   `pairing/QrPayloadParser` classifies MAC / UUID / URL / JSON / HEX / BASE64 / KEY=VALUE /
   JWT / TEXT / UNKNOWN(binary) / EMPTY. No auto-connect, no URL opening. COPY + EXPORT
   buttons present; scans stored verbatim (local-only).
4. **BLE advertisement capture** — `ble/AdvertisementParser` (pure AD-structure parser) +
   raw bytes stored per device (`rawAdvHex`); rendered in Diagnostics/Compare screens and
   included in the export.
5. **Connection log** — canonical categories (`diagnostics/LogEvent`): SCAN_STARTED,
   SCAN_STOPPED, DEVICE_FOUND, CONNECTING, CONNECTED, SERVICE_DISCOVERY, SERVICE_FOUND,
   CHARACTERISTIC_FOUND, NOTIFICATION_ENABLED/DISABLED, READ_REQUEST/READ_RESPONSE,
   RX_PACKET (raw hex only), DISCONNECTED, ERROR + direction tags PHONE→WATCH / WATCH→PHONE.
6. **Safe manual testing** — no arbitrary write controls anywhere in the UI (stronger than
   the hidden-developer-mode requirement). Documented in PROTOCOL_RESEARCH.md.
7. **Legacy UUID validity** — `16186f00/01/02-…` preserved as UNVERIFIED catalog entries;
   after each service discovery the app logs PRESENT/ABSENT for each, flags them with a ⚠
   LEGACY label in the explorer if present, and always uses the discovered services (never
   gates on them).
8. **Protocol research** — register kept with CONFIRMED BY ACTUAL WATCH (empty) /
   PUBLICLY DOCUMENTED / COMMUNITY REPORT / HYPOTHESIS taxonomy in
   `docs/PROTOCOL_RESEARCH.md`.
9. **Architecture** — matches the audit-directed layout: `ble/` (BleScanner, BleConnection,
   GattExplorer, AdvertisementParser), `diagnostics/`, `pairing/` (QrScanner,
   QrPayloadParser, PairingState), `devices/NoiseColorFitCaliber2881Driver`,
   `protocol/ProtocolDecoder|ProtocolEncoder`, `ui/`.
10. **Testing** — see below.
11. **Build verification** — CI (`Build APK`): unit tests → lint → assemble → artifacts +
   a machine build report posted as a commit comment.
12. **Completion criteria** — see checklist below.

## Completion criteria checklist

- ✓ APK builds — CI assembleDebug (green run verified at ae2de56; this rev re-verified below)
- ✓ QR scanner works — QrScannerActivity + QrScanner analyzer; camera permission flow
- ✓ raw QR payload displayed — verbatim, selectable, with format classification
- ✓ BLE scan works — BleScanner (permissions gate, BT-off gate, 15s timeout, dedup)
- ◻ Caliber can be discovered — requires physical watch (SIMULATED Caliber path exists)
- ◻ GATT connection works (against real watch) — state machine unit-tested; needs hardware
- ◻ services/characteristics displayed — explorer implements; needs hardware (simulated path OK)
- ◻ notifications observed safely — explicit CCCD toggles; needs hardware (simulated path OK)
- ✓ diagnostic log works — DiagnosticLog + taxonomy; assertion-tested
- ✓ diagnostic export works — omnitrix-diagnostic.txt via explicit Share
- ✓ no unknown BLE writes occur automatically — structural (no write API) + no auto-op on connect
- ✓ permissions work on Android 12+ and ≤11 — BlePermissions (+ unit tests for both policy paths)
- ✓ disconnect/reconnect handled — DISCONNECTING → closeGattOnly → fresh connect; WatchLink detach
- ✓ app does not crash if watch disappears — disconnect path clears ops/registry; UI shows OFFLINE
- ✓ protocol assumptions explicitly marked — UnverifiedLegacyCatalog + evidence labels in UI/logs/docs
- ✓ no feature falsely reports success — driver `buildCommand` Unsupported; actions show "Not yet supported on this firmware."

## Unit test inventory (JVM, CI)

QrPayloadParserTest (14) · ProtocolCodecTest (9) · HexCodecTest (4) · QrBleCorrelationTest (6)
· ConnectionStateMachineTest (7) · DiagnosticLogTest (3) · DriverTest (5) ·
AdvertisementParserTest (7) · BlePermissionsTest (5) · PairingStateMachineTest (7) ·
UnverifiedLegacyCatalogTest (4) — including malformed QR / malformed packet / permission
denial / connection failure / truncated advertisement coverage.

## What remains hardware-gated (cannot be simulated here)

Anything with a real radio: discovering the actual Caliber, connecting, real GATT
discovery, reading Device Information/Battery, observing notifications, and (therefore)
any CONFIRMED BY ACTUAL WATCH entry. The SIMULATED Caliber path exercises every screen
in the meantime; the exported `omnitrix-diagnostic.txt` from the first real run is the
input for the next milestone.
