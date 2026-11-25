---
type: contract
kit: 2
title: Dashboard UI
status: active
summary: The look and behaviour every change to the main screen keeps - four colour-block slots, fixed slot identity, the status bar, client approval and the screen-on rules.
owner: maintainers
updated: 2026-11-25
area: dashboard-ui
paths: app/src/main/kotlin/dev/jodacame/dsucontroller/ui/
version: 1.0
consumers: MainActivity, SettingsActivity, DiagnosticsActivity
enforced_by: unit tests of the slot view model, screenshot review on a real phone in both orientations
tags: ui, dashboard, slots, accessibility
---

# Dashboard UI

The main screen is a dashboard that stays on for a whole play session, usually with the phone
on a stand a few metres away. Its job is to answer one question at a glance: **which Joy-Con is
which player, and is it working?** The chosen direction is "colour blocks": a connected slot is
a solid block of its colour with a giant player label; a free slot is nearly black.

## Types and shapes

### Layout

```
+-------------------------------+-------------------------------+
|  P1   Left Joy-Con         [] |  P2   Right Joy-Con        [] |
|       Motion ready [|||.]     |       Motion ready [||..]     |
|                               |                               |
+-------------------------------+-------------------------------+
|  P3   Free                    |  P4   Free                    |
|       Pair a Joy-Con in       |       Pair a Joy-Con in       |
|       Bluetooth settings      |       Bluetooth settings      |
+-------------------------------+-------------------------------+
| DSU CONTROLLER  192.0.2.10 : 26760  Sending to 1 emulator     |
|                              Settings   Diagnostics   [ Stop ]|
+---------------------------------------------------------------+
```

- Four slot blocks in a **2 x 2 grid in both orientations**, in reading order: P1 top-left, P2
  top-right, P3 bottom-left, P4 bottom-right. The grid fills the screen above the status bar.
- 4 dp gaps on a pure black ground. Outer corners of the grid are rounded (24 dp), inner
  corners nearly square (6 dp), so the four blocks read as one object.
- The **status bar** is one row at the bottom, at least 56 dp tall. When it does not fit
  (portrait), it wraps to two rows; it never truncates the address.

### Slot identity (fixed, never reassigned)

| Slot | Label | Block colour | Text on the block |
|---|---|---|---|
| 0 | P1 | blue `#2F6BFF` | white `#FFFFFF` |
| 1 | P2 | yellow `#FFC629` | near-black `#1A1400` |
| 2 | P3 | coral `#FF6B4A` | near-black `#1F0A05` |
| 3 | P4 | mint `#5FE0B7` | near-black `#04241A` |

The DSU slot number on the wire is the label minus one. Colours are tokens in one place; these
values are the starting palette and may be tuned after seeing them on a real screen, as long as
text keeps a contrast of at least 4.5:1 and the four blocks stay distinguishable by lightness as
well as hue.

### Slot block content

| Element | Rule |
|---|---|
| Player label (`P1`...`P4`) | The dominant element: condensed, heaviest weight, about 60% of the block's height. Always shown, connected or free. |
| Title | `Left Joy-Con`, `Right Joy-Con`, or `Free`. |
| Status line | One short line about motion, see states below. |
| Battery | A battery icon with four segments next to the status line, drawn in the block's text colour. Never written as text. Not shown when free or when Android reports no battery level. |
| Joy-Con drawing | A simple left or right silhouette at the outer edge of a connected block. Not shown when free. |

### Slot states

| State | Block | Status line |
|---|---|---|
| Free | near-black `#121212`, label `#2E2E2E`, text `#A6A6A6` | `Pair a Joy-Con in Bluetooth settings` |
| Connected, bridge stopped | slot colour | `Ready` + battery icon (motion is not read while stopped) |
| Connected, motion ready | slot colour | `Motion ready` + battery icon; shown once a first motion sample arrived since Start, or during the first 10 seconds after Start |
| Connected, no motion | slot colour with a diagonal hatch | `No motion yet. Move this Joy-Con.` (tapping the block opens Diagnostics); shown when no motion sample came in the 10 seconds after Start. A Joy-Con lying perfectly still sends no samples, so moving it is the first thing to try |
| Battery low | slot colour; the battery icon shows one segment inside a filled pill of the text colour, so low differs by shape and not only by fill | status line unchanged + low battery icon |
| Button pressed | the block inverts (colour and text swap) while any button of that Joy-Con is held | unchanged |

### Status bar

Left to right: app name, the phone's `IP : port`, the server state, `Settings`, `Diagnostics`,
and the primary button.

| Server state | Text | Primary button |
|---|---|---|
| Stopped | `Stopped` | `Start` |
| Running, no client subscribed | `Waiting for an emulator` | `Stop` |
| Running, clients subscribed | `Sending to <n> emulator` / `emulators` | `Stop` |
| Cannot start | the reason, for example `Port 26760 is in use` | `Start` |
| No network | `No Wi-Fi address` in place of the IP | unchanged |

