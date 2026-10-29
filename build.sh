#!/bin/bash
# Manual APK build: aapt2 -> javac -> d8 -> zipalign -> apksigner (no Gradle)
set -e
cd "$(dirname "$0")"
SDK=/home/hatch/workspace/android-sdk
BT=$SDK/build-tools/34.0.0
AJAR=$SDK/platforms/android-34/android.jar
export JAVA_HOME=$SDK/jdk-17.0.20.1+1
export PATH=$JAVA_HOME/bin:$PATH
KSPASS=tvlock2026

rm -rf gen classes dex res.zip app-unsigned.apk tvlock.apk tvlock-signed.apk sources.txt
mkdir -p gen classes dex

echo "== aapt2 compile =="
$BT/aapt2 compile --dir res -o res.zip
echo "== aapt2 link =="
$BT/aapt2 link -o app-unsigned.apk -I "$AJAR" --manifest AndroidManifest.xml --java gen \
  --min-sdk-version 26 --target-sdk-version 28 res.zip

echo "== javac =="
find src gen -name "*.java" > sources.txt
javac -encoding UTF-8 -source 8 -target 8 -nowarn -cp "$AJAR" -d classes @sources.txt

echo "== d8 =="
$BT/d8 --lib "$AJAR" --min-api 26 --output dex $(find classes -name "*.class")
ls dex/

echo "== add classes.dex =="
python3 -c "
import zipfile
z = zipfile.ZipFile('app-unsigned.apk', 'a', zipfile.ZIP_DEFLATED)
z.write('dex/classes.dex', 'classes.dex')
z.close()
"

echo "== zipalign =="
$BT/zipalign -f 4 app-unsigned.apk tvlock.apk

if [ ! -f tvlock.keystore ]; then
  echo "== gen keystore =="
  keytool -genkeypair -keystore tvlock.keystore -alias tvlock -keyalg RSA -keysize 2048 \
    -validity 10950 -storepass $KSPASS -keypass $KSPASS \
    -dname "CN=TVLock, OU=Home, O=Home, L=Home, ST=Home, C=CN" 2>/dev/null
  chmod 600 tvlock.keystore
fi

echo "== apksigner =="
$BT/apksigner sign --ks tvlock.keystore --ks-pass pass:$KSPASS --out tvlock-signed.apk tvlock.apk
$BT/apksigner verify --print-certs tvlock-signed.apk | head -6

echo "== badging =="
$BT/aapt2 dump badging tvlock-signed.apk | head -8
ls -la tvlock-signed.apk
echo BUILD_OK
