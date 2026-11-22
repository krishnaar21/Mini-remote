#!/usr/bin/env bash
# Screenshots of the debug build on an emulator: each screen is opened with `-e shot <name>`
# on pretend TVs (see ui/Shot.kt). Same screens as the iOS run, plus the Turn off sheet.
set -u
PKG=com.krishna.miniremote
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put global adb_enabled 1
# A clean status bar: 9:41, full battery and signal, no notifications.
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e level 4 -e datatype none >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
mkdir -p shots
i=1
for s in home depth flat welcome tour page2 page3 more moreapps depthmore sleep keys apps devices scan settings settingsheet settingsheet2 power trackpad live light navy; do
  adb shell am force-stop $PKG
  adb shell pm clear $PKG >/dev/null
  adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
  adb shell am start -W -n $PKG/.MainActivity -e shot "$s" >/dev/null
  sleep 7
  adb exec-out screencap -p > "shots/$(printf %02d $i)-$s.png"
  i=$((i+1))
done
ls -la shots
