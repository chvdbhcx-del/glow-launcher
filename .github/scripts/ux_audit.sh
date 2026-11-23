#!/usr/bin/env bash
# Installs Glow, runs the tap-everything crawler, then a short Russian-locale pass.
set -x
PKG=app.glowlauncher
adb install -r glow.apk
adb shell cmd package set-home-activity "$PKG/app.lawnchair.LawnchairLauncher"
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell input keyevent KEYCODE_HOME
sleep 15
mkdir -p audit
adb exec-out screencap -p > audit/00-first-start.png
python3 .github/scripts/ux_crawl.py || echo "crawler failed: $?"
# Russian pass: what a Russian user actually sees
adb shell cmd locale set-app-locales $PKG --user 0 --locales ru-RU || true
adb shell am force-stop $PKG; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 8
SIZE=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${SIZE%x*}; H=${SIZE#*x}
adb exec-out screencap -p > audit/ru-01-home.png
adb shell input swipe $((W / 2)) $((H * 30 / 100)) $((W / 2)) $((H * 30 / 100)) 1500; sleep 3
adb exec-out screencap -p > audit/ru-02-edit-mode.png
adb shell uiautomator dump /sdcard/ui.xml; adb exec-out cat /sdcard/ui.xml > audit/ru-02-edit-mode.xml
adb shell input keyevent KEYCODE_HOME; sleep 2
for i in 1 2 3 4; do adb shell input swipe $((W * 88 / 100)) $((H / 2)) $((W * 8 / 100)) $((H / 2)) 220; sleep 2; done
adb exec-out screencap -p > audit/ru-03-library.png
adb shell uiautomator dump /sdcard/ui.xml; adb exec-out cat /sdcard/ui.xml > audit/ru-03-library.xml
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell am start -n "$PKG/app.lawnchair.ui.preferences.PreferenceActivity"; sleep 5
adb exec-out screencap -p > audit/ru-04-settings.png
adb shell uiautomator dump /sdcard/ui.xml; adb exec-out cat /sdcard/ui.xml > audit/ru-04-settings.xml
adb shell input swipe $((W / 2)) $((H * 80 / 100)) $((W / 2)) $((H * 20 / 100)) 300; sleep 2
adb exec-out screencap -p > audit/ru-05-settings-scrolled.png
adb shell uiautomator dump /sdcard/ui.xml; adb exec-out cat /sdcard/ui.xml > audit/ru-05-settings-scrolled.xml
adb logcat -d > audit/logcat.txt
adb logcat -d -b crash > audit/crash.txt
adb shell dumpsys gfxinfo $PKG > audit/gfxinfo.txt
exit 0
