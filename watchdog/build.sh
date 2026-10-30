#!/bin/bash
set -e
cd "$(dirname "$0")"
SDK=~/workspace/android-sdk
BT=$SDK/build-tools/34.0.0
PL=$SDK/platforms/android-34
KEYSTORE=~/workspace/tvlock2/tvlock2.keystore
ALIAS=tvlock
PASS=tvlock2026
export PATH=$SDK/jdk-17.0.20.1+1/bin:$PATH

rm -rf gen classes dex
mkdir -p gen classes dex

$BT/aapt2 compile --dir res -o res.zip 2>/dev/null || mkdir -p res/values && echo '<?xml version="1.0" encoding="utf-8"?><resources></resources>' > res/values/empty.xml && $BT/aapt2 compile --dir res -o res.zip
# 打包 assets（内置主程序 APK）
if [ -d assets ]; then
  $BT/aapt2 compile --dir assets -o assets.zip 2>/dev/null || true
fi
$BT/aapt2 link -o app-unsigned.apk -I $PL/android.jar \
  --manifest AndroidManifest.xml --java gen \
  --min-sdk-version 21 --target-sdk-version 29 \
  -A assets \
  res.zip

find gen src -name "*.java" > sources.txt
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $PL/android.jar \
  -d classes @sources.txt

$BT/d8 --output dex/ --lib $PL/android.jar $(find classes -name "*.class")

cp app-unsigned.apk app-final.apk
$BT/aapt add -k app-final.apk dex/classes.dex 2>&1 | head -2

jarsigner -keystore $KEYSTORE -storepass $PASS -keypass $PASS \
  -signedjar tvlock-wd-signed.apk app-final.apk $ALIAS 2>&1 | head -3

ls -lh tvlock-wd-signed.apk
echo BUILD_OK
