---
type: architecture
kit: 2
title: Architecture
status: active
summary: One Kotlin Android app with no libraries - Joy-Con paired to the phone feed a pure-Kotlin DSU codec and a single UDP server that answers approved clients only.
owner: maintainers
updated: 2026-11-25
scope: the whole repository - Android app, build and signing, Python test client
paths:
tags: android, kotlin, udp, dsu, joycon
---

# Architecture

## Overview

```
 Joy-Con          phone                                              emulator machine
+--------+ BT  +------------------------------------------------+   +----------------+
| left   +---->| Android input stack (kernel Nintendo driver)   |   |                |
| right  +---->|    | InputDevice + its sensors, key and stick  |   |                |
+--------+     |    v              events                       |   |                |
               | JoyConRegistry -> SlotTable (4 slots)          |   |                |
               |    | PadState per slot (motion, buttons, stick)|   |                |
               |    v                                           |UDP|  DSU client    |
               | DsuServer --- DsuCodec (encode / decode / CRC)-+<->+  (requests     |
               |   ^  approved clients, subscribers (5 s expiry)|   |   every 3 s)   |
               |   |                                            |   |                |
               | BridgeService (foreground)                     |   +----------------+
               |   ^ start / stop, state                        |
               | MainActivity (4 slots)  DiagnosticsActivity    |
               +------------------------------------------------+
```

1. **Controllers in.** `JoyConRegistry` watches Android's input devices and recognises Joy-Con
   by vendor and product id. Each one is given a slot in `SlotTable` (0-3) and keeps it while
   it stays connected. For each Joy-Con the registry listens to the sensors Android exposes for
   that device and receives its key and stick events, and keeps one `PadState` per slot.
2. **Requests in.** `DsuServer` owns the only socket. A datagram from an address that is not
   approved is never answered; if it is a well-formed DSU request, the address is offered to the
   user as a pending client. Requests from approved clients are validated and decoded by
   `DsuCodec`: version and port-info requests are answered at once, a pad-data request adds or
   renews the sender in the subscriber table (5 s expiry).
3. **Pad data out.** Every motion sample of a slot produces one 100-byte pad-data packet with
   that slot's current buttons, stick and motion, sent to every live subscriber. A button or
   stick change with no motion sample pending is sent at once as well. With no subscriber,
   nothing is encoded or sent.
4. **State to the screen.** The service exposes one immutable state: server status, the four
   slots (Joy-Con side, battery, whether motion is arriving, packets per second), approved and
   pending clients, last error. The activities observe it on the main thread.

The wire format and every protocol rule are in [the DSU contract](contracts/dsu-protocol.md).

## Layers and modules

A single Gradle module, `app`. Packages (dependencies only point downwards in this list):

| Package | Responsibility | Android APIs |
|---|---|---|
| `ui` | `MainActivity`: the four-block slot dashboard (custom framework Views drawn on `Canvas`), IP, port, server state, start/stop, pending-client approval. `SettingsActivity`: approved clients, port, screen behaviour. `DiagnosticsActivity`: per Joy-Con, which sensors Android exposes and whether data arrives. Framework Views only. The activity forwards controller key and stick events to the service. | yes |
| `service` | `BridgeService`: foreground service with a notification; owns the registry and the server for as long as the bridge runs. | yes |
| `settings` | Typed, validated settings on `SharedPreferences`: port, approved clients, keep screen on. | yes |
| `controller` | `JoyConRegistry` (device discovery, per-device sensor listeners, battery), `SlotTable`, `PadState`, and `JoyConMapping`: the single place that turns a Joy-Con's Android axes and key codes into DSU motion, buttons and stick, one table per Joy-Con side. | input, sensors |
| `server` | `DsuServer`: socket, receive thread, approved and pending clients, subscriber table, publish. Uses only `java.net`. | none |
| `protocol` | `DsuCodec` and the message types: byte-exact encode/decode, CRC32 via `java.util.zip.CRC32`. | none |

`protocol` and `server` are plain JVM code, unit-tested on the build machine (including a
loopback socket test) without a phone. `controller` keeps Android types at its edge so mapping
and slot assignment are also unit-tested with plain values.

Outside the app:

- `tools/dsu_client.py` - Python 3, standard library only. Acts as a DSU client and prints live
  motion, buttons and stick per slot, verifying sizes and CRC. It shares no code with the app on
  purpose: it is an independent check of the contract.

Threads: main (UI and controller key/stick events), one sensor `HandlerThread`, one socket
receive thread. Sending happens on the thread that produced the data; `PadState`, the slot table
and the subscriber table are the only shared state and are thread-safe. No coroutines:
`kotlinx.coroutines` is a library.

## Decisions that shape it

- [0001](decisions/0001-minimum-android-12.md) - minimum Android 12 (API 31), the first
  version with per-controller sensors.
- [0002](decisions/0002-no-libraries-in-the-apk.md) - no libraries in the APK: Android
  framework and the Kotlin standard library only.
