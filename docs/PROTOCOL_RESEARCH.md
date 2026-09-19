# OMNITRIX — Protocol Research Notes (Phase 9)

Publicly available research relevant to the Noise ColorFit Caliber. Every statement carries an
evidence level, and **nothing here is treated as proof for device 2881 / firmware R204.5.8** until
captured from the actual watch by the diagnostic app.

Evidence levels used across the app: `CONFIRMED` (captured from the actual watch),
`LIKELY` (public research or strong heuristic, not validated on this device),
`UNKNOWN` (guessed or never verified).

---

## CONFIRMED

- (Nothing yet. This section fills as the diagnostic app captures data.)

## LIKELY (public research — hypotheses only)

- **Noise watches historically pair with the Da Fit app family.** Noise's own support pages for
  ColorFit models instruct pairing through the "Da Fit" app; newer models use the rebranded
  "NoiseFit" app. Source: gonoise.com support articles.
- **Da Fit watches speak the MOYOUNG / MOYOUNG-V2 protocol** (Gadgetbridge documentation &
  `MoyoungConstants.java`, AGPL, krzys_h / Freeyourgadget Gadgetbridge):
  - GATT service: `0000feea-0000-1000-8000-00805f9b34fb`
  - Characteristic `0000fee2-…` **DATA_OUT** (phone→watch writes), `0000fee3-…` **DATA_IN** (watch→phone notifications), `0000fee1-…` steps, `fee5/fee6` "special", `fee7/fee8` ECG variants.
  - Packet framing (V2): `FE EA | sizeHi+32 sizeLo | cmd | payload…`, where `size` counts
    UUID+size+cmd bytes (empty-payload command ⇒ size=5). MTU=20 legacy variant uses
    `FE EA 16 len cmd …`.
  - Protocol version (V1 vs V2) is detected by reading the **manufacturer name** characteristic
    (`0x2A29` in Device Information service `0x180A`): values like `MOYOUNG` / `MOYOUNG-V2`.
  - Some find-watch / find-phone / shutdown commands exist in that family (e.g. find-watch cmd `97`),
    but availability varies per firmware — treat as UNKNOWN for the Caliber.
- **Standard SIG services are worth reading first** on any watch of this family (Gadgetbridge notes):
  Device Information `0x180A` (manufacturer `0x2A29`, model `0x2A24`, serial `0x2A25`,
  firmware `0x2A26`), Battery `0x180F` / Battery Level `0x2A19`. These are read-only and safe.
- Noise manuals mention the NoiseFit app showing a **QR on the watch for app binding** and a
  separate BT-call radio. The exact QR content is undocumented → captured raw by the app.
- General BLE methodology for capturing the official protocol: Android "Bluetooth HCI snoop log"
  + bugreport (btsnoop) + Wireshark — standard practice for the next research phase. **No
  such capture exists for the Caliber 2881 yet.**

## UNKNOWN (do not assume)

- Whether 2881/R204.5.8 exposes `0xFEEA` at all.
- Whether the Caliber requires NoiseFit app binding/encryption before GATT access (some Noise models
  link only through the app, and the watch may reject or ignore unbound GATT clients).
- The QR payload format (MAC? URL? serial? token? encrypted blob?).
- All opcodes, session-init sequences, find-watch behavior, time sync, notification/call handling.
- The old repo's `16186f00/01/02-…` UUIDs, PING/ACK bytes, protobuf frames, registration packet —
  no public source corroborates them; classified as **fabricated guesses, discarded**.

## Driver policy (enforced in code)

- `NoiseColorFitCaliber2881Driver` advertises identification heuristics and a read-only GATT
  vocabulary. **It builds no command packets.** Any `buildCommand()` call returns an
  `Unsupported` result shown to the user as *"Not yet supported on this firmware."*
- The Moyoung-family codec (`protocol/PacketEncoder`, `protocol/PacketDecoder`) is pure Kotlin used
  by unit tests and by the diagnostic log to *label* (never act on) hypotheses about incoming bytes.
- `BleConnection` exposes no characteristic-value write API in diagnostic mode.
