#!/usr/bin/env bash
# Full Android emulator smoke test. The emulator-runner action invokes each
# non-empty line in its "script" input separately, so this file is launched
# through one command and owns its complete Bash process.
set -Eeuo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5554}"
PACKAGE="com.revelacode.app"
APK_PATH="android/app/build/outputs/apk/debug/app-debug.apk"
LOG_PATH="android-launch-logcat.txt"

capture_logcat() {
  adb -s "$SERIAL" logcat -d -v threadtime > "$LOG_PATH" 2>/dev/null || true
}
trap capture_logcat EXIT

echo "Checking the generated APK..."
if [[ ! -s "$APK_PATH" ]]; then
  echo "Debug APK is missing or empty: $APK_PATH"
  exit 1
fi

echo "Waiting for emulator ADB transport..."
adb start-server >/dev/null 2>&1 || true
adb devices -l

device_state=""
for attempt in $(seq 1 120); do
  device_state="$(adb devices | awk -v serial="$SERIAL" '$1 == serial { print $2 }')"
  [[ "$device_state" == "device" ]] && break
  if [[ "$device_state" == "offline" ]]; then
    adb reconnect offline >/dev/null 2>&1 || true
  fi
  sleep 5
done

if [[ "$device_state" != "device" ]]; then
  echo "Emulator ADB transport did not become online (state: ${device_state:-missing})."
  adb devices -l || true
  exit 1
fi

echo "Waiting for Android framework boot completion..."
boot_complete=""
for attempt in $(seq 1 120); do
  boot_complete="$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [[ "$boot_complete" == "1" ]] && break
  sleep 5
done

if [[ "$boot_complete" != "1" ]]; then
  echo "Android framework did not report sys.boot_completed=1."
  exit 1
fi

echo "Installing RevelaCode APK..."
adb -s "$SERIAL" install --no-streaming -r "$APK_PATH"
adb -s "$SERIAL" shell pm path "$PACKAGE"
adb -s "$SERIAL" logcat -c

echo "Launching RevelaCode..."
adb -s "$SERIAL" shell monkey -p "$PACKAGE" 1
sleep 20

app_pid="$(adb -s "$SERIAL" shell pidof "$PACKAGE" | tr -d '\r' || true)"
if [[ -z "$app_pid" ]]; then
  echo "RevelaCode did not retain a running process after launch."
  capture_logcat
  grep -E -i 'FATAL EXCEPTION|AndroidRuntime|Fatal signal|Process: com\.revelacode\.app' "$LOG_PATH" | tail -n 100 || true
  exit 1
fi

echo "RevelaCode launched successfully. PID: $app_pid"

echo "Delivering an Android Google OAuth callback deep-link smoke test..."
adb -s "$SERIAL" shell am start -W \
  -n "$PACKAGE/.MainActivity" \
  -a android.intent.action.VIEW \
  -d "com.revelacode.app://oauth/callback?oauth=error&reason=android_deeplink_smoke"
sleep 5

app_pid_after_deeplink="$(adb -s "$SERIAL" shell pidof "$PACKAGE" | tr -d '\r' || true)"
if [[ -z "$app_pid_after_deeplink" ]]; then
  echo "RevelaCode crashed after receiving its OAuth callback deep link."
  capture_logcat
  grep -E -i 'FATAL EXCEPTION|AndroidRuntime|Fatal signal|Process: com\.revelacode\.app' "$LOG_PATH" | tail -n 100 || true
  exit 1
fi

echo "Android OAuth callback deep link was delivered; app remains alive (PID: $app_pid_after_deeplink)."

echo "Delivering the verified HTTPS Android App Link callback..."
adb -s "$SERIAL" shell am start -W \
  -n "$PACKAGE/.MainActivity" \
  -a android.intent.action.VIEW \
  -d "https://revelacode.co.ke/oauth/android-callback?oauth=error&reason=android_app_link_smoke"
sleep 5

app_pid_after_app_link="$(adb -s "$SERIAL" shell pidof "$PACKAGE" | tr -d '\r' || true)"
if [[ -z "$app_pid_after_app_link" ]]; then
  echo "RevelaCode crashed after receiving its HTTPS OAuth App Link."
  capture_logcat
  grep -E -i 'FATAL EXCEPTION|AndroidRuntime|Fatal signal|Process: com\.revelacode\.app' "$LOG_PATH" | tail -n 100 || true
  exit 1
fi

echo "HTTPS Android App Link was delivered; app remains alive (PID: $app_pid_after_app_link)."
