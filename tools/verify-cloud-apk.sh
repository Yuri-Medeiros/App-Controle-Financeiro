#!/usr/bin/env bash
# Infrastructure probe only: deliberately not the financial application.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/cloud-env.sh"
probe_dir="$(mktemp -d "$FINANCE_CLOUD_ROOT/apk-probe.XXXXXX")"
mkdir -p "$probe_dir/classes" "$probe_dir/dex"
cat > "$probe_dir/Probe.java" <<'JAVA'
package cloud.validation;
public final class Probe extends android.app.Activity {
  @Override public void onCreate(android.os.Bundle state) {
    super.onCreate(state);
  }
}
JAVA
cat > "$probe_dir/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
 package="cloud.validation" android:versionCode="1" android:versionName="1">
 <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>
 <application android:label="Cloud infrastructure probe" android:hasCode="true">
  <activity android:name="cloud.validation.Probe" android:exported="true">
   <intent-filter><action android:name="android.intent.action.MAIN"/>
    <category android:name="android.intent.category.LAUNCHER"/></intent-filter>
  </activity>
 </application>
</manifest>
XML
javac --release 8 -classpath "$ANDROID_HOME/platforms/android-36/android.jar" \
  -d "$probe_dir/classes" "$probe_dir/Probe.java"
d8 --lib "$ANDROID_HOME/platforms/android-36/android.jar" --min-api 26 \
  --output "$probe_dir/dex" "$probe_dir/classes/cloud/validation/Probe.class"
aapt2 link -I "$ANDROID_HOME/platforms/android-36/android.jar" \
  --manifest "$probe_dir/AndroidManifest.xml" -o "$probe_dir/unsigned.apk"
(cd "$probe_dir/dex" && zip -q "$probe_dir/unsigned.apk" classes.dex)
zipalign -p -f 4 "$probe_dir/unsigned.apk" "$probe_dir/aligned.apk"
# Disposable public test credentials; never use this key for the actual app.
keytool -genkeypair -keystore "$probe_dir/disposable.jks" -storepass android \
  -keypass android -alias probe -keyalg RSA -keysize 2048 -validity 2 \
  -dname 'CN=Disposable Cloud Infrastructure Probe' >/dev/null 2>&1
apksigner sign --ks "$probe_dir/disposable.jks" --ks-pass pass:android \
  --out "$probe_dir/cloud-probe.apk" "$probe_dir/aligned.apk"
apksigner verify --verbose "$probe_dir/cloud-probe.apk"
aapt2 dump badging "$probe_dir/cloud-probe.apk"
sha256sum "$probe_dir/cloud-probe.apk"
rm "$probe_dir/disposable.jks"
echo "Technical probe created: $probe_dir/cloud-probe.apk"