### Client approval

When an unknown machine sends a valid request, a bar appears above the status bar:
`<address> asks for controller data` with `Ignore` and `Allow`. One request at a time; others
wait their turn. `Ignore` hides that address until the app is restarted. The approved list is
edited in `Settings` (remove, add by hand).

### Settings screen

Opened with `Settings` in the status bar; `Back` returns to the dashboard. Same black ground
and dark panels as the free slots, no slot colours.

| Section | Content |
|---|---|
| Emulators allowed | One row per approved address with `Remove`; the row of a machine that is subscribed right now says `Connected now`. Below the list: a field `Add an emulator by IP address` and `Add`. An empty list says `No emulator allowed yet` and explains the two ways to add one. |
| Port | The UDP port, default `26760`. |
| Keep the screen on | Switch, on by default: `While the bridge is running`. |
| Dim when not touched | Switch, on by default: `After 60 seconds`. |
| About | App name and version, license, link to Diagnostics. |

This phone's own address is shown at the top, so the user can copy it into the emulator.

## Interfaces

- The dashboard renders one immutable state from the bridge service (server state, four slot
  states, pending client). It holds no state of its own apart from the dim timer.
- `Settings`: approved clients (add by address, remove), port, keep screen on, dim when not
  touched. Every change is validated, saved at once and applied without restarting the app.
- `Diagnostics`: per Joy-Con, the sensors Android exposes, live values and sample rate, in the
  same slot colours.
- The activity forwards controller key and stick events to the service; it does not interpret
  them, except to show the "button pressed" inversion.

## Rules and invariants

1. **Identity never depends on colour alone.** The player label is always visible and is the
   largest element on screen; the left/right title is always text.
2. **A slot's label and colour never change** and are the same on the dashboard, in
   Diagnostics and in the notification.
3. **The Joy-Con's player lights show its slot** (one light for P1 up to four for P4) whenever
   the platform lets the app set them; the dashboard never shows a number that disagrees with
   the lights.
4. **Readable at a distance.** The player label and title are legible from about 3 metres on a
   6.5-inch phone. Secondary text is at least 14 sp.
5. **Touch targets are at least 48 dp**, including `Settings` and `Diagnostics`.
6. **System typefaces only**, in keeping with the no-libraries rule: a condensed heavy system
   face for the player label, the default system face for everything else. No bundled fonts.
7. **Screen on only while the bridge runs.** `FLAG_KEEP_SCREEN_ON` is set when the bridge
   starts and cleared when it stops or the activity leaves the foreground. Nothing else keeps
   the screen awake.
8. **Dim at rest.** After 60 seconds without a touch, the window brightness drops to a low
   level; any touch restores it. Controller input does not restore it, since it never stops
   during play. Colours and layout stay the same when dimmed, so identity is preserved.
9. **Motion is honest.** The only animations are the "button pressed" inversion and state
   changes of a block; nothing loops or decorates. With system animations disabled, state
   changes are instant.
10. **Both orientations and any aspect ratio** keep the 2 x 2 grid, the reading order and the
    full address; nothing is clipped or overlaps on a small screen or with large font settings.
11. **Text is plain English**, sentence case, and names what the user sees: "emulator",
    "Joy-Con", "Free". Errors say what happened and what to do.
12. **No private data on screen beyond the phone's own address**; client addresses are shown
    only to approve or remove them.
13. **Battery is visual.** Four segments map to the levels Android reports (full, high, medium,
   low); charging adds a bolt mark. The icon carries a spoken description for screen readers
   (`Battery high`), which is the only place the level exists as words.

## Errors

| Case | What the user sees |
|---|---|
| No Joy-Con paired | Four free blocks, each with the pairing hint |
| More than four Joy-Con connected | The first four keep their slots; a line in the status bar says `4 slots in use` |
| Joy-Con connected but this phone delivers no motion | The "no motion" state on that block, linking to Diagnostics |
| Port in use or socket error | Server state shows the reason; the primary button offers `Start` again |
| Wi-Fi lost while running | `No Wi-Fi address`; the bridge keeps running and recovers when the address returns |
| Address typed in Settings is not a valid IPv4 address | The field keeps the text and says `Enter an address like 192.168.1.60`; nothing is added |
| Address already on the list | `Already allowed`; nothing is added |
| Port outside 1024-65535 | `Enter a port between 1024 and 65535`; the old port stays |
| Port changed while the bridge runs | The bridge restarts on the new port and the status bar shows it |
| Notification permission denied | The bridge still runs while the app is on screen; `Settings` explains what is lost |
| App sent to the background | The notification says the bridge is running and that buttons and stick need the app on screen |
