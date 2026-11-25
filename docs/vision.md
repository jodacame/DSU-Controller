---
type: vision
kit: 2
title: Vision
status: active
summary: A dependency-free Android app that bridges Joy-Con paired to the phone to emulators over the DSU (cemuhook) protocol on the local network.
owner: maintainers
updated: 2026-11-25
audience: users, contributors, AI agents working on this repo
tags: android, dsu, joycon, motion, emulator
---

# Vision

## The problem

Motion-controlled Nintendo Switch games (tennis, bowling and similar) need a controller with a
gyroscope. When the emulator runs on a machine in another room and the game is streamed to a TV,
the Joy-Con in the player's hand have no way to reach it: they are out of Bluetooth range of
the emulator, and game-streaming clients do not forward their motion.

Emulators such as Eden, yuzu forks, Cemu and Dolphin accept motion over the network through the
DSU (cemuhook) protocol. Bridges from Joy-Con to DSU exist for Windows and Linux, and Android
apps exist that send the phone's own sensors, but many phones have no gyroscope. Nothing on
Android takes the Joy-Con the player already owns and puts them on the network.

## Who it is for

People who play emulated motion games away from the machine that runs the emulator and have an
Android phone (Android 12 or newer) and original Nintendo Switch Joy-Con. The phone needs no
motion sensors of its own. The app is distributed as a sideloaded APK and its source is open.

## What it is

A small Android app, written in Kotlin, that is a **DSU server for Joy-Con**:

- Joy-Con are paired to the phone in Android's Bluetooth settings. The app serves each one as
  its own DSU controller slot: motion (gyroscope and accelerometer), buttons and stick.
- The main screen is a **dashboard split in four**, one block per DSU slot. A connected slot
  is a solid block of its own colour with a giant player label (P1 to P4) and its Joy-Con (left
  or right, battery, motion); a free slot stays dark. The Joy-Con's player lights show the same slot number, and
  pressing any button on a Joy-Con makes its block flash, so it is always clear which
  controller is which player.
- While the bridge runs the app keeps the screen on, using Android's standard keep-screen-on
  window flag, so the bridge stays active for a whole session. The dashboard is made to be left
  on: bold, pleasant to look at, readable from across the room, and dimmed when untouched.
- The phone's IP address, the port and the server state are always visible.
- A **diagnostics screen** says, for each connected Joy-Con, whether this phone actually
  delivers its motion, so anyone can check their own phone.
- Only approved emulator machines get data: a new client that asks appears on screen with an
  Allow button; nobody is answered by default.
- It runs as a foreground service while the phone sits on a table or a stand.

Around the app: a signed APK, a README, and a Python test client that prints motion and buttons
live so the bridge can be checked without an emulator.

## What it is not

- Not a phone-motion app: it never sends the phone's own sensors.
- Not a virtual gamepad: no on-screen buttons or sticks.
- Not a Joy-Con driver: it uses what Android already exposes. If a phone's Android does not
  deliver Joy-Con motion, the app reports that and does not work around it.
- Not connected to anything else: no accounts, analytics, ads, crash reporting, update checks or
  cloud services. The only network traffic is the local DSU socket.
- Not an emulator configuration tool: setting up the emulator, the streaming setup or the
  network is the user's side.
- Not affiliated with Nintendo. "Joy-Con" and "Nintendo Switch" are named only to describe
  compatibility.

## What success looks like

1. On a supported phone, the diagnostics screen shows live gyroscope and accelerometer data
   from a paired Joy-Con.
2. The Python test client, run from an approved machine, prints motion that follows the Joy-Con
   and its button presses; from any other machine it gets nothing.
3. The emulator, pointed at the phone's IP and port, shows each Joy-Con as a UDP controller, and
   a tennis swing or a bowling throw follows the Joy-Con in the right direction with no lag
   the player notices.
4. It keeps working for a whole play session with the dashboard on, and a glance at the phone
   tells which Joy-Con is which player.
5. Anyone can read the whole source in one sitting and confirm that nothing leaves the phone
   except those UDP packets.
