#!/usr/bin/env bash
# Screenshots of the debug build on an emulator: each screen is opened with `-e shot <name>`
# on pretend TVs (see ui/Shot.kt). Same screens as the iOS run, plus the Turn off sheet.
set -u -x
PKG=com.krishna.miniremote
# Every adb call gets a time limit, so one stuck command can't hang the whole run.
a() { timeout 40 adb "$@"; }
timeout 120 adb install -r app/build/outputs/apk/debug/app-debug.apk
a shell settings put global adb_enabled 1
# A clean status bar: 9:41, full battery and signal, no notifications.
a shell settings put global sysui_demo_allowed 1
a shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
a shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 >/dev/null
a shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
a shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null
a shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e level 4 -e datatype none >/dev/null
a shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
a shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
mkdir -p shots
i=1
for s in home depth flat welcome tour page2 page3 more moreapps depthmore sleep keys apps devices scan settings settingsheet settingsheet2 power trackpad live light navy; do
  a shell am force-stop $PKG
  a shell pm clear $PKG >/dev/null
  a shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
  a shell am start -n $PKG/.MainActivity -e shot "$s" >/dev/null
  sleep 7
  echo "shot $i $s"
  timeout 40 adb exec-out screencap -p > "shots/$(printf %02d $i)-$s.png"
  i=$((i+1))
done
ls -la shots
