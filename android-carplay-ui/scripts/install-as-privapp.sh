#!/bin/sh
# Silent installs, persistent=true and overlay grants all need a priv-app install. Run from a PC with adb root.
set -e
APK=${1:?usage: $0 app-release.apk}
adb root; adb remount
adb shell mkdir -p /system/priv-app/RidgelineDock
adb push "$APK" /system/priv-app/RidgelineDock/RidgelineDock.apk
adb shell chmod 644 /system/priv-app/RidgelineDock/RidgelineDock.apk
adb shell appops set com.ridgeline.dock SYSTEM_ALERT_WINDOW allow
adb shell appops set com.ridgeline.dock MANAGE_EXTERNAL_STORAGE allow
adb reboot
