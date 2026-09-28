#!/bin/bash
# ./dev.sh run — 빌드 → (무선) 설치 → 실행
#   build  디버그 APK 만 빌드
#   run    빌드 → 설치 → 실행 (기기: ANDROID_SERIAL 또는 FOLDMIC_PHONE, 없으면 adb 기본 기기)
#   log    앱 로그
set -e
# JDK 21 — 이미 JAVA_HOME 이 있으면 그대로, 없으면 Homebrew openjdk@21 을 찾아 본다
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21 ]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
export PATH=$HOME/Library/Android/sdk/platform-tools:$PATH
D=$(cd "$(dirname "$0")" && pwd)

resolve_device() {
  if [ -z "${ANDROID_SERIAL:-}" ] && [ -n "${FOLDMIC_PHONE:-}" ]; then
    adb connect "$FOLDMIC_PHONE" >/dev/null 2>&1 || true
    export ANDROID_SERIAL=$FOLDMIC_PHONE
  fi
  adb get-state >/dev/null 2>&1 || { echo "✗ adb 기기가 없습니다 — ANDROID_SERIAL 또는 FOLDMIC_PHONE 을 지정하세요"; exit 1; }
}

case "${1:-run}" in
  build) "$D/gradlew" -p "$D" -q assembleDebug ;;
  run)
    "$D/gradlew" -p "$D" -q assembleDebug
    resolve_device
    adb install -r "$D/app/build/outputs/apk/debug/app-debug.apk"
    adb shell am start -n kr.joonlab.foldmic/.MainActivity ;;
  log) resolve_device; adb logcat --pid="$(adb shell pidof kr.joonlab.foldmic)" ;;
esac
