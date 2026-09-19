# OMNITRIX — Noise ColorFit Caliber 2881

A community Android companion app for the **Noise ColorFit Caliber (device 2881, firmware R204.5.8)**.

> This project is experimental. It does not use Noise's private keys or proprietary source code. The BLE protocol will be discovered and implemented only for interoperability with the user's own watch.

## Goals

- BLE device discovery and connection
- GATT service/characteristic diagnostics
- Device/battery information where exposed by the watch
- Notifications and call-related features where supported
- Find-watch/vibration features where supported
- Activity/health data where exposed
- Omnitrix-inspired dashboard and controls

## Development

The first milestone is a diagnostic Android app that can scan for and connect to the Caliber, enumerate GATT services, and safely inspect characteristics. No firmware flashing or firmware updates are performed.

## APK

APK releases will be published in GitHub Releases once a build is ready for testing.
