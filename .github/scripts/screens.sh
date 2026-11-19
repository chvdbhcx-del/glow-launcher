#!/usr/bin/env bash
# Walks through the main Glow screens on a running emulator and saves PNGs to screens/.
set -x
OUT=screens
mkdir -p "$OUT"
PKG=app.glowlauncher

shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png"; }

# Taps the first element whose text or description equals $1 (UI Automator dump).
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" <<'PY'
import re, sys
want = sys.argv[1]
xml = open("ui.xml", encoding="utf-8").read()
for node in re.findall(r"<node [^>]*>", xml):
    text = re.search(r' text="([^"]*)"', node)
    desc = re.search(r' content-desc="([^"]*)"', node)
    if want in ((text.group(1) if text else ""), (desc.group(1) if desc else "")):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -n "$xy" ]; then adb shell input tap $xy; return 0; fi
  echo "not found: $1"; return 1
}

adb install -r glow.apk
adb shell cmd package set-home-activity "$PKG/app.lawnchair.LawnchairLauncher"
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell input keyevent KEYCODE_HOME
sleep 12
shot 00-first-start 1
adb shell input keyevent KEYCODE_HOME
shot 01-home 4

SIZE=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}

# Folder preview opened
tap_text "Google" && shot 01b-folder 3
adb shell input keyevent KEYCODE_BACK
sleep 1

# App Library: swipe left past the last home page (one swipe per page)
has_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  grep -q "$1" ui.xml
}
for i in 1 2 3 4; do
  adb shell input swipe $((W * 88 / 100)) $((H / 2)) $((W * 8 / 100)) $((H / 2)) 220
  sleep 3
  if has_text "A–Z"; then break; fi
done
shot 02-app-library 2
tap_text "A–Z" && shot 03-a-z-list 3
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 3
# Swipe up must no longer open the library
adb shell input swipe $((W / 2)) $((H * 70 / 100)) $((W / 2)) $((H * 25 / 100)) 250
shot 04-after-swipe-up 3
adb shell input keyevent KEYCODE_HOME
sleep 2

# Edit mode: long press on empty home screen space, then the Edit menu
adb shell input swipe $((W / 2)) $((H * 30 / 100)) $((W / 2)) $((H * 30 / 100)) 1500
shot 05-long-press 3
# The Edit pill is drawn by the overlay; fall back to its position (top-left corner)
tap_text "Edit" || adb shell input tap $((W * 13 / 100)) $((H * 65 / 1000))
shot 06-edit-menu 2
tap_text "Add Widget" || tap_text "Widgets" && shot 07-widgets 4
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_HOME
sleep 2

adb shell am start -n "$PKG/app.lawnchair.ui.preferences.PreferenceActivity"
shot 08-settings 5
tap_text "General" && shot 09-settings-general 3
adb shell input keyevent KEYCODE_HOME
exit 0
