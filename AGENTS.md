# DSU-Controller — rules for agents

How to work in this repository. Keep it short; link to docs/ for detail.

## Read first

- docs/vision.md — what this is and what it is not.
- docs/architecture.md — how it is built.
- docs/contracts/ — the rules every change keeps.
- docs/decisions/ — why it is built this way.
- LOCAL.md — only if it exists: the maintainer's private, git-ignored notes (build machine,
  signing, test devices). Never copy anything from it into a tracked file.

## Commands

The project is a plain Gradle build (JDK 21, Android SDK platform 36). These commands must keep
working.

```bash
./gradlew testDebugUnitTest    # unit tests (codec golden packets, server loopback, mapping tables)
./gradlew lintDebug            # Android lint
./gradlew assembleDebug        # debug APK for quick installs over adb
./gradlew assembleRelease      # unsigned release APK
python3 tools/dsu_client.py <phone-ip> [--port 26760] [--slot 0]   # live motion and buttons without an emulator
```

There is no dependency-install step: the app has no dependencies.

The release APK is unsigned. Sign it with `apksigner` and a keystore kept outside the
repository (docs/decisions/0004).

## Hard rules

A change that breaks any of these is rejected.

1. **No libraries in the APK.** Only the Android framework and the Kotlin standard library: no
   AndroidX, Compose, coroutines, analytics, ads, crash reporters or any SDK. JUnit is allowed
   in `src/test` only. `tools/dsu_client.py` uses the Python standard library only.
2. **One socket.** The UDP server is the only network code. No HTTP, WebView, DNS lookup,
   update check or second socket. Nothing leaves the phone except DSU responses.
3. **Approved clients only.** An address that the user did not approve never gets a response.
   Never add an "allow all" default, a pre-approved address or a debug bypass.
4. **The wire format is read from the reference client, not from memory.** Follow
   docs/contracts/dsu-protocol.md; if you change the codec, re-check it against
   `udp_protocol.h` / `udp_client.cpp` of the Eden source tree and keep the golden-packet tests
   passing.
5. **Joy-Con axes, buttons and sticks are mapped in one place** (`controller/JoyConMapping`).
   Never flip a sign or rename a button anywhere else.
6. **Permissions are fixed** by docs/architecture.md. Adding one needs that document changed
   and the maintainers' approval first.
7. **No secrets or keys in the repo.** No keystore, password or signing config for release. No
   built APK in git.
8. **Everything committed is public.** The repository will be open source (MIT). No private IP
   addresses, host names, e-mail addresses, device serials or details of anyone's home network
   in code, docs, tests, examples, commit messages or PR text. Examples use documentation
   addresses such as `192.0.2.10`. Anything private that is worth keeping goes in `LOCAL.md`.
   Every source file starts with the SPDX line `SPDX-License-Identifier: MIT`.
9. **Own code only.** Do not copy code from emulators or other DSU projects (most are GPL). The
   protocol facts are read from them; the implementation is written here.
10. **English** for code, comments, docs, commit messages and the app's UI, in a tone fit for a
    public project.
11. **Scope:** this repo is the app, its tests and its test client. Emulator, streaming and
    network configuration belong to the user's side and are never changed from here.
12. Keep the docs true when the code changes.
