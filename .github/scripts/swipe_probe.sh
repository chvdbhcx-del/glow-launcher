#!/usr/bin/env bash
# Checks the "swipe right to left past the last home page opens the App Library" gesture
# on a running emulator. Saves screenshots and a filtered logcat to screens/.
set -x
OUT=screens
mkdir -p "$OUT"
PKG=app.glowlauncher
shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png"; }
has_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  grep -q "$1" ui.xml
}
adb install -r glow.apk
adb shell cmd package set-home-activity "$PKG/app.lawnchair.LawnchairLauncher"
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell input keyevent KEYCODE_HOME
sleep 14
shot 00-start 1
# Close the welcome sheet if it is up
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_HOME; sleep 3
adb logcat -c
SIZE=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
RESULT="$OUT/result.txt"
: > "$RESULT"
n=0
for dur in 120 250 500 900; do
  for i in 1 2 3 4; do
    adb shell input swipe $((W * 85 / 100)) $((H * 45 / 100)) $((W * 15 / 100)) $((H * 45 / 100)) $dur
    sleep 2
    if has_text "A–Z"; then
      echo "duration ${dur}ms: library OPEN after $i swipes" >> "$RESULT"
      n=$((n + 1)); shot "open-${dur}" 0
      break
    fi
    [ $i = 4 ] && { echo "duration ${dur}ms: library NOT open" >> "$RESULT"; shot "closed-${dur}" 0; }
  done
  adb shell input keyevent KEYCODE_HOME; sleep 3
done
adb shell dumpsys activity "$PKG" > /dev/null 2>&1
adb logcat -d > "$OUT/logcat-full.txt"
grep -E "AndroidRuntime|FATAL|GlowPull|StateManager" "$OUT/logcat-full.txt" | grep -v "^--" | tail -400 > "$OUT/logcat.txt"
cat "$RESULT"; grep -A12 GlowPull "$OUT/logcat-full.txt" | head -150
exit 0
