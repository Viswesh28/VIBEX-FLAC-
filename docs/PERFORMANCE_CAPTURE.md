# VIBEX baseline performance capture

## Baseline
Use the pre-Canvas preview `VIBEX-codec-network-preview.apk` (package `dev.viswesh.vibex.experimental`, versionCode 17, SHA-256 `93cebbf5d034e874c716298e1063598915d06be0c211266ea502beb327c7ff3d`). It installs separately from the original test7 app. The separate performance working copy removes later Canvas work; its unmodified Vite web assets matched this baseline byte-for-byte before readability edits. Canvas is not part of this baseline.

## What to send
1. Phone model, Android version, display refresh rate, whether battery saver was enabled, and whether the device was on Wi-Fi or mobile data.
2. A system trace of the baseline app performing the scenarios below. Android's traces can contain app names and activity/process details; review before sharing and do not include sensitive content.
3. If possible, `gfxinfo` frame statistics and memory snapshots (optional computer steps below).
4. Which actions felt slow and approximate timestamps within the trace.

## Capture on the phone, without a computer
1. Install the baseline APK from this conversation. Leave security scanning enabled. If Android displays a security warning, stop and share the exact warning rather than bypassing it.
2. Turn on Developer options if not already enabled: Settings → About phone → tap Build number seven times. Names vary by manufacturer.
3. Open Developer options → System tracing. If System tracing is not listed, add its tile to Quick Settings or use a computer/Android Studio instead. Select the available graphics, view, input, scheduling, frequency, memory and WebView categories; leave defaults if unsure. Do not enable unnecessary sensitive app logging.
4. Start a trace, return to VIBEX, and perform the scenario for about 45–90 seconds. Stop the recording using the notification or tracing tile. Save/share the resulting `.perfetto-trace` or `.ctrace` file and upload it here. Repeat separately for the following, if practical:
   - Home scrolling, search typing/results, library scrolling, and theme switching.
   - Start a song, open the full player, use controls, rapidly skip several tracks, and scroll a large queue. Note which track, network, and displayed actual codec; FLAC is not assumed from the preference alone.
   - Playback while navigating, then background and foreground the app. Repeat with data saver enabled and note which recording is which.
5. The baseline has no enabled Canvas provider. Canvas and FLAC-plus-Canvas cannot be measured on this APK and require a later authorized functioning Canvas build.

## Optional: collect frame stats using a computer
Install Android SDK Platform Tools from the official Android developer site. Connect the phone, enable USB debugging only if you are comfortable, and approve the computer's key. In a terminal run:

```sh
adb devices -l
adb shell dumpsys gfxinfo dev.viswesh.vibex.experimental reset
# Now perform one repeatable scenario in VIBEX for 45–90 seconds.
adb shell dumpsys gfxinfo dev.viswesh.vibex.experimental framestats > vibex-baseline-framestats.txt
adb shell dumpsys meminfo dev.viswesh.vibex.experimental > vibex-baseline-meminfo.txt
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
```

Upload the text files with the trace. `gfxinfo` support varies by Android/WebView version and may not reveal all WebView rendering; Perfetto is preferred for UI/RenderThread and scheduling analysis. For trustworthy comparisons, repeat the same scenario on the same device, refresh rate, network and power settings, with comparable warm/cold cache conditions. Record before and after separately. We will calculate frame distributions only where the trace has usable frame timing data; no 60 FPS claim will be made without it.

## Current inspection, not measurements
APK inspection confirms the requested blur values (22px and 26px backdrop filters, 56px background blur, 5px modal backdrop) and CSS declarations using 6px, 8px and 9px fonts. Their impact on the phone is unknown until profiled. The React source has 250ms catalog request scheduling, a visible-only playback snapshot poll (normally 1s), and lazy/IntersectionObserver artwork handling. These are areas to inspect in trace/profiling, not established root causes. The current JavaScript unit tests passed 67/67, but they do not measure Android frame timing, audio or touch latency.

## Analyze returned gfxinfo
From the project directory, run `python3 tools/gfxinfo_stats.py path/to/vibex-baseline-framestats.txt --hz 60` using the actual display refresh rate. This reports `FrameCompleted − IntendedVsync` only, and refuses empty or unusable inputs; compare with Perfetto presentation/frame data when available.
