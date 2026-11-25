# Contributing to DSU Controller

Thanks for your interest. This is a small project with a narrow goal, and a few rules keep it
that way.

## Before you start

- Read the [vision](docs/vision.md), in particular "What it is not". Features outside that scope
  (on-screen gamepads, sending the phone's own sensors, cloud features) will be declined.
- For anything larger than a fix, open an issue first so the approach can be agreed before you
  write code.

## Hard rules

A change that breaks one of these cannot be merged:

1. **No libraries in the APK.** Android framework and Kotlin standard library only. JUnit is
   allowed in tests.
2. **One socket.** The DSU server is the only network code.
3. **Approved clients only.** No address is trusted by default and there is no bypass.
4. **The wire format follows the reference client.** Changes to the codec are checked against
   the emulator's UDP client source and keep the golden-packet tests passing. See the
   [DSU protocol contract](docs/contracts/dsu-protocol.md).
5. **Own code only.** Do not copy code from emulators or other DSU projects; most are GPL and
   this project is MIT.
6. **No new permissions** without a change to the [architecture](docs/architecture.md) agreed in
   an issue.
7. **Nothing private in the repository:** no real IP addresses, host names, device serials or
   keys. Examples use documentation addresses such as `192.0.2.10`.

## Development

Requirements: JDK 21 and the Android SDK (platform 36).

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

All three must pass before you open a pull request.

## Code style

- Kotlin official code style; compiler warnings are errors.
- English for code, comments, documentation and commit messages.
- Comments explain why, not what.
- Every source file starts with `SPDX-License-Identifier: MIT`.
- New behaviour comes with tests. `protocol` and `server` are plain JVM code and are tested
  without a device.
- Update the docs in the same change as the code they describe.

## Commits and pull requests

- [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`,
  `test:`, `refactor:`, `chore:`.
- One topic per pull request, with a short description of what changed and how you tested it
  (phone model and Android version when it involves real Joy-Con).

## Reporting bugs

Please include the phone model, Android version, which Joy-Con (left, right), the emulator and
its version, and what the diagnostics screen shows. Do not post private network details.

By contributing you agree that your contribution is licensed under the [MIT License](LICENSE).
