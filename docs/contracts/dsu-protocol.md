---
type: contract
kit: 2
title: DSU (cemuhook) wire protocol
status: active
summary: Byte-exact DSU server behaviour and Joy-Con to DSU mapping the app must keep so Eden-family UDP clients accept its packets.
owner: maintainers
updated: 2026-11-25
area: dsu-protocol
paths: app/src/main/kotlin/dev/jodacame/dsucontroller/protocol/, app/src/main/kotlin/dev/jodacame/dsucontroller/server/, app/src/main/kotlin/dev/jodacame/dsucontroller/controller/JoyConMapping.kt, tools/dsu_client.py
version: 1.0
consumers: Eden-family emulator UDP input clients, tools/dsu_client.py
enforced_by: golden-packet unit tests of the codec, mapping-table unit tests, tools/dsu_client.py against a real phone
tags: protocol, udp, dsu, cemuhook, joycon
---

# DSU (cemuhook) wire protocol

The phone is the **DSU server**; the emulator is the **client**. The reference client is the
UDP input driver shared by Eden and its forks (yuzu lineage). The single source of truth is that
client code, not any written spec:

- `src/input_common/helpers/udp_protocol.h` and `udp_protocol.cpp`
- `src/input_common/drivers/udp_client.cpp`

in the Eden source tree (read from an Eden fork on 2026-11-25). When this document and that code disagree, the code wins and this document
is fixed.

## Types and shapes

All integers are **little-endian**. Floats are IEEE-754 32-bit little-endian. Structs are packed
(no padding).

### Header (20 bytes, every packet)

| Offset | Size | Field | Value |
|---|---|---|---|
| 0 | 4 | magic | `DSUS` from the server (`0x53555344` LE), `DSUC` from the client (`0x43555344` LE) |
| 4 | 2 | protocol version | `1001` |
| 6 | 2 | payload length | number of bytes **after offset 16**: body size + 4 (the type field counts as payload) |
| 8 | 4 | CRC32 | standard CRC-32 (IEEE 802.3, same as `java.util.zip.CRC32`) of the whole packet with these 4 bytes set to zero |
| 12 | 4 | id | sender id: a random value chosen once per server start (server), client id (client) |
| 16 | 4 | message type | `0x100000` version, `0x100001` port info, `0x100002` pad data |

### Requests (client to server)

| Type | Total size | Body |
|---|---|---|
| Version `0x100000` | 20 | empty |
| Port info `0x100001` | 28 | `u32` pad count (1-4), then 4 bytes of slot numbers; only the first `pad count` are meaningful |
| Pad data `0x100002` | 28 | `u8` flags (0 = all pads, 1 = by slot, 2 = by MAC), `u8` slot, 6 bytes MAC |

The reference client sends, every 3 seconds, one port info request for slots `0,1,2,3` and one pad data request
with flags `0` (all pads). It never sends a version request, but the server answers one anyway.

### Responses (server to client)

| Type | Total size | Payload length field | Body |
|---|---|---|---|
| Version | 22 | 6 | `u16` = `1001` |
| Port info | 32 | 16 | the 12-byte slot block below |
| Pad data | 100 | 84 | the 80-byte block below |

Slot block (12 bytes), shared by port info and pad data:

| Offset in packet | Size | Field | Value sent |
|---|---|---|---|
| 20 | 1 | slot id | 0-3 |
| 21 | 1 | state | 0 disconnected, 2 connected |
| 22 | 1 | model | 2 (full gyro) for an active slot, 0 otherwise |
| 23 | 1 | connection type | 2 (Bluetooth) for a Joy-Con, 0 for an empty slot |
| 24 | 6 | MAC address | a stable 6-byte id derived from the Joy-Con's Android device descriptor (not its real Bluetooth address); zeros for an empty slot |
| 30 | 1 | battery | 0 none ... 5 full, `0xEE` charging, `0xEF` charged |
| 31 | 1 | port info: zero terminator / pad data: `is active` (1 while streaming) | |

Rest of the pad data packet:

