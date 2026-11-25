---
type: decision
kit: 2
title: Unsigned Gradle build, signed outside the repo
status: active
summary: The project is a plain Gradle wrapper build that produces an unsigned release APK; signing happens afterwards with a keystore that never enters the repository.
date: 2026-11-25
deciders: maintainers
supersedes:
superseded_by:
tags: build, signing, security
---

# 0004. Unsigned Gradle build, signed outside the repo

## Context

The project needs a build that anyone can reproduce and release APKs signed with a stable key.
A signing key is irreplaceable: an APK signed with a different key cannot update an installed
app. A public repository must never contain that key, its password, or a path that reveals
where it lives.

## Decision

- The project builds with the Gradle wrapper and a standard toolchain (JDK 21, Android SDK
  platform 36). It needs no custom plugin, script or environment.
- `assembleRelease` produces an **unsigned** APK. The Gradle build contains no release signing
  configuration at all.
- Signing is a separate step with `apksigner` and a keystore kept outside the repository.
  Whoever publishes a build uses their own keystore.

Alternatives ruled out:

- **A release `signingConfig` fed from a git-ignored properties file:** works, but keeps the key
  and its password as plain files next to the source and makes it easy to commit them by
  mistake.
- **Signing with the debug key:** not a stable key; a build from another machine could not
  update the installed app.

## Consequences

- Building and signing are two commands, documented in the README.
- No secret can leak through the build files.
- A first build needs network access to Google Maven and Maven Central for the Gradle plugins;
  nothing from them ships in the APK.
- Built APKs are never committed.
