# VIBEX source and test APK

The latest experimental Android test APK is `releases/VIBEX-settings-update-v26.apk`. It uses application ID `dev.viswesh.vibex.experimental` and is a debug build, not a store release. The artwork fix adds a visible cover and a dark fallback, and v26 refreshes the Settings about text; the build has not undergone a full physical-device regression.

## Build
Install Node.js, JDK 21, Android SDK/platform 35 and the required build tools. From `music-app`, run `npm ci`, `npm run build`, `npx cap sync android`, then `cd android && bash gradlew :app:assembleDebug`. `jiosaavn-api` is a local dependency and must remain alongside `music-app`. Run `npm test` and `npx playwright test` in `music-app` for tests; browser tests also require Playwright Chromium and system dependencies.

The existing APK was signed with a test-only key which is deliberately **not** in this public repository. If `music-app/android/vibex-testing.keystore` is absent, Android uses the normal local debug key. An APK built with a different key cannot update the existing installed APK; uninstalling the existing app to install it will remove app-local data, so back up first. Do not use debug/test signing for a production release.

The APK is accessible to anyone because this repository is public. Android screenshots were provided by the user; native playback and device compatibility should be checked on a phone before relying on them.