| Offset | Size | Field | Notes |
|---|---|---|---|
| 32 | 4 | packet counter | must change on every packet of a slot |
| 36 | 2 | digital buttons | bit 0 Share, 1 L3, 2 R3, 3 Options, 4 Up, 5 Right, 6 Down, 7 Left, 8 L2, 9 R2, 10 L1, 11 R1, 12 Triangle, 13 Circle, 14 Cross, 15 Square |
| 38 | 1 | home | 0 / 1 |
| 39 | 1 | touch hard press | 0 / 1 |
| 40 | 4 | left stick X, left stick Y, right stick X, right stick Y | `u8`; the client computes `(value - 127) / 127`, so neutral is `127` |
| 44 | 12 | analog buttons | d-pad left, down, right, up, square, cross, circle, triangle, R1, L1, R2, L2 |
| 56 | 12 | two touch points | each: `u8` active, `u8` id, `u16` x, `u16` y |
| 68 | 8 | motion timestamp | `u64`, microseconds |
| 76 | 12 | accelerometer x, y, z | floats, in **g** |
| 88 | 12 | gyroscope pitch, yaw, roll | floats, in **degrees per second** |

### What the client does with motion

`udp_client.cpp` maps the packet to the emulated controller as:

```
gyro_x  =  pitch / 312      accel_x =  accel.x
gyro_y  =  roll  / 312      accel_y = -accel.z
gyro_z  = -yaw   / 312      accel_z =  accel.y
```

It measures the time between packets with its own clock and does **not** read the motion
timestamp. The timestamp is still sent correctly because other DSU clients use it.

### What the client does with buttons and sticks

The client's default mapping from DSU inputs to Switch buttons (`GetButtonMappingForDevice`):

| Switch button | DSU input | Where in the packet |
|---|---|---|
| A | Circle | buttons bit 13 |
| B | Cross | buttons bit 14 |
| X | Triangle | buttons bit 12 |
| Y | Square | buttons bit 15 |
| Plus | Options | buttons bit 3 |
| Minus | Share | buttons bit 0 |
| D-pad left / up / right / down | Left / Up / Right / Down | buttons bits 7 / 4 / 5 / 6 |
| L, R | L1, R1 | buttons bits 10, 11 |
| ZL, ZR | L2, R2 | buttons bits 8, 9 |
| SL, SR (either Joy-Con) | L2, R2 | buttons bits 8, 9 (shared with ZL, ZR) |
| Left stick press, right stick press | L3, R3 | buttons bits 1, 2 |
| Home | home | byte 38 |
| Capture | touch hard press | byte 39 |

Sticks: left stick in bytes 40-41, right stick in bytes 42-43, read as `(value - 127) / 127`.

### Joy-Con to DSU mapping

Each Joy-Con is one slot. A slot sends:

- **Buttons:** every physical button of that Joy-Con on the DSU input the table above assigns
  to its Switch name, so the emulator's default mapping works without manual remapping. SL and
  SR are sent as L2 and R2 because the reference client offers no separate inputs for them.
- **Stick:** a left Joy-Con fills the left stick bytes, a right Joy-Con the right stick bytes;
  the other pair stays at 127. The stick's up direction and byte range are confirmed against
  the reference client on a real device.
- **Motion:** the Joy-Con's own gyroscope and accelerometer as delivered by Android, converted
  to the wire units and axes. The axis order and signs for each Joy-Con side are one row of the
  mapping table and are confirmed on a real device against the reference client.
- **Battery:** from Android's battery state for that device, mapped to the DSU battery values;
  0 when Android does not report it.

Analog button bytes mirror the digital state (255 pressed, 0 released). Touch points are always
inactive.

The Android key codes come from the phone's key layout for the Nintendo controllers
(`Vendor_057e_Product_2006.kl` / `_2007.kl`, kernel `hid-nintendo` button codes). The table in
`JoyConMapping`:

| Joy-Con | Android key code | DSU input |
|---|---|---|
| left | `DPAD_UP` / `DOWN` / `LEFT` / `RIGHT` | Up / Down / Left / Right |
| left | `BUTTON_L1` (L) | L1 |
| left | `BUTTON_L2` (ZL) | L2 |
| left | `BUTTON_R1` (SL), `BUTTON_R2` (SR) | L2, R2 |
| left | `BUTTON_SELECT` (Minus) | Share |
| left | `BUTTON_THUMBL` (stick press) | L3 |
| left | `BUTTON_MODE` (Capture) | touch hard press |
| right | `BUTTON_B` (A), `BUTTON_A` (B), `BUTTON_Y` (X), `BUTTON_X` (Y) | Circle, Cross, Triangle, Square |
| right | `BUTTON_R1` (R) | R1 |
| right | `BUTTON_R2` (ZR) | R2 |
| right | `BUTTON_L1` (SL), `BUTTON_L2` (SR) | L2, R2 |
| right | `BUTTON_START` (Plus) | Options |
| right | `BUTTON_THUMBR` (stick press) | R3 |
| right | `HOME` | home |

