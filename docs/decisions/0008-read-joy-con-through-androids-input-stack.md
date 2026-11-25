---
type: decision
kit: 2
title: Read Joy-Con through Android's input stack
status: active
summary: Joy-Con are read through Android's own input devices and their sensors, one Joy-Con per DSU slot, with a diagnostics build as the first deliverable.
date: 2026-11-25
deciders: maintainers
supersedes:
superseded_by:
tags: joycon, motion, platform
---

# 0008. Read Joy-Con through Android's input stack

## Context

A Joy-Con paired to an Android phone is handled by the kernel's Nintendo controller driver.
Android then presents it as a game controller and, since Android 12, exposes its gyroscope and
accelerometer through `InputDevice.getSensorManager()`. Whether that works depends on the
phone's kernel and Android build, not only on the app.

The alternative is to talk to the Joy-Con directly over Bluetooth and decode its reports in the
app, as desktop bridges do.

## Decision

- The app reads Joy-Con **only through Android's input stack**: `InputDevice` for discovery,
  buttons, stick and battery, and the device's own `SensorManager` for motion.
- Each Joy-Con is served as its own DSU slot (up to four), and the main screen shows the four
  slots.
- The **first deliverable is a diagnostics build** that proves on a real phone that motion
  arrives, measures its rate and background behaviour, and records the raw axis orientation.
  The bridge is built on those findings, and the diagnostics screen stays in the product.

Alternatives ruled out:

- **Direct Bluetooth access to the Joy-Con:** a normal Android app cannot open the classic
  Bluetooth HID channel of a device the system already owns, and it would mean re-implementing
  the controller protocol and calibration that the kernel driver already handles.
- **Combining a left and a right Joy-Con into one slot:** the games this targets use one Joy-Con
  per player; a combined mode can be added later without changing the protocol.

## Consequences

- No Bluetooth or location permission is needed.
- On a phone whose Android does not deliver Joy-Con motion the app cannot work, and it says so
  instead of working around it.
- Android delivers controller buttons and sticks only to the app on screen, so those need the
  app in the foreground; motion is expected to continue in the foreground service.
- Two wireless hops (Bluetooth, then Wi-Fi) add latency that has to be measured.
