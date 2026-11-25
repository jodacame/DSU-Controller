---
type: decision
kit: 2
title: No libraries in the APK
status: active
summary: The APK contains only the Android framework APIs and the Kotlin standard library - no AndroidX, Compose, coroutines or any third-party SDK.
date: 2026-11-25
deciders: maintainers
supersedes:
superseded_by:
tags: dependencies, security
---

# 0002. No libraries in the APK

## Context

The app handles controller input and opens a network socket on the user's phone. Users should
be able to audit all of it. The UI is two screens.

## Decision

The app ships no library code: only the Android framework and the Kotlin standard library. That
excludes AndroidX, Jetpack Compose, `kotlinx.coroutines`, and every analytics, advertising,
crash-reporting or networking SDK. The UI uses framework Views; concurrency uses plain threads.

Alternatives ruled out:

- **AndroidX core/appcompat:** more convenient, but adds code to the APK that the project does
  not own, for little gain on two screens.
- **Jetpack Compose:** faster to write, but brings several libraries and a much larger APK.

## Consequences

- The whole shipped code can be read in one sitting, and the APK stays small.
- Some conveniences are written by hand (state observation, view updates).
- A proposal to add a dependency is a change to this decision, not a routine edit.
