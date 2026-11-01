# RevelaCode Android

Native Android delivery repository for the RevelaCode ecosystem.

This repository owns the **Android application layer only**: Capacitor Android, Gradle, Kotlin/Java native extensions, Android permissions, AdMob manifest setup, app signing, APK/AAB packaging, and Android device/emulator verification.

The React/Vite application remains the single UI source of truth in [RevelaCode-Frontend](https://github.com/musombi123/RevelaCode-Frontend). This repository checks out the selected frontend revision, builds its production `dist/`, copies that web bundle into the Capacitor `www/` directory, and packages it as an Android application. It does not duplicate the React source or create a second backend.

## Architecture

```text
RevelaCode-Frontend
  React + Vite + shared ecosystem UI
          |
          | selected Git ref / production dist
          v
RevelaCode-Android
  Capacitor + native Android project + Gradle
  permissions + AdMob + signing + APK/AAB
          |
          v
Existing RevelaCode services
  RevelaCode Backend / RevelaAI / Jumuiya services
```

## Stable application identity

- Android application ID: `com.revelacode.app`
- App name: `RevelaCode`
- Capacitor: 8.x
- Node.js: 22
- Java: 21
- Minimum Android SDK: 24
- Target / compile SDK: 36

The Android application ID is intentionally preserved. Do not change it for routine UI, branding, or release work.

## Build and verification

The `Android build and launch verification` workflow supports:

- `repository_dispatch` event `frontend-updated` from the frontend repository.
- Manual `workflow_dispatch` with a frontend ref, version name, version code, and debug/release selection.
- Changes to Android native source, Capacitor configuration, scripts, or workflow files.

Each run:

1. Checks out the Android repository and the requested frontend revision.
2. Installs the frontend's locked dependencies and runs its production Vite build.
3. Copies the production bundle into the Android Capacitor web directory.
4. Installs the Android-only Capacitor dependencies.
5. Creates the Capacitor Android project on the first run, or syncs the checked-in native project on later runs.
6. Generates Android launcher/splash assets from `frontend/public/Logo.png`.
7. Configures permissions, the custom `RevelaPermissions` bridge, AdMob application metadata, versioning, and release resource shrinking.
8. Builds an installable debug APK.
9. Installs and launches the APK on an Android emulator, retaining Logcat diagnostics on failure.
10. Persists the generated native Android project and dependency lockfile in this repository.

The web bundle under `www/` and Capacitor-generated web assets under `android/app/src/main/assets/public/` are build outputs and are intentionally not committed. Native source, Gradle files, manifest changes, app resources, and native extensions are committed.

## Triggering Android builds from the frontend repository

Add a fine-grained GitHub token as the **frontend repository secret**:

- Secret name: `REVELACODE_ANDROID_DISPATCH_TOKEN`
- Repository access: `musombi123/RevelaCode-Android`
- Permission: **Contents: Read and write**

The frontend repository's dispatch workflow uses this token only to send a `frontend-updated` repository-dispatch event containing the exact frontend commit SHA. Until that secret is configured, run the Android workflow manually and select the desired frontend ref.

## Build secrets

Configure these only in the Android repository's GitHub Actions secrets:

| Secret | Purpose |
|---|---|
| `VITE_ADMOB_ANDROID_APP_ID` | Production AdMob Android application ID |
| `VITE_ADMOB_COMMUNITY_NATIVE_AD_UNIT_ID` | Production Community native ad unit ID |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Release key alias |
| `ANDROID_KEY_PASSWORD` | Release key password |

Without both AdMob IDs, the native manifest uses Google's official test app ID; the production frontend ad unit ID is not injected. Without all signing secrets, the workflow produces a debug APK for testing only and does not pretend it is a signed release.

Never commit `.env`, keystores, passwords, signing properties, or production credentials.

## Local build

Prerequisites: Node.js 22, Java 21, Android SDK/platform tools, and the Android SDK platform/build tools for API 36.

```powershell
git clone https://github.com/musombi123/RevelaCode-Android.git
cd RevelaCode-Android

# Install Android-only Capacitor dependencies
npm install

# Check out/build the web source separately
git clone https://github.com/musombi123/RevelaCode-Frontend.git frontend
npm ci --prefix frontend
npm run build --prefix frontend

# Prepare the Capacitor web directory
New-Item -ItemType Directory -Force www | Out-Null
Copy-Item frontend/dist/* www/ -Recurse -Force
New-Item -ItemType Directory -Force brand-assets | Out-Null
Copy-Item frontend/public/Logo.png brand-assets/logo.png -Force

# First build only: create the native project
npx cap add android

# Generate brand resources, sync plugins/assets and configure Android
npx capacitor-assets generate --android --assetPath brand-assets --iconBackgroundColor '#08111F' --iconBackgroundColorDark '#08111F' --splashBackgroundColor '#08111F' --splashBackgroundColorDark '#08111F'
npx cap sync android
node scripts/configure-android-permissions.mjs
node scripts/configure-admob-android.mjs
node scripts/set-android-release-version.mjs
node scripts/optimize-android-release.mjs

# Build and install
cd android
./gradlew assembleDebug
```

On Windows, use `android\\gradlew.bat assembleDebug` from the `android` directory.

## APK size policy

- Build the production Vite bundle, never a development server URL.
- Keep Android-only dependencies in this repository; do not install a second copy of the complete web toolchain here.
- Use R8 code shrinking and Android resource shrinking for signed release builds.
- Prefer the AAB for store distribution so Play can deliver device-specific APK splits.
- Keep generated `www/`, caches, debug outputs, and keystores out of Git.
- Measure and compare release artifacts before claiming a size reduction; a successful Gradle build alone is not a size benchmark.

## Release identity

The current workflow defaults are version name `2.1.0` and version code `3`, matching the existing frontend Android packaging workflow. Increase version code for each published release. Debug artifacts are for controlled device testing; production releases must be signed with the existing release keystore.
