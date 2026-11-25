---
type: decision
kit: 2
title: Minimum Android 12
status: active
summary: The app requires Android 12 (API 31), the first version that exposes the sensors of a connected controller to apps.
date: 2026-11-25
deciders: maintainers
supersedes:
superseded_by:
tags: android, platform
---

# 0001. Minimum Android 12

## Context

The app reads the gyroscope and accelerometer of Joy-Con connected to the phone. Android exposes
the sensors of an input device to apps through `InputDevice.getSensorManager()`, added in
Android 12 (API 31). Per-device battery state and lights arrived in the same release.

## Decision

`minSdk` is 31 (Android 12). There is no fallback path for older versions.

Alternatives ruled out:

- **Supporting Android 10 or 11:** those versions cannot deliver controller motion to an app, so
  the app could not do its one job there.

## Consequences

- No version checks or compatibility branches for the controller APIs.
- Android 12 is necessary but not sufficient: the phone's kernel must also include the Nintendo
  controller driver. The diagnostics screen tells the user whether their phone qualifies.
