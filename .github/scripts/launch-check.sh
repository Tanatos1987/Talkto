#!/usr/bin/env bash
# Opens ZnaiKo on the running emulator and fails if it crashes, so a build that does not start never reaches a phone.
# Usage: launch-check.sh <apk> <package> [<mapping.txt> <r8.jar>]
# With a mapping, a release crash is shown with the real class names (R8 renames them).
set -u
apk=$1 pkg=$2 mapping=${3:-} r8=${4:-}
echo "::group::$pkg"
adb install -r -g "$apk" || { echo "::error::$pkg: install failed"; exit 1; }
adb logcat -c
adb shell am start -W -n "$pkg/com.talkto.app.MainActivity"
# Long enough for the first screen and everything the app starts in the background.
sleep 30
crash=$(adb logcat -d -b crash)
pid=$(adb shell pidof "$pkg" | tr -d '\r')
# What the app itself reported (caught errors are logged with the ZnaiKo tag).
adb logcat -d -s ZnaiKo:W AndroidRuntime:E > app.log
retrace() {
  if [ -n "$mapping" ] && [ -f "$mapping" ] && [ -f "$r8" ]; then
    java -cp "$r8" com.android.tools.r8.retrace.Retrace "$mapping" "$1"
  else
    cat "$1"
  fi
}
retrace app.log | tail -n 150
echo "::endgroup::"
if [ -z "$crash" ] && [ -n "$pid" ]; then
  echo "OK: $pkg is still running after 30 s (pid $pid)"
  exit 0
fi
echo "::error::$pkg did not stay open"
printf '%s\n' "$crash" > crash.log
retrace crash.log
exit 1
