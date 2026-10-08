#!/usr/bin/env bash
# Builds Zip2Jar.apk without the Android SDK. Needs: JDK 17+, python3, internet (Maven Central + PyPI).
# Usage: tools/build_apk.sh [workdir]   -> workdir/Zip2Jar.apk
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
W="${1:-$ROOT/build-apk}"; L="$W/lib"; mkdir -p "$L" "$W/classes" "$W/apk"
M=https://repo.maven.apache.org/maven2
fetch() { [ -s "$L/$(basename "$1")" ] || curl -fsSL -o "$L/$(basename "$1")" "$M/$1"; }
fetch org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar
fetch com/jakewharton/android/repackaged/dalvik-dx/9.0.0_r3/dalvik-dx-9.0.0_r3.jar
fetch com/android/tools/build/apksig/2.3.0/apksig-2.3.0.jar
[ -x "$W/venv/bin/python" ] || { python3 -m venv "$W/venv"; "$W/venv/bin/pip" install -q pyaxml; }

# 1. Compile Java against the Android framework (Java 8 bytecode so dx can read it)
rm -rf "$W/classes"/*; rm -rf "$W/apk"/*
javac --release 8 -nowarn -cp "$L/android-all-14-robolectric-10818077.jar" -d "$W/classes" "$ROOT"/android/app/src/main/java/com/zip2jar/*.java 2>&1 | grep -v '^Note\|warning' || true
# 2. Dex
java -cp "$L/dalvik-dx-9.0.0_r3.jar" com.android.dx.command.Main --dex --min-sdk-version=29 --output="$W/apk/classes.dex" "$W/classes"
# 3. Binary manifest
"$W/venv/bin/python" - "$W/apk/AndroidManifest.xml" <<'PY'
import sys, pyaxml
xml = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.zip2jar" android:versionCode="1" android:versionName="1.0">
  <uses-sdk android:minSdkVersion="29" android:targetSdkVersion="34"/>
  <application android:label="Zip to Jar" android:icon="@android:drawable/ic_menu_save" android:theme="@android:style/Theme.DeviceDefault.NoActionBar">
    <activity android:name="com.zip2jar.MainActivity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>
    </activity>
  </application>
</manifest>'''
a = pyaxml.AXML(); a.from_xml(xml)
open(sys.argv[1], 'wb').write(a.pack())
PY
# 4. Package (assets = the web app) and sign
mkdir -p "$W/apk/assets"; cp "$ROOT"/zip2jar/{index.html,jszip.min.js} "$W/apk/assets/"
(cd "$W/apk" && rm -f ../unsigned.apk && zip -qr -X ../unsigned.apk AndroidManifest.xml classes.dex assets)
[ -f "$W/debug.p12" ] || keytool -genkeypair -keystore "$W/debug.p12" -storetype PKCS12 -storepass android -alias key -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Zip2Jar" 2>/dev/null
javac -cp "$L/apksig-2.3.0.jar" -d "$W" "$ROOT/tools/Sign.java"
java --add-exports java.base/sun.security.x509=ALL-UNNAMED -cp "$L/apksig-2.3.0.jar:$W" Sign "$W/debug.p12" "$W/unsigned.apk" "$W/Zip2Jar.apk"
echo "Built $W/Zip2Jar.apk"
