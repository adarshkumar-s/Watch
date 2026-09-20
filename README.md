# OMNITRIX — Noise ColorFit Caliber 2881

A community Android **diagnostic** companion app for the **Noise ColorFit Caliber
(device 2881, firmware R204.5.8)**.

> ⚠ The BLE protocol of this watch is **not publicly documented**. This app is
> deliberately **read/discovery-only**: it never sends guessed packets, opcodes,
> session-init frames or ACKs to the watch. See `docs/PROTOCOL_AUDIT.md` and
> `docs/PROTOCOL_RESEARCH.md` for exactly what is CONFIRMED / LIKELY / UNKNOWN.

## Milestone 1 — safe diagnostics (current)

- **SCAN WATCH QR** — captures the QR shown by the watch, shows the *complete raw payload*,
  classifies its format (URL / JSON / MAC / UUID / hex / base64 / JWT…), COPY / SAVE / EXPORT.
  Nothing in the payload is ever auto-executed.
- **SCAN BLUETOOTH** — lists *all* nearby BLE devices (not just "Caliber"), with RSSI, name,
  address, advertisement data; handles Android 12+ and ≤11 permissions, Bluetooth-off,
  timeouts and duplicates.
- **CONNECTED WATCH** — GATT explorer: services, characteristics, properties, descriptors,
  user-initiated READs and optional NOTIFY subscriptions (CCCD only). **No characteristic
  writes exist in diagnostic mode.**
- **DIAGNOSTICS** — timestamped, direction-tagged log (PHONE → WATCH / WATCH → PHONE),
  COPY / EXPORT / CLEAR, plus the **QR ↔ BLE correlation** screen that computes from observed
  data whether the QR is part of the BLE pairing process.
- **omnitrix-diagnostic.txt** export (explicit user Share action only; nothing is uploaded).

## Architecture

```
app/src/main/java/com/adarshkumar/omnitrix/
├── ble/          BleScanner, BleConnection, GattExplorer, AdvertisementParser,
│                 GattModel, BlePermissions, DeviceRegistry
├── diagnostics/  DiagnosticLog, DiagnosticExporter, LogEvent (event taxonomy)
├── pairing/      QrScanner, QrPayloadParser, QrPayload, QrStore,
│                 PairingManager, PairingState
├── devices/      DeviceDriver, NoiseColorFitCaliber2881Driver, FakeCaliberDevice
├── protocol/     ProtocolDecoder, ProtocolEncoder (hypothesis only — never transmitted),
│                 ProtocolFrame, UnverifiedLegacyCatalog, ConnectionStateMachine, HexCodec
├── analysis/     QrBleCorrelation
└── ui/           MainActivity (Dashboard), QrScannerActivity, BleScanActivity,
                  GattExplorerActivity, DiagnosticsActivity, CompareActivity, WatchLink
```

New ColorFit models are added as new drivers under `devices/` without touching BLE or UI code.

## Verification

- Unit tests: QR parser (incl. malformed payloads), packet codec (incl. malformed frames),
  hex codec, connection state machine, QR↔BLE correlation, diagnostic log, driver policy
  (every capability must report "Not yet supported on this firmware").
- GitHub Actions runs tests + lint + `assembleDebug` and publishes **`OMNITRIX-debug.apk`**
  as a build artifact. Releases will be published once the diagnostic milestone proves stable.

## Safety rules implemented

No firmware flashing/updating, no unknown persistent writes, no unknown opcodes, no scan
spam, no brute-force pairing, no auth bypass. Every feature gated behind CONFIRMED protocol
facts shows *"Not yet supported on this firmware."* — never a fake success.
