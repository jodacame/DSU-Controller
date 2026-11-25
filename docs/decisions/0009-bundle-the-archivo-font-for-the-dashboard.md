---
type: decision
kit: 2
title: Bundle the Archivo font for the dashboard
status: active
summary: The app ships the Archivo variable font (SIL Open Font License) as an asset so the dashboard matches the approved colour-block design; a font is a resource, not a library.
date: 2026-11-25
deciders: maintainers
supersedes:
superseded_by:
tags: ui, typography, licensing
---

# 0009. Bundle the Archivo font for the dashboard

## Context

The approved dashboard design (colour blocks) is set in Archivo: the player label in its
heaviest, most condensed cut, titles and status lines in its bold and semibold weights. The
first build used the phone's system faces instead, because the dashboard contract asked for
system typefaces only. On the phone the result did not look like the approved design: the
player label lost its shape and the text lost its rhythm. The maintainers asked for the
approved typeface.

Android has no condensed black system face that matches it, and the system faces differ
between phone makers.

## Decision

Ship the Archivo variable font (`wdth` 62-125, `wght` 100-900) in `app/src/main/assets/fonts/`,
with its SIL Open Font License next to it (`Archivo-OFL.txt`, packed into the APK as the license
asks). The app loads it with the framework's `Typeface.Builder` and picks each cut through font
variation settings. No font library, no downloadable fonts, no network.

A font file is a resource, not code: decision 0002 (no libraries) and the "own code only" rule
still hold. The OFL allows bundling with software under any license, including MIT, as long as
the font is not sold on its own and its license travels with it.

## Consequences

- The dashboard looks the same on every phone and matches the approved design.
- The APK grows by about 650 KB.
- The font's license file must stay next to the font; replacing or subsetting the font needs a
  check that the OFL terms (reserved font name) are kept.
- Other screens (settings, diagnostics) may keep the system face; the dashboard contract says
  which text uses Archivo.
