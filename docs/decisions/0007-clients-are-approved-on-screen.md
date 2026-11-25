---
type: decision
kit: 2
title: Clients are approved on screen
status: active
summary: Nobody is answered by default: a machine that asks for data appears on screen with an Allow button, and only approved addresses get responses.
date: 2026-11-25
deciders: maintainers
supersedes:
superseded_by:
tags: security, network, ui
---

# 0007. Clients are approved on screen

## Context

The DSU protocol has no authentication. Any machine on the network that sends a request to the
port would receive the controllers' motion and button presses. The app is meant for the public,
so it cannot ship with any address already trusted.

## Decision

- The list of approved clients is **empty** on a fresh install, and the server answers approved
  addresses only.
- When an unknown machine sends a well-formed DSU request, its address appears on screen as a
  pending client with an **Allow** button. Allowing stores it permanently.
- An address can also be typed in by hand, and approved clients can be removed.

Alternatives ruled out:

- **Manual entry only:** safe, but the user has to find the emulator machine's address first.
- **Allow the whole local network by default:** works with no setup, but lets any device on the
  Wi-Fi read the controllers.

## Consequences

- First use needs one tap after the emulator starts asking.
- An unapproved address never receives any datagram, so the phone cannot be discovered by
  scanning the port.
- The pending list is bounded and fed only by valid requests, so network noise cannot flood it.
