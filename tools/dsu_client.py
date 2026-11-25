#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""DSU (cemuhook) test client.

Asks a DSU server for controller data the same way an emulator does and prints what arrives,
checking every packet against docs/contracts/dsu-protocol.md. Standard library only. It shares
no code with the app on purpose: it is an independent check of the wire format.

    python3 tools/dsu_client.py 192.0.2.10
    python3 tools/dsu_client.py 192.0.2.10 --port 26760 --slot 0 --seconds 10

The phone must have approved this machine's address first (the app asks on screen).
"""

from __future__ import annotations

import argparse
import random
import socket
import struct
import sys
import time
import zlib
from dataclasses import dataclass, field

MAGIC_CLIENT = b"DSUC"
MAGIC_SERVER = b"DSUS"
PROTOCOL_VERSION = 1001
HEADER = struct.Struct("<4sHHIII")  # magic, version, payload length, crc, id, type
TYPE_VERSION = 0x100000
TYPE_PORT_INFO = 0x100001
TYPE_PAD_DATA = 0x100002
EXPECTED_SIZE = {TYPE_VERSION: 22, TYPE_PORT_INFO: 32, TYPE_PAD_DATA: 100}
SLOT_BLOCK = struct.Struct("<BBBB6sBB")  # slot, state, model, connection, mac, battery, last
PAD_BODY = struct.Struct("<IHBB4B12B12xQ3f3f")
SLOT_COUNT = 4
RENEW_SECONDS = 3.0
BUTTON_NAMES = [
    "Share", "L3", "R3", "Options", "Up", "Right", "Down", "Left",
    "L2", "R2", "L1", "R1", "Triangle", "Circle", "Cross", "Square",
]
BATTERY_NAMES = {
    0x00: "none", 0x01: "dying", 0x02: "low", 0x03: "medium", 0x04: "high", 0x05: "full",
    0xEE: "charging", 0xEF: "charged",
}


def build_request(client_id: int, message_type: int, body: bytes) -> bytes:
    """Returns a complete request packet with its CRC filled in."""
    packet = bytearray(
        HEADER.pack(MAGIC_CLIENT, PROTOCOL_VERSION, len(body) + 4, 0, client_id, message_type)
        + body
    )
    struct.pack_into("<I", packet, 8, zlib.crc32(packet))
    return bytes(packet)


def version_request(client_id: int) -> bytes:
    return build_request(client_id, TYPE_VERSION, b"")


def port_info_request(client_id: int, slots: list[int]) -> bytes:
    padded = bytes(slots) + bytes(SLOT_COUNT - len(slots))
    return build_request(client_id, TYPE_PORT_INFO, struct.pack("<I", len(slots)) + padded)


def pad_data_request(client_id: int, flags: int = 0, slot: int = 0, mac: bytes = bytes(6)) -> bytes:
    return build_request(client_id, TYPE_PAD_DATA, struct.pack("<BB6s", flags, slot, mac))


class PacketError(ValueError):
    """A received datagram does not follow the contract."""


def parse_response(packet: bytes) -> tuple[int, bytes]:
    """Validates a server packet and returns (message type, bytes after the header)."""
    if len(packet) < HEADER.size:
        raise PacketError(f"too short: {len(packet)} bytes")
    magic, version, payload_length, crc, _server_id, message_type = HEADER.unpack_from(packet)
    if magic != MAGIC_SERVER:
        raise PacketError(f"wrong magic {magic!r}")
    if version != PROTOCOL_VERSION:
        raise PacketError(f"wrong protocol version {version}")
    if message_type not in EXPECTED_SIZE:
        raise PacketError(f"unknown message type {message_type:#x}")
    if len(packet) != EXPECTED_SIZE[message_type]:
        raise PacketError(f"type {message_type:#x} has {len(packet)} bytes")
    if payload_length != len(packet) - 16:
        raise PacketError(f"payload length field says {payload_length}")
    zeroed = bytearray(packet)
    zeroed[8:12] = bytes(4)
    if zlib.crc32(zeroed) != crc:
        raise PacketError("bad CRC")
    return message_type, packet[HEADER.size:]


@dataclass
class SlotView:
    """What the client knows about one slot."""

    connected: bool = False
    mac: str = ""
    battery: str = "none"
    packets: int = 0
    lost: int = 0
    last_counter: int | None = None
    window_start: float = field(default_factory=time.monotonic)
    window_packets: int = 0
    rate: float = 0.0
    line: str = ""

    def count(self, counter: int, now: float) -> None:
        if self.last_counter is not None and counter != self.last_counter + 1:
            self.lost += max(counter - self.last_counter - 1, 0)
        self.last_counter = counter
        self.packets += 1
        self.window_packets += 1
        if now - self.window_start >= 1.0:
            self.rate = self.window_packets / (now - self.window_start)
            self.window_start = now
            self.window_packets = 0


def describe_pad(body: bytes, view: SlotView) -> None:
    (counter, buttons, home, capture, lx, ly, rx, ry, *rest) = PAD_BODY.unpack_from(body, SLOT_BLOCK.size)
    timestamp, ax, ay, az, pitch, yaw, roll = rest[12:]
    view.count(counter, time.monotonic())
    pressed = [name for bit, name in enumerate(BUTTON_NAMES) if buttons & (1 << bit)]
    if home:
        pressed.append("Home")
    if capture:
        pressed.append("Capture")
    view.line = (
        f"{view.rate:6.1f} Hz  accel g ({ax:+.2f} {ay:+.2f} {az:+.2f})  "
        f"gyro deg/s ({pitch:+7.1f} {yaw:+7.1f} {roll:+7.1f})  "
        f"L({lx:3d},{ly:3d}) R({rx:3d},{ry:3d})  "
        f"[{' '.join(pressed) or '-'}]  lost {view.lost}  t={timestamp / 1e6:.3f}s"
    )


def run(host: str, port: int, only_slot: int | None, seconds: float | None) -> int:
    client_id = random.getrandbits(32)
    slots = [SlotView() for _ in range(SLOT_COUNT)]
    errors = 0
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(0.25)
    started = time.monotonic()
    next_renew = started
    next_print = started
    answered = False
    try:
        sock.sendto(version_request(client_id), (host, port))
        while seconds is None or time.monotonic() - started < seconds:
            now = time.monotonic()
            if now >= next_renew:
                sock.sendto(port_info_request(client_id, list(range(SLOT_COUNT))), (host, port))
                sock.sendto(pad_data_request(client_id), (host, port))
                next_renew = now + RENEW_SECONDS
            try:
                packet, _ = sock.recvfrom(256)
            except socket.timeout:
                packet = None
            if packet is not None:
                try:
                    message_type, body = parse_response(packet)
                except PacketError as error:
                    errors += 1
                    print(f"INVALID PACKET: {error}", file=sys.stderr)
                    continue
                answered = True
                if message_type == TYPE_VERSION:
                    print(f"server protocol version {struct.unpack_from('<H', body)[0]}")
                else:
                    slot, state, _model, _conn, mac, battery, _last = SLOT_BLOCK.unpack_from(body)
                    if slot >= SLOT_COUNT:
                        errors += 1
                        print(f"INVALID PACKET: slot {slot}", file=sys.stderr)
                        continue
                    view = slots[slot]
                    view.connected = state == 2
                    view.mac = mac.hex(":")
                    view.battery = BATTERY_NAMES.get(battery, f"{battery:#x}")
                    if message_type == TYPE_PAD_DATA:
                        describe_pad(body, view)
            if now >= next_print:
                next_print = now + 0.5
                if not answered:
                    print("no answer yet (is this machine allowed on the phone?)")
                for index, view in enumerate(slots):
                    if only_slot is not None and index != only_slot:
                        continue
                    if view.connected:
                        print(f"slot {index} {view.mac} battery {view.battery:8s} {view.line}")
                    elif answered:
                        print(f"slot {index} free")
    except KeyboardInterrupt:
        pass
    finally:
        sock.close()
    total = sum(view.packets for view in slots)
    print(f"done: {total} pad packets, {sum(view.lost for view in slots)} lost, {errors} invalid")
    return 0 if answered and errors == 0 else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="DSU (cemuhook) test client")
    parser.add_argument("host", help="IPv4 address of the phone, for example 192.0.2.10")
    parser.add_argument("--port", type=int, default=26760)
    parser.add_argument("--slot", type=int, choices=range(SLOT_COUNT), help="print only this slot")
    parser.add_argument("--seconds", type=float, help="stop after this many seconds")
    args = parser.parse_args()
    return run(args.host, args.port, args.slot, args.seconds)


if __name__ == "__main__":
    sys.exit(main())
