#!/bin/bash
set -e
cd "$(dirname "$0")"
SDK=~/workspace/android-sdk
BT=$SDK/build-tools/34.0.0
PL=$SDK/platforms/android-34
KEYSTORE=~/workspace/tvlock2/tvlock2.keystore
ALIAS=tvlock
PASS=tvlock2026

rm -rf gen classes dex
mkdir -p gen classes dex

# R.java
$BT/aapt2 compile --dir res -o res.zip
$BT/aapt2 link -o app-unsigned.apk -I $PL/android.jar \
  --manifest AndroidManifest.xml --java gen \
  --min-sdk-version 21 --target-sdk-version 29 \
  res.zip

# Java
find gen src -name "*.java" > sources.txt
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $PL/android.jar \
  -d classes @sources.txt

# DEX
$BT/d8 --output dex/ --lib $PL/android.jar $(find classes -name "*.class")

# APK: use aapt to add dex (preserves arsc alignment)
cp app-unsigned.apk app-final.apk
$BT/aapt add -k app-final.apk dex/classes.dex 2>&1 | head -2

# Sign with jarsigner (more compatible)
export PATH=~/workspace/android-sdk/jdk-17.0.20.1+1/bin:$PATH
jarsigner -keystore $KEYSTORE -storepass $PASS -keypass $PASS \
  -signedjar tvlock-signed.apk app-final.apk $ALIAS 2>&1 | head -3

$BT/aapt dump badging tvlock-signed.apk | grep -E "package|launchable|uses-permission" | head -12
ls -lh tvlock-signed.apk
echo BUILD_OK
