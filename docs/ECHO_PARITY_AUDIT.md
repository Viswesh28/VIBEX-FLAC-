# VIBEX / Echo Music screen audit and implementation record

## Reference and scope
Echo Music current `main` inspected at `bd4275632e41654a43b99eb2535a1cef6808fa6b`. GitHub latest release on inspection: v1.4.6 (2026-11-09), not assumed identical to current source. Read `AGENT.md`, `DESIGN.md`, README, `Screenshots/`, `HomeScreen.kt`, `FloatingNavBar.kt`, `floatingtabbar/FloatingTabBar.kt`, `player/FloatingMiniPlayer.kt` and screen/component inventory. `AGENT.md` requests Nothing-style minimal high contrast; `DESIGN.md` describes dynamic colors and optional liquid glass. Source explicitly describes floating nav as an alternative with optional glass; its mini-player is a docked accessory with 44dp artwork when expanded. Screenshots show light examples, not proof of a single mandatory dark layout. GPL reference is not copied into VIBEX; UI adaptation is independently written.

VIBEX v23 APK has React/Vite `assets/public`, Capacitor plugins and Media3 service; package `dev.viswesh.vibex.experimental`. The project has no git history. `App.jsx` owns navigation and player state, `Model.jsx` native/browser operations, `ui.jsx` common items/sheets, screen JSX and `theme.css` define UI. Existing separate player sheet is a functional baseline. Source inventory spans about 9,590 lines in `src/v2`; Android plugin/service source and manifest were inspected as boundaries, not rewritten.

## Screen-by-screen audit before this work
| Screen | Existing | Difference / implementation risk |
| --- | --- | --- |
| Shell/nav | Four functional tabs, full-width bar; safe-area offsets | Echo supports floating compact tabs; replace layout without losing accessible names or downloaded destination. |
| Mini-player | 43px art, play/next, progress | Colorful strip and separate bar; make coordinated compact floating surfaces while retaining actions. |
| Home | mood chips, track artwork rail, list, recent/album shelves, decorative mix | Echo hero and dense shelves; reduce decorative prominence but keep functionality and real data. |
| Search | real search, recent searches and category filters | Compact field, chips and results; no fabricated suggestions. |
| Library | actual liked/recent/local/playlists and sorting | Compact supported shortcuts and real lists only; albums/artists are not current library categories. |
| Player | Full-art background with controls, lyrics/queue sheet | User selected full-art; retain this and other controls rather than replacing with fictional Echo behavior. |
| Lyrics | timed lines and real supplied word timestamps | Refine type and contrast; preserve position/seek/provider logic. |
| Collection details | cover, metadata, real play/download and tracks | Align typography/artwork/list treatment, don't add fake shuffle. |
| Downloads | actual native download states and actions | Grouped list, compact information and controls. |
| Settings | real toggles and sections | Neutral grouped rows, keep settings, explicit theme preference. |
| Dialogs/sheets | accessible modal and existing action buttons | Consistent neutral surfaces, preserve focus/back/outside behavior. |

## Known validation limitations at audit
No physical Android device attached. No Android emulator, system image, AVD or `/dev/kvm` is present; software-emulator setup may not run in the 2 GB sandbox. We will not describe Chromium fixtures as Android screenshots or claim exact parity. Reference current source and latest release are separate versions; no release APK runtime comparison has yet been performed.

## Implementation and verification (v24)
- `src/v2/echo-parity.css` adds shared neutral light/dark tokens and styling for shell, Home, search, library, details, downloads, settings, lyrics, player and sheets. `App.jsx` retains four real destinations in a compact accessible floating nav. Mini-player actions remain. Home removes the decorative promotional panels and keeps working mix/stats buttons. The full-art player and separate lyrics/queue sheet remain as chosen.
- `core.js` sets dark for new installations. `storage.js` fixes the independent legacy-migration fallback so a fresh install really is dark while saved modern settings and explicit `svTheme=light` remain light. Tests cover dark and explicit light.
- Before/after **Chromium fixture**, not Android, comparisons are in `docs/ui-parity/comparisons/` for 360×800, 393×873 and 412×915; each has Home, Search, Library, Downloads, Settings, player, and lyrics. Fixture art, song names, network responses and missing audio are synthetic; screenshots are visual checks, not native playback proof. The baseline reflects v23 fresh-install light, while v24 fresh-install dark. Existing persisted light remains light.
- Node 69/69; Playwright 31/31; Vite/Capacitor sync and `:app:assembleDebug` succeeded. v24 APK verified v1/v2 debug signature; SHA-256 a5c7180392df5aab9156342922e226aaa260147fdb1ae92f4a76657a44e71e4c. This is an experimental test build, not a production release.
- Installed emulator and API 35 Google APIs x86_64 image, created an AVD, and tried software rendering and `-accel off`; emulator exited `Insufficient RAM free for launching emulator`, automatically requiring 2560 MB. `/dev/kvm` unavailable and no device attached. Required Android before/after comparisons and real WebView gesture/insets/keyboard/playback validation remain incomplete. No claim of full Echo parity is justified.
- Echo floating nav collapses on scroll and can position the accessory inline; VIBEX uses a fixed expanded nav plus separate mini-player. Search is not a separate circular dock. Screen details are not exact reproductions, Material/Apple player templates have not been reproduced, and Echo release APK was not run side by side. Some existing explicit saved light preferences remain light by design. Native Media3, downloads, sharing and audio integrations were not altered or device-tested in this iteration.