- [0003](decisions/0003-junit-for-tests-only.md) - JUnit is allowed for tests only.
- [0004](decisions/0004-unsigned-gradle-build-signed-outside-the-repo.md) - plain Gradle
  build; the release APK is signed outside Gradle with a keystore kept out of the repository.
- [0005](decisions/0005-application-id-and-name.md) - application id
  `dev.jodacame.dsucontroller`, visible name "DSU Controller".
- [0006](decisions/0006-mit-license.md) - MIT license.
- [0007](decisions/0007-clients-are-approved-on-screen.md) - nobody is answered by default;
  clients are approved on screen.
- [0008](decisions/0008-read-joy-con-through-androids-input-stack.md) - Joy-Con are read
  through Android's input stack, one per slot; diagnostics come first.

## Constraints

- **Depends on the platform.** The app reads Joy-Con through Android's input stack
  (`InputDevice`, `InputDevice.getSensorManager()`, API 31+), which needs the Nintendo
  controller driver in the phone's kernel. Whether a given phone delivers Joy-Con motion is
  shown by the diagnostics screen, which stays in the product for that reason.
- **What Android offers:** on a phone that qualifies, each Joy-Con appears as **one** input
  device that merges the controller and its IMU, with sources
  `KEYBOARD | GAMEPAD | JOYSTICK | SENSOR`, a gyroscope and an accelerometer with hardware
  timestamps, a battery and the player lights. The sensors stay disabled until an app listens.
- **Measured with the diagnostics build** (Android 16, kernel 6.12, one left and one right
  Joy-Con):
  - Gyroscope (rad/s) and accelerometer (m/s²) samples reach the app at about 200 per second
    from one Joy-Con and about 265 per second from the other, so the rate is a property of the
    connection and the code never assumes one.
  - Delivery continued unchanged with the app in the background and with the screen off, for the
    half minute measured and without a foreground service. Longer periods are not measured yet.
  - **The sensor events' own timestamps are not usable as a clock:** they advanced about twice
    as fast as real time. Everything that needs time (sample rate, the DSU motion timestamp)
    uses the phone's monotonic clock at the moment a sample arrives.
  - The four player LEDs are exposed as four separate on/off lights named `...player-1` to
    `...player-4` (not as one "player id" light), and the app can set them. A fifth light,
    `player-5`, is the Home button ring and is left alone. When the app releases the lights
    they turn off; they do not return to their previous state.
  - Battery level and status are reported.
  - Still to record: the key codes and stick axes each side sends, and the orientation of the
    motion axes. Both need a person holding the controller.
- **Buttons and stick need the app on screen.** Android delivers controller key and stick events
  only to the focused window, so the app is designed to stay on screen while the bridge runs.
  Motion is expected to continue in the foreground service regardless. System keys (Home,
  Capture) may be taken by Android and never reach the app.
- **Screen stays on, the standard way.** While the bridge runs, the activity sets
  `FLAG_KEEP_SCREEN_ON` on its window and clears it when the bridge stops or the activity
  leaves the foreground. No screen wake lock, no change to system settings and no extra
  permission. Because the screen is on for hours and connected slots are bright colour
  blocks, the dashboard sits on a black ground and lowers the window brightness after a period
  without touch (any touch restores it).
- **Slots are easy to tell apart.** Each slot has a fixed label (P1-P4) and colour used
  everywhere in the UI, defined in [the dashboard contract](contracts/dashboard-ui.md). The app sets each Joy-Con's player LEDs to its slot number (player N lights the first N LEDs,
  as the console does) through the device's `LightsManager` when the platform exposes them, and a button press on a Joy-Con highlights its
  block. Information is never carried by colour alone (player label and left/right title too).
- **Network:** exactly one socket in the whole app, the UDP server (default port 26760). No HTTP
  client, no WebView, no DNS lookups. `INTERNET` is declared only because Android requires it
  for any socket.
- **Clients:** exact IPv4 addresses. The approved list is empty on a fresh install. A pending
  client is listed only after it sent a well-formed DSU request; the pending list is small and
  bounded, so a noisy network cannot flood the screen or the memory. Approval is always a
  deliberate tap or a typed address.
- **Permissions (complete list):** `INTERNET`, `FOREGROUND_SERVICE` plus the typed
  foreground-service permission, `WAKE_LOCK`, `POST_NOTIFICATIONS` (Android 13+, for the service
  notification). Joy-Con are read through the input stack, so no Bluetooth or location
  permission is requested. Adding a permission is a reviewed change to this document.
- **Manifest:** only the launcher activity is exported; the service is not. `allowBackup` is
  off. No obfuscation or minification: the shipped code is the readable code.
- **Battery:** sensor listeners are registered only while the bridge runs; nothing is sent
  without a live subscriber.
- **Build and signing:** a plain Gradle wrapper build (JDK 21). The release APK is unsigned;
  it is signed outside Gradle with a keystore that never enters the repository.
- **Publication-ready:** the repository will be public. No private addresses, host names,
  credentials or personal data in code, docs, tests, commit messages or examples; examples use
  documentation addresses such as `192.0.2.10`.
- **Language:** code, comments, docs and the app's UI in English.
