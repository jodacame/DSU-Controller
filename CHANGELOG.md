# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Project documentation: vision, architecture, DSU protocol contract, dashboard UI contract and
  design decisions.
- Gradle project skeleton: a single app module with no dependencies, unit tests and lint.
- Diagnostics screen: for each connected Joy-Con it shows the sensors Android exposes, live
  gyroscope and accelerometer values with their real rate, button and stick events, battery,
  and lets you change the player LEDs.
- DSU (cemuhook) protocol codec with golden-packet tests, and `tools/dsu_client.py`, a test client
  that uses the Python standard library only.
- UDP server on port 26760 that answers approved emulators only; other addresses that send a
  valid request are shown for approval.
- The bridge: each Joy-Con gets its own slot (up to four) and its player LEDs show the slot.
  Motion, buttons and stick are sent to every subscribed emulator.
- Foreground service that keeps the bridge running with the app in the background.
- Dashboard with four colour blocks (player label, Joy-Con side, motion state, battery icon),
  the phone's address and port, the server state, and an approval bar for new emulators.
- The dashboard uses the bundled Archivo font (SIL Open Font License) and the approved layout:
  the label, then title, status and battery, then a left or right Joy-Con drawing.
- Settings: allowed emulators (add by IP address, remove), port, keep the screen on, dim after
  60 seconds.

### Fixed

- Motion no longer freezes for minutes. Android's input reader can drop every motion sample of a
  Joy-Con after it resets the device; the bridge now restarts that Joy-Con's sensors after one
  second without a sample, logs the sample rate per slot every five seconds, and the dashboard
  warns when no sample came for two seconds.
