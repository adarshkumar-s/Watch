# OMNITRIX — Protocol Research Register (Noise ColorFit Caliber 2881 / R204.5.8)

Every protocol statement in the project carries exactly one evidence level:

- **CONFIRMED BY ACTUAL WATCH** — captured on-device by this diagnostic app (log/export proves it).
- **PUBLICLY DOCUMENTED** — vendor documentation or Bluetooth SIG specifications.
- **COMMUNITY REPORT** — public reverse-engineering from maintainers/users of other devices.
- **HYPOTHESIS** — a guess; never presented as fact, never transmitted to the watch.

---

## CONFIRMED BY ACTUAL WATCH

_Nothing yet. This section fills only from captured on-device diagnostics._

## PUBLICLY DOCUMENTED

- Standard GATT semantics: services/characteristics/descriptors, properties
  (READ/WRITE/WRITE_NO_RESPONSE/NOTIFY/INDICATE), CCCD `0x2902` for subscriptions.
  (Bluetooth Core Spec — GATT.)
- LE Advertisement Data structure format `[len][type][data]` and AD type assignments used
  by `ble/AdvertisementParser` (Bluetooth Core Spec — CSS / assigned numbers).
- SIG-assigned service/characteristic names used for labeling in `ble/GattExplorer`
  (e.g. 0x180A Device Information, 0x2A26 Firmware Revision, 0x180F/0x2A19 Battery).
- Android permission model: `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` on API 31+; legacy
  `BLUETOOTH`/`BLUETOOTH_ADMIN` install-time + runtime location for scan results on
  API 23–30. (Android developer docs.)

## COMMUNITY REPORT (other devices — may or may not apply to 2881)

- **Noise/Da Fit history**: Noise support docs direct ColorFit-series users to the
  "Da Fit" companion app; newer units to "NoiseFit". ⇒ Noise watches historically run
  Da Fit-class firmware. (gonoise.com product/support pages.)
- **Moyoung protocol family** (GitHub/Codeberg — Gadgetbridge, krzys_h et al., AGPL):
  Da Fit watches speak MOYOUNG/MOYOUNG-V2:
  - service `0000feea-0000-1000-8000-00805f9b34fb`; write `0000fee2-…` (DATA_OUT),
    notify `0000fee3-…` (DATA_IN), steps `0000fee1-…`, "special" `fee5/fee6`.
  - framing: `FE EA | sizeHi+32 sizeLo | cmd | payload…` (V2) or `FE EA 10 len cmd …`
    (MTU-20 legacy); `size` counts from magic through payload (empty payload ⇒ 5).
  - family id: reading Manufacturer Name (`0x2A29`) returns `MOYOUNG`/`MOYOUNG-V2`.
  - standard services used for real data in that family: Device Information, Battery,
    sometimes Heart Rate/HID.
- Noise marketing/support pages state the NoiseFit app shows a QR during binding and that
  notification features require the app running — no packet-level documentation exists.

## HYPOTHESIS (explicitly NOT evidence)

- That the Caliber 2881 exposes the 0xFEEA Moyoung service under R204.5.8.
- That the Caliber QR encodes a MAC address (alternatives: URL, JSON, serial, token,
  encrypted blob) — to be resolved by captured scans + the app's QR↔BLE correlation.
- That the pre-audit code's UUIDs (`16186f00/01/02-…-00807f9b34fb`) ever existed on the
  watch — archived as UNVERIFIED in `protocol/UnverifiedLegacyCatalog.kt` and compared
  against reality at runtime; the app uses discovered services regardless.
- All old guessed bytes (PING/ACK_OK/ACK_END, init burst, opcode 0xA1): archived as
  never-validated; removed from all code paths.

## Driver policy (enforced in code + tests)

- `NoiseColorFitCaliber2881Driver` identifies (advertisement/GATT heuristics with evidence
  labels) and interprets reads/notifications for display only. `buildCommand()` returns
  `Unsupported` for ALL capabilities — guarded by `DriverTest`.
- `BleConnection` has no characteristic-value write API. The only possible mutation is a
  user-initiated CCCD subscribe/unsubscribe, and it is logged as PHONE→WATCH each time.
- No developer write tool is exposed anywhere in the UI (stronger than the audit
  requirement of a hidden DEVELOPER MODE; if one is ever added it must be individually
  declared here first).
