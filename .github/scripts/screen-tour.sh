#!/usr/bin/env bash
# Walks through ZnaiKo's screens on the running emulator (ScreenTour in app/src/androidTest) and collects its
# screenshots and screen recordings in screens/; each recording also becomes one picture of its frames (-frames.png).
# Usage: screen-tour.sh <debug apk> <androidTest apk>
set -u
app=$1 tour=$2 pkg=com.talkto.app.debug
mkdir -p screens
adb install -r -t -g "$app" || { echo "::error::$pkg: install failed"; exit 1; }
adb install -r -t -g "$tour" || { echo "::error::screen tour: install failed"; exit 1; }
# Like a phone that already has ZnaiKo: All files access given, so its banner does not cover every screen.
adb shell appops set "$pkg" MANAGE_EXTERNAL_STORAGE allow || true
runner=$(adb shell pm list instrumentation | tr -d '\r' | sed -n "s/^instrumentation:\([^ ]*\) (target=$pkg)$/\1/p" | head -n1)
[ -n "$runner" ] || { echo "::error::No instrumentation for $pkg"; exit 1; }
# -r prints each step and how it went. am instrument exits 0 even when a step fails, so the log is the record.
adb shell am instrument -w -r -e class com.talkto.app.screens.ScreenTour "$runner" | tr -d '\r' > instrument.log
grep -E '^INSTRUMENTATION_STATUS: (test|stack)=|^INSTRUMENTATION_STATUS_CODE: -2|^INSTRUMENTATION_(RESULT|CODE)|^Tests run|^OK \(' instrument.log
for f in $(adb shell run-as "$pkg" ls files/screens | tr -d '\r'); do
  adb exec-out run-as "$pkg" cat "files/screens/$f" > "screens/$f"
done
for f in $(adb shell ls /data/local/tmp | tr -d '\r' | grep '^znaiko-.*\.mp4$'); do
  adb pull "/data/local/tmp/$f" "screens/${f#znaiko-}"
done
command -v ffmpeg > /dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq ffmpeg > /dev/null; }
for v in screens/*.mp4; do
  [ -f "$v" ] || continue
  # 24 frames spread over the whole film, in a 6 x 4 grid.
  d=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$v")
  fps=$(awk -v d="$d" 'BEGIN { if (d > 0) printf "%.3f", 24 / d; else print 4 }')
  ffmpeg -loglevel error -y -i "$v" -vf "fps=$fps,scale=240:-1,tile=6x4" -frames:v 1 "${v%.mp4}-frames.png" || true
done
ls -l screens
