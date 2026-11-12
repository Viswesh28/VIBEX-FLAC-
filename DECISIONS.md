# Decisions

- Use VIBEX 2.0-test7 source commit b048c1c as the selected baseline; keep its existing UI and interactions unchanged unless the user approves a necessary change.
- Retain and assess the existing Media3 player and MediaSessionService rather than replacing working features wholesale. Kotlin is being evaluated for an independent lossless adapter.
- Treat GPL-licensed reference projects as behavioral references only; do not copy their implementation into the distributed app without a licensing review.
- No VIBEX backend, Android localhost server, account requirement, credentials, analytics, or production mock stream data.
- Experimental cache and provider integration is not approved for release until offline/download behavior, recording identity, format evidence and fallback are tested. A compiled debug APK is not a playback or device-verification result.
- User approved the necessary UI change to add a FLAC-preferred setting and a playback format indicator. The indicator reflects Media3's active selected audio format, not a claim of decoder or lossless-output verification.
- Canvas: Echo Music and SimpMusic GPL-3.0 projects are architecture references only; no implementation copied. Implement independent static-fallback and isolated Media3 scaffolding. Leave Apple, Spotify and additional adapters disabled until an authorized, documented animated-media contract and rights are verified; Spotify login alone is not enough. Do not use full music videos as Canvas.

### 2026-11-11 — experimental share and interactions
Use the existing sheets/providers and a separate scoped temporary FileProvider for generated PNG share cards. No provider lyric is distributed on cards without rights and attribution; no dedicated Snapchat integration claimed. Collection actions and full input/device coverage remain incomplete; no new dependencies or backend.

### 2026-11-11 — Echo-inspired VIBEX layout
Use independent React/CSS adaptations of publicly visible layout patterns, retain VIBEX colors, identity, real data and existing features; do not copy GPL-3.0 implementation or artwork. Exact player and all-screen parity is not claimed.

### Version 21 UI preview
Package independently built Echo-inspired layout in the existing Capacitor/Media3 app; preserve current audio paths, settings and colors. Keep existing functionality even where this prevents an exact screen clone; do not infer on-device performance or scanner status from a successful debug build.

### Player swipe, version 22
Prevent unintended horizontal player scrolling instead of intercepting all horizontal touch events; preserve slider gestures, vertical scrolling, back handling and existing colors. Wrap overflowing player controls and bound the oversized Aurora visual layer. Android physical-gesture behavior still needs device verification.

### Full-art player, version 23
Use actual track cover as the full-page player backdrop with playback controls over a contrast scrim. Move lyrics and queue into an existing accessible sheet rather than showing a permanent partition. Retain the two player style settings, audio-quality selector, stream-meter and all playback features. Do not substitute or claim video/Canvas artwork.
