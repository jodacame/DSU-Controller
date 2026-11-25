# DSU Controller

**Use your Joy-Con as motion controllers for emulators, through your Android phone.**

DSU Controller is a small Android app that takes the Nintendo Switch Joy-Con paired to your
phone by Bluetooth and serves them to an emulator on your local network over the DSU
(cemuhook) protocol: gyroscope, accelerometer, buttons and stick, one Joy-Con per player.

It is made for one situation: the emulator runs on a machine in another room, the game is
streamed to your TV, and the Joy-Con in your hand are out of that machine's Bluetooth range.
The phone sits next to you and bridges them.

> **Status: early development.** The project builds, the design and the protocol are written
> down, and the first milestone is in progress. There is no usable release yet. See the
> [roadmap](#roadmap).

## Highlights

- **Joy-Con to DSU bridge.** Up to four Joy-Con, each one its own controller slot, with motion,
  buttons and stick.
- **Works with DSU clients.** Built and checked against the UDP input driver of Eden and other
  yuzu-lineage emulators; other emulators with cemuhook motion support (such as Cemu and
  Dolphin) speak the same protocol.
- **Your phone needs no gyroscope.** The motion comes from the Joy-Con, so budget phones work.
- **A dashboard you can read from the sofa.** The screen is split in four colour blocks, P1 to
  P4, and the Joy-Con's own player lights show the same number.
- **Private by design.** No accounts, analytics, ads or cloud. The only network traffic is one
  local UDP socket, and only machines you approve on screen get any data.
- **No libraries.** The APK contains the Android framework APIs and the Kotlin standard library,
  nothing else. You can read the whole source in one sitting.

## Requirements

- An Android phone with **Android 12 or newer** whose system delivers Joy-Con motion to apps.
  This depends on the phone's kernel and Android build; the app includes a diagnostics screen
  that tells you whether your phone qualifies.
- Original Nintendo Switch **Joy-Con**, paired to the phone in Android's Bluetooth settings.
- An emulator with a **DSU / cemuhook client**, on the same local network as the phone.

## How it works

```
Joy-Con  --Bluetooth-->  Android phone (DSU Controller)  --UDP 26760, DSU-->  emulator
```

1. Android reads the Joy-Con and exposes its buttons, stick and motion sensors.
2. DSU Controller gives each Joy-Con a slot (P1 to P4) and runs a DSU server on UDP port 26760.
3. The emulator asks for controller data. The first time, its address appears on the phone with
   an **Allow** button; nobody is answered by default.
4. The emulator sees each Joy-Con as a UDP controller with motion.

Buttons and stick need the app on screen (Android delivers controller input only to the app in
the foreground), so the app keeps the screen on while the bridge runs and dims it when untouched.

## Install

There is no release yet. When there is one, it will be an APK to sideload, published on the
releases page of this repository. Until then you can [build from source](#build-from-source).

## Build from source

You need JDK 21 and the Android SDK (platform 36).

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew lintDebug           # Android lint
./gradlew assembleDebug       # debug APK: app/build/outputs/apk/debug/
./gradlew assembleRelease     # unsigned release APK: app/build/outputs/apk/release/
```

The release APK is unsigned on purpose: the build contains no signing configuration, so no key
can leak through it. Sign it with your own keystore:

```bash
apksigner sign --ks my-release-key.jks --out dsu-controller.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

## Roadmap

1. **Diagnostics build** - list paired Joy-Con and show their live motion, to confirm a phone
   delivers it and at what rate.
2. **DSU codec and server** - byte-exact protocol implementation with golden-packet tests, the
   approved-client list and a Python test client.
3. **The bridge** - Joy-Con registry, four slots, mapping of motion, buttons and stick.
4. **Dashboard and settings** - the four-block screen, client approval, port and screen options.
5. **First release** - signed APK and user guide.

## Documentation

| Document | What it covers |
|---|---|
| [Vision](docs/vision.md) | What the app is, who it is for and what it is not |
| [Architecture](docs/architecture.md) | Modules, data flow, permissions and constraints |
| [DSU protocol contract](docs/contracts/dsu-protocol.md) | The wire format and the Joy-Con mapping |
| [Dashboard UI contract](docs/contracts/dashboard-ui.md) | Layout, slot identity, states and screen rules |
| [Decisions](docs/decisions/) | Why it is built this way |

## Contributing

Issues and pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first: the
project has a few hard rules (no libraries, one socket, approved clients only) that keep it
small and auditable.

## Security

The app opens a network port on your phone, so security reports matter. See
[SECURITY.md](SECURITY.md) for how to report a problem.

## License

[MIT](LICENSE).

## Disclaimer

This project is not affiliated with, endorsed by or sponsored by Nintendo. "Nintendo Switch" and
"Joy-Con" are trademarks of Nintendo, used here only to describe compatibility. The app contains
no Nintendo code and does not emulate any console.