Sticks: the left Joy-Con reports `AXIS_X` / `AXIS_Y`, the right one `AXIS_Z` / `AXIS_RZ`, each
-1..1 with Android's down as positive Y. The byte is `round(127 + v * 127)` for X and
`round(127 - v * 127)` for Y, clamped to 0-254, so pushing up raises the byte.

Motion: accelerometer m/s² divided by standard gravity, gyroscope rad/s times 180/π, with one
axis order and sign set per the table in `JoyConMapping`. That orientation is an assumption
until it is checked by hand against the reference client.

Sending: one pad data packet per gyroscope sample of a slot, one at once on a button or stick
change, and at least one every 20 ms per connected slot even without new input, so a lost
datagram never leaves a button held on the client. A Joy-Con lying perfectly still produces no
motion samples at all (the kernel drops unchanged values), so that steady packet carries the last
known state.

## Interfaces

- One UDP socket, IPv4, port **26760** by default (configurable). No other socket exists in the app.
- The client sends from an ephemeral source port. Responses and the pad-data stream go to the
  exact source address and port of the request.
- Version request: one version response.
- Port info request: one port info response **per requested slot**.
- Pad data request: registers that client endpoint as a subscriber, for all slots (flags 0), one
  slot (flags 1) or the slot with that id (flags 2). The server then sends pad data for the
  subscribed slots that have a Joy-Con, without further requests.
- A subscription expires **5 seconds** after its last pad data request (the client renews every 3).

## Rules and invariants

1. Packet sizes, offsets, endianness and the CRC rule in the tables above are exact. A change to
   the codec needs a golden-packet test that still passes byte for byte.
2. The packet counter of a slot increases by one on every pad data packet. The client marks the
   pad disconnected when it sees the same counter twice.
3. Units on the wire are fixed: accelerometer in g, gyroscope in degrees per second. Android
   delivers m/s² and rad/s; the conversion happens once, in the mapping. The motion timestamp
   is the phone's monotonic clock at the moment the sample arrives, in microseconds, and never
   goes backwards within a session. The sensor event's own timestamp is not used: on the
   reference device it runs at about twice real time (see the architecture).
4. The conversion from a Joy-Con's Android axes, key codes and stick axes to the DSU fields
   lives in **one** place (the mapping table, one row per Joy-Con side). Nothing else in the
   code flips an axis or renames a button.
5. Approved clients only: a datagram whose source IP is not approved never gets a response of
   any kind. If, and only if, it is a well-formed DSU request, its address may be shown to the
   user as a pending client; the pending list is bounded.
6. Incoming packets are validated before use: minimum size, `DSUC` magic, protocol version
   `1001`, declared length within the datagram, known message type and CRC. Slot numbers are
   range-checked (0-3) and `pad count` is clamped to 4.
7. The server never sends a datagram to an endpoint that did not send a valid request within the
   last 5 seconds, and never sends anything except the three responses above.
8. A slot keeps its Joy-Con for as long as that Joy-Con stays connected; a reconnecting Joy-Con
   returns to its previous slot when it is still free. An empty slot reports state 0 and sends
   no pad data.
9. Unused inputs are sent neutral: buttons 0, sticks 127, touch inactive. When a Joy-Con
   disconnects, its slot stops sending; it never repeats the last pressed state.

## Errors

The protocol has no error messages. Every invalid case is handled by silence:

| Case | Behaviour |
|---|---|
| Source IP not approved | no response; a well-formed request makes it a pending client |
| Too short, wrong magic, wrong version, bad length, unknown type, bad CRC | drop, no response |
| Request for a slot outside 0-3 | ignore that slot |
| Request for an empty slot | port info with state 0 (disconnected); no pad data stream |
| Joy-Con connected but Android delivers no motion for it | slot connected with buttons and stick only, motion zero; the app shows it per slot |
| Joy-Con disconnects | slot reported disconnected, its stream stops |
| Socket cannot bind (port in use) | server does not start; the app shows the error |
