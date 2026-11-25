---
type: decision
kit: 2
title: JUnit for tests only
status: active
summary: JUnit is the only third-party code in the project, used for unit tests on the build machine and never packaged in the APK.
date: 2026-11-25
deciders: maintainers
supersedes:
superseded_by:
tags: testing, dependencies
---

# 0003. JUnit for tests only

## Context

The byte-exact DSU codec, the server's client handling and the Joy-Con mapping tables are
protected by unit tests. The project otherwise uses no third-party code (decision 0002).

## Decision

JUnit is allowed as a `testImplementation` dependency. It runs on the build machine only and is
never part of the APK.

Alternatives ruled out:

- **An own minimal test runner:** more code to maintain for the same result, and unfamiliar to
  contributors.

## Consequences

- Tests run with the standard `./gradlew testDebugUnitTest`.
- The rule "no libraries in the APK" is unaffected.
