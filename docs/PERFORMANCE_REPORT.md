# VIBEX performance audit — initial work, real-device measurements pending

## Scope and baseline
The user selected the build immediately before Canvas: `VIBEX-codec-network-preview.apk`, application ID `dev.viswesh.vibex.experimental`, versionCode 17, SHA-256 `93cebbf5d034e874c716298e1063598915d06be0c211266ea502beb327c7ff3d`. A separate working copy was reconstructed without the later Canvas plugin and bridge. Before the readability edits, all Vite-generated web assets and index HTML that could be compared matched those packaged in this APK byte-for-byte. Cordova's generated stub files are added by Capacitor sync and were not in the raw Vite output. Five of six classes DEX files in the final build are byte-identical to baseline; the sixth has identical `dexdump -d` disassembly after the filenames in the output, but differs in raw bytes. This is strong evidence of source alignment, not a claim that the entire rebuilt APK is byte-identical.

## Root cause
**Not established.** No real Android device was attached. Neither Android Studio Profiler nor Perfetto device traces, GPU overdraw, render/UI thread timing, WebView long tasks, GC pauses, audio/FLAC interaction, nor real-device frame times were collected. A local browser test cannot determine Android jank. No 60-FPS, frame-time, or before/after speed improvement is claimed. Follow `PERFORMANCE_CAPTURE.md` to provide the baseline trace.

Static APK inspection found the described 22px top-bar backdrop blur, 56px aurora blur, 26px player backdrop blur, and 5px modal blur. These effects remain unchanged until device evidence identifies their cost and a suitable device-specific policy. Source also has visible-only playback snapshots, search request scheduling, and existing IntersectionObserver/lazy artwork. Those are profiling candidates, not proven causes.

## Changes completed
- Increased mobile hero text, descriptions, artwork caption, track metadata, and bottom navigation labels, while preserving the existing palette and components.
- Raised track action hit areas to 44 × 44 CSS pixels on mobile and added pressed-state styling.
- Reconciled one legacy browser regression assertion that expected three audio sources, although the selected pre-Canvas baseline already has four including FLAC. The revised test verifies the four options and FLAC's presence.
- Added repeatable desktop browser measurements and a tested `tools/gfxinfo_stats.py` parser for user-provided Android `dumpsys gfxinfo … framestats`. It reports median/p95 `FrameCompleted − IntendedVsync` only if valid rows are supplied; that is not identical to display-present timing. No real inputs have yet been supplied.
- No speculative React, provider, audio, caching, virtualization, or blur performance modification was made before device profiling. No feature was intentionally removed.

## Measured desktop browser comparison
Local headless Chromium, 320 × 800 CSS viewport, test-only catalog fixtures. The browser is not Android WebView, and the values are CSS/DOM measurements, not frame-time measurements:

| Item | Baseline | Preview |
|---|---:|---:|
| Hero tag font | 5.5 px | 9 px |
| Hero description font | 8 px | 11 px |
| Artwork caption label | 5 px | 9 px |
| Track metadata | 9 px | 11 px |
| Bottom navigation label | 8 px | 11 px |
| Track action hit area | 34 × 44 px | 44 × 44 px |
| Document width | 320 px | 320 px |

At 360, 393 and 412 CSS pixels the measured document width also matched the viewport; automated browser checks confirmed the hero action remains inside its card. Raw machine-readable results are in `mobile-metrics-before.jsonl` and `mobile-metrics-after.jsonl`. Text metrics and overall appearance must still be checked on actual Android devices, including large system font scale, RTL and narrow-screen accessibility.

## Tests and build
- Node/JavaScript unit suite: **66 passed**.
- Headless Chromium regression suite: **28 passed**, using explicitly test-only fixtures and a generated test WAV; these tests do not establish Android FLAC, network, or lifecycle behavior.
- Android JVM suite: **45 passed, 2 opt-in live tests skipped**. No Android instrumented or physical-device tests performed.
- gfxinfo parser: **2 synthetic tests passed**; no device data yet.
- Android debug APK built and v2 signature verifies. Preview: `releases/VIBEX-readability-preview.apk`, package `dev.viswesh.vibex.experimental`, versionCode 19, SHA-256 `1427c91992b35e606ecdea922836f1221d29c0597497c310bd24bfd6e0824c01`. Certificate fingerprint matches the earlier experimental build, so it is intended as an update to that package; actual installation/update has not been tested. Debuggable with the repository's public testing key. A valid signature is not a malware scan or security approval.

## Remaining work / limitations
1. Capture baseline Perfetto/System Tracing and gfxinfo on an actual Android device with model, refresh rate, OS, power and network conditions. Identify measured bottlenecks before changing filters, React subscriptions, request cancellation, list rendering or artwork decode behavior.
2. Collect an equivalent after trace on the same device and workload. Report median and p95 from supported timing data, plus drop bursts and interaction latency; distinguish cache states and refresh rates.
3. Profile WebView JS/React, main and render threads, GPU overdraw, image decoding/GC, audio decoder and provider contention. Implement and test only fixes supported by those observations.
4. Verify Home/search/library/large queue, skip, FLAC, theme, data saver and foreground/background on device, including typography/touch overlap and offline behavior. Canvas is intentionally deferred and is not present in the selected baseline; Canvas/FLAC coexistence cannot be verified here.
5. Review dependency vulnerabilities, existing codec/download security and the public debug signing identity before any production distribution. The installation security-scan issue reported earlier remains undiagnosed without its exact warning/device details.
