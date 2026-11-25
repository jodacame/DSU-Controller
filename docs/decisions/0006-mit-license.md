---
type: decision
kit: 2
title: MIT license
status: active
summary: The code is published under the MIT license, with an SPDX identifier at the top of every source file.
date: 2026-11-25
deciders: maintainers
supersedes:
superseded_by:
tags: license, open-source
---

# 0006. MIT license

## Context

The repository starts private and is meant to be published as open source. The code is written
from scratch for this project; the DSU wire format is read from emulator source code, but no
code is copied from it.

## Decision

The project is licensed under **MIT**. The repository carries a `LICENSE` file and every source
file starts with `SPDX-License-Identifier: MIT`.

Alternatives ruled out:

- **Apache 2.0:** equally permissive with an explicit patent grant, at the cost of more legal
  text; not needed here.
- **GPL v3:** the license of the emulators this app talks to. It would force derived apps to
  publish their source, which this project does not require.

## Consequences

- Anyone may use the code, including in closed apps, keeping the copyright notice.
- Code from GPL projects (emulators, other DSU tools) must never be copied into this repository.
