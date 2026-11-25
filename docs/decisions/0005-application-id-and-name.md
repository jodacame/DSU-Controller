---
type: decision
kit: 2
title: Application id and name
status: active
summary: The application id is dev.jodacame.dsucontroller and the visible name is "DSU Controller".
date: 2026-11-25
deciders: maintainers
supersedes:
superseded_by:
tags: android, release
---

# 0005. Application id and name

## Context

The application id identifies the app on a device and is tied to its signing key: changing it
later means a different app that cannot update the installed one.

## Decision

- Application id: `dev.jodacame.dsucontroller`.
- Visible name: **DSU Controller**.

## Consequences

- The signing keystore is stored under the same id.
- The Kotlin package root is `dev.jodacame.dsucontroller`.
